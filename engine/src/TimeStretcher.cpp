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
    reachedLoopEnd_ = false;
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

int TimeStretcher::render(float *const *out, int frames, double *sourceFrames) {
    // With a stop at the loop end, a fade's worth beyond the block: the stop is found while the frames
    // before it are still in the FIFO, to be faded whatever the block boundaries (see produceGrain()).
    const int wanted = frames + (stopAtLoopEnd_ ? stopFadeFrames_ : 0);
    while (fifoWrite_ - fifoRead_ < wanted && !finished_) produceGrain();
    const int available = std::min(frames, fifoWrite_ - fifoRead_);
    for (int c = 0; c < 2; ++c) {
        std::copy_n(fifo_[size_t(c)].begin() + fifoRead_, available, out[c]);
        std::fill(out[c] + available, out[c] + frames, 0.f);
    }
    if (sourceFrames) fillSourceFrames(sourceFrames, available);
    fifoRead_ += available;
    readCount_ += available;
    updatePosition();
    return available;
}

void TimeStretcher::produceGrain() {
    auto &r = request_->request;
    if (loopOn_ && r.position >= double(loopEnd_)) {
        if (stopAtLoopEnd_) {
            finished_ = true;
            reachedLoopEnd_ = true;
            fadeOutFifoTail();
            return;
        }
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
    if (chunk.begin >= 0 && int64_t(chunk.end) <= source_->frames) {
        // Almost every grain lies inside the file: read the channels directly (a mono file's
        // channel 0 serves both sides).
        const float *sourceLeft = source_->channelData(0) + chunk.begin;
        const float *sourceRight = source_->channelData(1) + chunk.begin;
        for (int i = 0; i < length; ++i) {
            const float l = sourceLeft[i], rr = sourceRight[i];
            left[i] = keepLeft_ * l + mixRightToLeft_ * rr;
            right[i] = keepRight_ * rr + mixLeftToRight_ * l;
        }
    } else {
        for (int i = 0; i < length; ++i) {
            const int64_t frame = int64_t(chunk.begin) + i;
            const float l = source_->at(0, frame), rr = source_->at(1, frame);
            left[i] = keepLeft_ * l + mixRightToLeft_ * rr;
            right[i] = keepRight_ * rr + mixLeftToRight_ * l;
        }
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

void TimeStretcher::fadeOutFifoTail() {
    // Shorter only when the stop comes within a fade of a seek: the FIFO then holds less.
    const int length = std::min(stopFadeFrames_, fifoWrite_ - fifoRead_);
    const int start = fifoWrite_ - length;
    for (auto &channel : fifo_)
        for (int i = 0; i < length; ++i) channel[size_t(start + i)] *= float(length - 1 - i) / float(length);
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

    const double end = output.request[Bungee::OutputChunk::end]->position;
    // The first chunk after a reset (a seek) starts from the grain before it, somewhere else in the
    // file: it would read as a jump back, or a wrap. It is the run-in's start: hold its end.
    const double begin =
        output.request[Bungee::OutputChunk::end]->reset ? end : output.request[Bungee::OutputChunk::begin]->position;
    if (!std::isnan(begin) && !std::isnan(end)) {
        if (chunkCount_ == kMaxChunks) { // drop the oldest (never seen: see kMaxChunks)
            chunkHead_ = (chunkHead_ + 1) % kMaxChunks;
            --chunkCount_;
        }
        const double loopLength = loopOn_ ? double(loopEnd_ - loopStart_) : 0.0;
        chunks_[size_t((chunkHead_ + chunkCount_) % kMaxChunks)] = {writeCount_, frames, begin, end, double(loopStart_),
                                                                    loopLength};
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

void TimeStretcher::skipPreroll() {
    if (seekTarget_ < 0) return;
    // A few grains at most: Bungee's run-in is a handful of grains long.
    for (int grains = 0; grains < kMaxChunks / 2; ++grains) {
        for (int k = 0; k < chunkCount_; ++k) {
            const ChunkInfo &chunk = chunks_[size_t((chunkHead_ + k) % kMaxChunks)];
            if (chunk.positionEnd < seekTarget_ || chunk.positionEnd < chunk.positionBegin) continue;
            // The first frame of this chunk at or past the target.
            const double span = chunk.positionEnd - chunk.positionBegin;
            const double t = span > 0 ? std::clamp((seekTarget_ - chunk.positionBegin) / span, 0.0, 1.0) : 0.0;
            const int64_t frame = std::max(readCount_, chunk.begin + int64_t(std::ceil(t * chunk.frames)));
            const int skip = int(std::min<int64_t>(frame - readCount_, fifoWrite_ - fifoRead_));
            const double target = seekTarget_;
            fifoRead_ += skip;
            readCount_ += skip;
            updatePosition();
            position_ = target;
            seekTarget_ = -1;
            return;
        }
        if (finished_) return;
        produceGrain();
    }
}

void TimeStretcher::fillSourceFrames(double *sourceFrames, int frames) const {
    // The same mapping as updateChunkPosition(), for every output frame of the block about to be read,
    // but without holding a seek target: the run-in before it is heard, so its beats click too.
    // Before the first chunk of a seek nothing is known (NaN): position_ holds the target, not yet heard.
    double last = seekTarget_ >= 0 ? std::nan("") : position_;
    int k = 0; // chunks after the head
    for (int i = 0; i < frames; ++i) {
        const int64_t frame = readCount_ + i;
        while (k < chunkCount_) {
            const ChunkInfo &chunk = chunks_[size_t((chunkHead_ + k) % kMaxChunks)];
            if (chunk.begin + chunk.frames > frame) break;
            ++k;
        }
        double p = last; // between chunks (a NaN request was skipped): hold
        if (k < chunkCount_) {
            const ChunkInfo &chunk = chunks_[size_t((chunkHead_ + k) % kMaxChunks)];
            if (chunk.begin <= frame) {
                const double t = double(frame - chunk.begin) / chunk.frames;
                double end = chunk.positionEnd;
                // Across a wrap: interpolate as if the loop went on, then fold back to its start.
                const bool wraps = end < chunk.positionBegin && chunk.loopLength > 0;
                if (wraps) end += chunk.loopLength;
                p = chunk.positionBegin + (end - chunk.positionBegin) * t;
                if (wraps && p >= chunk.loopStart + chunk.loopLength) p -= chunk.loopLength;
                else if (!wraps && end < chunk.positionBegin) p = chunk.positionBegin;
            }
        }
        sourceFrames[i] = p;
        last = p;
    }
}
