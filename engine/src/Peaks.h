#pragma once

#include "Pcm.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <vector>

/**
 * Loudness outline of a decoded file, for drawing its waveform: the largest |sample| of both channels
 * in every block of kFramesPerPeak frames. Built once per open (a few ms for a 5-minute song) and
 * read many times, at any zoom, without touching the audio again.
 */
class PeakSummary {
public:
    /** About 6 ms at 44.1 kHz: finer than a zoomed loop of a second or two ever needs on screen. */
    static constexpr int64_t kFramesPerPeak = 256;

    static std::shared_ptr<const PeakSummary> of(const PcmBuffer &pcm) {
        auto summary = std::make_shared<PeakSummary>();
        const int64_t count = (pcm.frames + kFramesPerPeak - 1) / kFramesPerPeak;
        summary->peaks_.assign(size_t(std::max<int64_t>(count, 0)), 0.f);
        const float *left = pcm.channelData(0);
        const float *right = pcm.channelData(1);
        for (int64_t block = 0; block < count; ++block) {
            const int64_t first = block * kFramesPerPeak;
            const int64_t last = std::min(first + kFramesPerPeak, pcm.frames);
            float peak = 0.f;
            for (int64_t frame = first; frame < last; ++frame) {
                peak = std::max(peak, std::max(std::fabs(left[frame]), std::fabs(right[frame])));
            }
            // Decoders can overshoot full scale a little: the outline stays within 0..1.
            summary->peaks_[size_t(block)] = std::min(peak, 1.f);
        }
        return summary;
    }

    /**
     * Fills `buckets` values with the peak of each equal slice of from..to (fractions 0..1 of the
     * file). Slices narrower than one block repeat its value. Returns the number of values written.
     */
    int read(double from, double to, float *out, int buckets) const {
        const auto count = int64_t(peaks_.size());
        if (count == 0 || buckets <= 0 || out == nullptr) return 0;
        from = std::clamp(from, 0.0, 1.0);
        to = std::clamp(to, from, 1.0);
        const double span = to - from;
        for (int i = 0; i < buckets; ++i) {
            const double start = from + span * i / buckets;
            const double end = from + span * (i + 1) / buckets;
            const int64_t first = std::min(count - 1, int64_t(start * double(count)));
            const int64_t last = std::clamp(int64_t(std::ceil(end * double(count))), first + 1, count);
            float peak = 0.f;
            for (int64_t block = first; block < last; ++block) peak = std::max(peak, peaks_[size_t(block)]);
            out[i] = peak;
        }
        return buckets;
    }

private:
    std::vector<float> peaks_;
};
