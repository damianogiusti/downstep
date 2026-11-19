#include "TimeStretcher.h"

#include <bungee/Bungee.h>

#include <algorithm>
#include <cmath>

struct TimeStretcher::RequestState {
    Bungee::Request request{};
};

TimeStretcher::TimeStretcher() : request_(std::make_unique<RequestState>()) {}

TimeStretcher::~TimeStretcher() = default;

void TimeStretcher::prepare(const PcmSource &source, int outputRate, int maxBlockFrames) {
    source_ = &source;
    sourceFrames_ = source.frames();
    targetGain_.fill(1.f);
    appliedGain_.fill(1.f);
    // Hop adjust 0: Bungee warns that other values degrade the sound, so there are no quality levels.
    stretcher_ = std::make_unique<Bungee::Stretcher<Bungee::Basic>>(
        Bungee::SampleRates{source.sampleRate(), outputRate}, 2);
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
    snapsGains_ = true; // the output starts under a fade-in: no audible ramp from stale gains
}

void TimeStretcher::setStreamGains(const float *gains, int count) {
    for (int s = 0; s < std::min(count, kMaxStreams); ++s) targetGain_[size_t(s)] = gains[s];
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
    if (!loopOn_ && r.position >= double(sourceFrames_)) {
        finished_ = true;
        return;
    }

    const Bungee::InputChunk chunk = stretcher_->specifyGrain(r);
    const int length = chunk.end - chunk.begin;
    float *left = grainInput_.data();
    float *right = grainInput_.data() + length;
    if (source_->isStemSet) {
        sumStreams(chunk.begin, length, left, right);
    } else {
        const PcmBuffer &pcm = *source_->streams.front();
        if (chunk.begin >= 0 && int64_t(chunk.end) <= sourceFrames_) {
            // Almost every grain lies inside the file: read the channels directly (a mono file's
            // channel 0 serves both sides).
            const float *sourceLeft = pcm.channelData(0) + chunk.begin;
            const float *sourceRight = pcm.channelData(1) + chunk.begin;
            for (int i = 0; i < length; ++i) {
                const float l = sourceLeft[i], rr = sourceRight[i];
                left[i] = keepLeft_ * l + mixRightToLeft_ * rr;
                right[i] = keepRight_ * rr + mixLeftToRight_ * l;
            }
        } else {
            for (int i = 0; i < length; ++i) {
                const int64_t frame = int64_t(chunk.begin) + i;
                const float l = pcm.at(0, frame), rr = pcm.at(1, frame);
                left[i] = keepLeft_ * l + mixRightToLeft_ * rr;
                right[i] = keepRight_ * rr + mixLeftToRight_ * l;
            }
        }
    }
    const int muteHead = std::clamp(-chunk.begin, 0, length);
    const int muteTail = std::clamp(int(int64_t(chunk.end) - sourceFrames_), 0, length);
    stretcher_->analyseGrain(grainInput_.data(), length, muteHead, muteTail);

    Bungee::OutputChunk output{};
    stretcher_->synthesiseGrain(output);
    appendToFifo(output);

    stretcher_->next(r);
    r.reset = false;
}

void TimeStretcher::sumStreams(int begin, int length, float *left, float *right) {
    std::fill_n(left, length, 0.f);
    std::fill_n(right, length, 0.f);
    // Frames outside the file are silence: only the part of the grain that lies inside is read.
    const int first = int(std::clamp<int64_t>(-int64_t(begin), 0, length));
    const int last = int(std::clamp<int64_t>(sourceFrames_ - begin, first, length));
    const int streamCount = int(source_->streams.size());
    for (int s = 0; s < streamCount; ++s) {
        const float to = targetGain_[size_t(s)];
        const float from = snapsGains_ ? to : appliedGain_[size_t(s)];
        appliedGain_[size_t(s)] = to;
        if (from == 0.f && to == 0.f) continue; // a muted stem costs nothing
        const PcmBuffer &pcm = *source_->streams[size_t(s)];
        // Offset by `begin` only where it is inside the file: before the start it would point outside.
        const float *sourceLeft = pcm.channelData(0) + (int64_t(begin) + first);
        const float *sourceRight = pcm.channelData(1) + (int64_t(begin) + first);
        // The gain moves along the whole grain: its window is weighted to the middle, and the next
        // grain, which overlaps this one, starts from where this one ended.
        const float step = (to - from) / float(length);
        if (step == 0.f) {
            for (int i = first; i < last; ++i) {
                left[i] += to * sourceLeft[i - first];
                right[i] += to * sourceRight[i - first];
            }
        } else {
            for (int i = first; i < last; ++i) {
                const float g = from + step * float(i);
                left[i] += g * sourceLeft[i - first];
                right[i] += g * sourceRight[i - first];
            }
        }
    }
    snapsGains_ = false;
    for (int i = 0; i < length; ++i) {
        const float l = left[i], rr = right[i];
        left[i] = keepLeft_ * l + mixRightToLeft_ * rr;
        right[i] = keepRight_ * rr + mixLeftToRight_ * l;
    }
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
