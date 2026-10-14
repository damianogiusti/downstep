#include "TimeStretcher.h"

#include <bungee/Bungee.h>

#include <algorithm>
#include <cmath>

struct TimeStretcher::RequestState {
    Bungee::Request request{};
};

TimeStretcher::TimeStretcher() : request_(std::make_unique<RequestState>()) {}

TimeStretcher::~TimeStretcher() = default;

void TimeStretcher::prepare(const PcmBuffer &source, int outputRate, int maxBlockFrames) {
    source_ = &source;
    // Hop adjust 0: Bungee warns that other values degrade the sound, so there are no quality levels.
    stretcher_ = std::make_unique<Bungee::Stretcher<Bungee::Basic>>(
        Bungee::SampleRates{source.sampleRate, outputRate}, 2);
    grainInput_.assign(2 * size_t(stretcher_->maxInputFrameCount()), 0.f);
    // A grain is ~10 ms of output; at the lowest speed and an upsampling device it can still be
    // several thousand frames, so leave room for a block plus a full second.
    fifoCapacity_ = maxBlockFrames * 2 + outputRate;
    for (auto &channel : fifo_) channel.assign(size_t(fifoCapacity_), 0.f);
    auto &r = request_->request;
    r = Bungee::Request{};
    r.speed = 1.0;
    r.pitch = 1.0;
    r.resampleMode = resampleMode_autoOut;
    seek(0);
}

void TimeStretcher::seek(double frame) {
    auto &r = request_->request;
    r.position = frame;
    r.reset = true;
    stretcher_->preroll(r);
    fifoRead_ = fifoWrite_ = 0;
    readCount_ = writeCount_ = 0;
    chunkHead_ = chunkCount_ = 0;
    finished_ = false;
    position_ = frame;
    seekTarget_ = frame;
}

void TimeStretcher::setSpeed(double ratio) { request_->request.speed = std::clamp(ratio, 0.05, 4.0); }

void TimeStretcher::setPitch(double semitones) { request_->request.pitch = std::pow(2.0, semitones / 12.0); }

void TimeStretcher::setLoop(int64_t start, int64_t end, bool enabled) {
    loopStart_ = start;
    loopEnd_ = end;
    loopOn_ = enabled && end > start;
}

void TimeStretcher::setMix(double mixRight) {
    // The far side fades into the near one, which stays; m < 0.5 mirrors m >= 0.5.
    const double m = std::clamp(mixRight, 0.0, 1.0);
    if (m >= 0.5) {
        keepLeft_ = float(2 * (1 - m));
        mixRightToLeft_ = float(2 * (m - 0.5));
        keepRight_ = 1.f;
        mixLeftToRight_ = 0.f;
    } else {
        keepRight_ = float(2 * m);
        mixLeftToRight_ = float(2 * (0.5 - m));
        keepLeft_ = 1.f;
        mixRightToLeft_ = 0.f;
    }
}

int TimeStretcher::render(float *const *out, int frames) {
    while (fifoWrite_ - fifoRead_ < frames && !finished_) produceGrain();
    const int available = std::min(frames, fifoWrite_ - fifoRead_);
    for (int c = 0; c < 2; ++c) {
        std::copy_n(fifo_[size_t(c)].begin() + fifoRead_, available, out[c]);
        std::fill(out[c] + available, out[c] + frames, 0.f);
    }
    fifoRead_ += available;
    readCount_ += available;
    updatePosition();
    return available;
}

void TimeStretcher::produceGrain() {
    auto &r = request_->request;
    if (loopOn_ && r.position >= double(loopEnd_)) {
        // Jump without reset: Bungee overlaps the grains on both sides, so the wrap stays smooth.
        r.position = double(loopStart_) + std::fmod(r.position - double(loopEnd_), double(loopEnd_ - loopStart_));
    }
    if (!loopOn_ && r.position >= double(source_->frames)) {
        finished_ = true;
        return;
    }

    const Bungee::InputChunk chunk = stretcher_->specifyGrain(r);
    const int length = chunk.end - chunk.begin;
    float *left = grainInput_.data();
    float *right = grainInput_.data() + length;
    for (int i = 0; i < length; ++i) {
        const int64_t frame = int64_t(chunk.begin) + i;
        const float l = source_->at(0, frame), rr = source_->at(1, frame);
        left[i] = keepLeft_ * l + mixRightToLeft_ * rr;
        right[i] = keepRight_ * rr + mixLeftToRight_ * l;
    }
    const int muteHead = std::clamp(-chunk.begin, 0, length);
    const int muteTail = std::clamp(int(int64_t(chunk.end) - source_->frames), 0, length);
    stretcher_->analyseGrain(grainInput_.data(), length, muteHead, muteTail);

    Bungee::OutputChunk output{};
    stretcher_->synthesiseGrain(output);
    appendToFifo(output);

    stretcher_->next(r);
    r.reset = false;
}

void TimeStretcher::appendToFifo(const Bungee::OutputChunk &output) {
    if (fifoWrite_ + output.frameCount > fifoCapacity_) {
        for (auto &channel : fifo_) std::copy(channel.begin() + fifoRead_, channel.begin() + fifoWrite_, channel.begin());
        fifoWrite_ -= fifoRead_;
        fifoRead_ = 0;
    }
    const int frames = std::min(output.frameCount, fifoCapacity_ - fifoWrite_);
    if (frames <= 0) return;
    for (int c = 0; c < 2; ++c) {
        const float *src = output.data + size_t(c) * size_t(output.channelStride);
        std::copy_n(src, frames, fifo_[size_t(c)].begin() + fifoWrite_);
    }
    fifoWrite_ += frames;

    const double begin = output.request[Bungee::OutputChunk::begin]->position;
    const double end = output.request[Bungee::OutputChunk::end]->position;
    if (!std::isnan(begin) && !std::isnan(end)) {
        if (chunkCount_ == kMaxChunks) { // drop the oldest; positions are for display only
            chunkHead_ = (chunkHead_ + 1) % kMaxChunks;
            --chunkCount_;
        }
        chunks_[size_t((chunkHead_ + chunkCount_) % kMaxChunks)] = {writeCount_, frames, begin, end};
        ++chunkCount_;
    }
    writeCount_ += frames;
}

void TimeStretcher::updatePosition() {
    updateChunkPosition();
    if (seekTarget_ >= 0) {
        if (position_ < seekTarget_) position_ = seekTarget_;
        else seekTarget_ = -1;
    }
}

void TimeStretcher::updateChunkPosition() {
    // Drop chunks fully played, then interpolate inside the one at the read head.
    while (chunkCount_ > 0) {
        const ChunkInfo &head = chunks_[size_t(chunkHead_)];
        if (head.begin + head.frames > readCount_) break;
        position_ = head.positionEnd;
        chunkHead_ = (chunkHead_ + 1) % kMaxChunks;
        --chunkCount_;
    }
    if (chunkCount_ == 0) return;
    const ChunkInfo &head = chunks_[size_t(chunkHead_)];
    if (readCount_ < head.begin) return;
    // Across a loop wrap the end is before the begin: hold the begin for those few milliseconds.
    if (head.positionEnd < head.positionBegin) {
        position_ = head.positionBegin;
        return;
    }
    const double t = double(readCount_ - head.begin) / head.frames;
    position_ = head.positionBegin + (head.positionEnd - head.positionBegin) * t;
}
