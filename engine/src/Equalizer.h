#pragma once

#include "Biquad.h"

#include <algorithm>
#include <array>
#include <cmath>

/** One bell of the user's EQ. */
struct EqBandParams {
    double frequencyHz = 1000;
    double gainDb = 0;
    double q = 1;
};

/** The user's EQ as the control thread sets it: plain data, fixed size, copied without allocating. */
struct EqParams {
    static constexpr int kMaxBands = 8; // SD_EQ_MAX_BANDS
    bool enabled = false;
    double preampDb = 0;
    int bandCount = 0;
    std::array<EqBandParams, kMaxBands> bands{};
};

/**
 * The user's EQ: up to 8 RBJ peaking bells, then the preamp. Every change glides (see Smoothing):
 * gain, frequency and Q of each band, the preamp, and turning the EQ on or off, which glides every
 * band to 0 dB and the preamp to unity. Once all of that has settled at flat the EQ is skipped, so
 * a flat EQ is exactly transparent.
 *
 * Audio thread only, apart from construction: setTarget, reset and process never allocate.
 */
class Equalizer {
public:
    static constexpr int kMaxBands = EqParams::kMaxBands;

    void setSampleRate(double sampleRate) {
        sampleRate_ = sampleRate;
        k_ = Smoothing::coefficient(sampleRate);
        for (auto &band : bands_) band.redesign(sampleRate);
    }

    /** `snap` jumps to the new settings at once: a new player (the export) must not glide in from flat. */
    void setTarget(const EqParams &params, bool snap) {
        const int count = std::clamp(params.bandCount, 0, kMaxBands);
        for (int b = 0; b < kMaxBands; ++b) {
            SmoothedFilter &band = bands_[size_t(b)];
            if (b < count) {
                const EqBandParams &p = params.bands[size_t(b)];
                band.setTarget(p.frequencyHz, params.enabled ? p.gainDb : 0.0, p.q);
            } else {
                // A removed band fades out where it is.
                band.gainDb.target = 0;
            }
            if (snap) band.snap(sampleRate_);
        }
        preampDb_.target = params.enabled ? std::clamp(params.preampDb, -24.0, 24.0) : 0.0;
        if (snap) preampDb_.snap();
    }

    /** Clears the filters' history (a seek, or a start): the settings and their glides go on. */
    void reset() {
        for (auto &band : bands_) band.reset();
    }

    /** Settled at flat: process() leaves the signal untouched. */
    bool isFlat() const {
        for (const auto &band : bands_)
            if (!band.isIdle()) return false;
        return preampDb_.current == 0 && preampDb_.target == 0;
    }

    void process(float *const *channels, int frames) {
        if (isFlat()) return;
        for (int offset = 0; offset < frames; offset += Smoothing::kSubBlockFrames) {
            const int n = std::min(Smoothing::kSubBlockFrames, frames - offset);
            for (auto &band : bands_) {
                band.step(k_, sampleRate_);
                band.process(channels, offset, n);
            }
            applyPreamp(channels, offset, n);
        }
    }

    // For the tests.
    double currentGainDb(int band) const { return bands_[size_t(band)].gainDb.current; }
    double currentPreampDb() const { return preampDb_.current; }

private:
    /** A per-sample ramp across the sub-block from the previous gain to the new one. */
    void applyPreamp(float *const *channels, int offset, int frames) {
        const double before = preampDb_.current;
        preampDb_.step(k_, Smoothing::kMinGainStepDb);
        if (before == 0 && preampDb_.current == 0) return;
        const float from = float(std::pow(10.0, before / 20.0));
        const float to = before == preampDb_.current ? from : float(std::pow(10.0, preampDb_.current / 20.0));
        const float slope = (to - from) / float(frames);
        for (int c = 0; c < 2; ++c) {
            float *x = channels[c] + offset;
            for (int i = 0; i < frames; ++i) x[i] *= from + slope * float(i + 1);
        }
    }

    double sampleRate_ = 44100;
    double k_ = Smoothing::coefficient(44100);
    std::array<SmoothedFilter, kMaxBands> bands_{};
    Glide preampDb_;
};
