#pragma once

#include "Biquad.h"

#include <array>

/**
 * Isolate: brings the selected range (low, mid, high; bit 0, 1, 2 of the mask) forward by pulling
 * everything else down 12 dB. A fixed stage after the user's EQ, so the user's own bands, wherever
 * they are, stay as they are under the selection; and it works whether the EQ is on or off.
 *
 * Seven fixed filters split the spectrum as the first EQ did: a low shelf at 60 Hz, bells (Q 1) at
 * 150, 400, 1k, 2.4k and 6k Hz, a high shelf at 15 kHz. Low is the first two, mid the next three,
 * high the last two. Toggling glides like every EQ change; a full mix (0) is skipped entirely.
 *
 * Audio thread only, apart from construction; never allocates.
 */
class IsolationFilter {
public:
    static constexpr int kBands = 7;
    static constexpr std::array<double, kBands> kFrequencies{60, 150, 400, 1000, 2400, 6000, 15000};
    static constexpr double kCutDb = -12;
    static constexpr int kLow = 1, kMid = 2, kHigh = 4;

    IsolationFilter() {
        for (int b = 0; b < kBands; ++b) {
            SmoothedFilter &band = bands_[size_t(b)];
            band.shape = b == 0 ? Biquad::Shape::LowShelf : b == kBands - 1 ? Biquad::Shape::HighShelf
                                                                             : Biquad::Shape::Peaking;
            band.setTarget(kFrequencies[size_t(b)], 0, 1);
        }
    }

    void setSampleRate(double sampleRate) {
        sampleRate_ = sampleRate;
        k_ = Smoothing::coefficient(sampleRate);
        for (auto &band : bands_) band.redesign(sampleRate);
    }

    void setMask(int mask, bool snap) {
        const int selected = mask & (kLow | kMid | kHigh);
        for (int b = 0; b < kBands; ++b) {
            const int range = b < 2 ? kLow : b < 5 ? kMid : kHigh;
            const bool cut = selected != 0 && (selected & range) == 0;
            SmoothedFilter &band = bands_[size_t(b)];
            band.setTarget(kFrequencies[size_t(b)], cut ? kCutDb : 0.0, 1);
            if (snap) band.snap(sampleRate_);
        }
    }

    void reset() {
        for (auto &band : bands_) band.reset();
    }

    bool isFlat() const {
        for (const auto &band : bands_)
            if (!band.isIdle()) return false;
        return true;
    }

    void process(float *const *channels, int frames) {
        if (isFlat()) return;
        for (int offset = 0; offset < frames; offset += Smoothing::kSubBlockFrames) {
            const int n = std::min(Smoothing::kSubBlockFrames, frames - offset);
            for (auto &band : bands_) {
                band.step(k_, sampleRate_);
                band.process(channels, offset, n);
            }
        }
    }

private:
    double sampleRate_ = 44100;
    double k_ = Smoothing::coefficient(44100);
    std::array<SmoothedFilter, kBands> bands_{};
};
