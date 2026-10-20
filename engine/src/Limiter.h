#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

/**
 * Stereo-linked look-ahead peak limiter: no sample leaves above the ceiling.
 *
 * Each input sample asks for the gain that keeps it under the ceiling. A running minimum over the
 * look-ahead window, an exponential release, then a moving average over the same window: the gain
 * glides down before a peak reaches the output, as the audio is delayed by the window. Every value
 * the average reads covers the delayed sample, so the average never asks for more than that sample
 * allows. A gentler curve than a limiter without look-ahead, at the cost of 5 ms of latency.
 *
 * prepare() allocates. process() and reset() never allocate, lock or block: safe on the audio thread.
 */
class Limiter {
public:
    /** -1 dBFS: some margin for the peaks the converter rebuilds between samples. */
    static constexpr float kCeiling = 0.8912509f;
    static constexpr double kLookAheadSeconds = 0.005;
    static constexpr double kReleaseSeconds = 0.1;

    void prepare(int sampleRate) {
        window_ = std::max(1, int(std::lround(sampleRate * kLookAheadSeconds)));
        releaseCoefficient_ = 1.0 - std::exp(-1.0 / (sampleRate * kReleaseSeconds));
        delayed_[0].assign(size_t(window_), 0.f);
        delayed_[1].assign(size_t(window_), 0.f);
        wanted_.assign(size_t(window_), 1.f);
        released_.assign(size_t(window_), 1.f);
        minimumIndex_.assign(size_t(window_), 0);
        minimumGain_.assign(size_t(window_), 1.f);
        reset();
    }

    /** Clears the delay line (silence) and the gain history: for a seek or a fresh start. */
    void reset() {
        std::fill(delayed_[0].begin(), delayed_[0].end(), 0.f);
        std::fill(delayed_[1].begin(), delayed_[1].end(), 0.f);
        std::fill(wanted_.begin(), wanted_.end(), 1.f);
        std::fill(released_.begin(), released_.end(), 1.f);
        releasedSum_ = double(window_);
        releasedGain_ = 1.0;
        minimumHead_ = minimumCount_ = 0;
        write_ = 0;
        count_ = 0;
    }

    /** Output delay, in frames. */
    int latency() const { return window_ - 1; }

    void process(float *const *channels, int frames) {
        float *left = channels[0];
        float *right = channels[1];
        for (int i = 0; i < frames; ++i) {
            const float peak = std::max(std::fabs(left[i]), std::fabs(right[i]));
            // One step below the rounded quotient: peak × (ceiling / peak) can round up past the ceiling by
            // one float step. With it, no product does (checked over every float peak up to 16).
            const float wanted = peak > kCeiling ? std::nextafter(kCeiling / peak, 0.f) : 1.f;
            const double held = pushMinimum(wanted);
            // Down at once, up slowly. It never rises above the held minimum, so the bound still holds.
            releasedGain_ = held < releasedGain_ ? held : releasedGain_ + (held - releasedGain_) * releaseCoefficient_;

            const size_t w = size_t(write_);
            // Add the stored float, not the double: the sum then removes exactly what it added.
            const float released = float(releasedGain_);
            releasedSum_ += double(released) - double(released_[w]);
            released_[w] = released;
            delayed_[0][w] = left[i];
            delayed_[1][w] = right[i];
            wanted_[w] = wanted;
            write_ = write_ + 1 == window_ ? 0 : write_ + 1;

            // The oldest frame of the window leaves now. min(): rounding in the running sum must never
            // let it through above the ceiling.
            const size_t oldest = size_t(write_);
            const float gain = std::min(float(releasedSum_ / window_), wanted_[oldest]);
            left[i] = delayed_[0][oldest] * gain;
            right[i] = delayed_[1][oldest] * gain;
            ++count_;
        }
    }

private:
    /** Adds the newest gain and returns the minimum of the last `window_` ones (monotonic queue). */
    double pushMinimum(float gain) {
        // Drop the frame that left the window first, so the ring never holds more than `window_`.
        if (minimumCount_ > 0 && minimumIndex_[size_t(minimumHead_)] <= count_ - window_) {
            minimumHead_ = minimumHead_ + 1 == window_ ? 0 : minimumHead_ + 1;
            --minimumCount_;
        }
        while (minimumCount_ > 0 && minimumGain_[slot(minimumCount_ - 1)] >= gain) --minimumCount_;
        minimumIndex_[slot(minimumCount_)] = count_;
        minimumGain_[slot(minimumCount_)] = gain;
        ++minimumCount_;
        return minimumGain_[size_t(minimumHead_)];
    }

    size_t slot(int offset) const { return size_t((minimumHead_ + offset) % window_); }

    int window_ = 1;
    double releaseCoefficient_ = 1.0;
    std::vector<float> delayed_[2];
    std::vector<float> wanted_;   // gain each delayed frame needs
    std::vector<float> released_; // the moving average's window
    double releasedSum_ = 1.0;
    double releasedGain_ = 1.0;
    // Running minimum: a ring of (frame index, gain), increasing gains from the head.
    std::vector<int64_t> minimumIndex_;
    std::vector<float> minimumGain_;
    int minimumHead_ = 0, minimumCount_ = 0;
    int write_ = 0;
    int64_t count_ = 0;
};
