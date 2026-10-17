#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <complex>

/**
 * One RBJ ("Audio EQ Cookbook") biquad: a peaking bell or a shelf, double precision, transposed
 * direct form II with one state pair per stereo channel. Designing touches no memory but its own,
 * so the audio thread can redesign it as often as it likes.
 */
struct Biquad {
    enum class Shape { Peaking, LowShelf, HighShelf };

    double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    std::array<double, 2> z1{}, z2{};

    /** Keeps the frequency inside 20 Hz..0.45·rate: the top band stays below Nyquist at low rates. */
    static double clampFrequency(double frequency, double sampleRate) {
        return std::clamp(frequency, 20.0, std::max(20.0, sampleRate * 0.45));
    }

    void design(Shape shape, double frequency, double gainDb, double q, double sampleRate) {
        const double f = clampFrequency(frequency, sampleRate);
        const double a = std::pow(10.0, gainDb / 40.0);
        const double w = 2 * M_PI * f / sampleRate;
        const double cosw = std::cos(w), sinw = std::sin(w);
        double nb0, nb1, nb2, na0, na1, na2;
        if (shape == Shape::Peaking) {
            const double alpha = sinw / (2 * q);
            nb0 = 1 + alpha * a;
            nb1 = -2 * cosw;
            nb2 = 1 - alpha * a;
            na0 = 1 + alpha / a;
            na1 = -2 * cosw;
            na2 = 1 - alpha / a;
        } else {
            const double alpha = sinw / 2 * std::sqrt(2.0); // shelf slope S = 1
            const double k = 2 * std::sqrt(a) * alpha;
            const double sign = shape == Shape::LowShelf ? 1.0 : -1.0;
            nb0 = a * ((a + 1) - sign * (a - 1) * cosw + k);
            nb1 = sign * 2 * a * ((a - 1) - sign * (a + 1) * cosw);
            nb2 = a * ((a + 1) - sign * (a - 1) * cosw - k);
            na0 = (a + 1) + sign * (a - 1) * cosw + k;
            na1 = -sign * 2 * ((a - 1) + sign * (a + 1) * cosw);
            na2 = (a + 1) + sign * (a - 1) * cosw - k;
        }
        b0 = nb0 / na0;
        b1 = nb1 / na0;
        b2 = nb2 / na0;
        a1 = na1 / na0;
        a2 = na2 / na0;
    }

    /** Steady-state gain at `frequency`, in dB: |H(e^jw)| of the current coefficients. */
    double magnitudeDb(double frequency, double sampleRate) const {
        const std::complex<double> z1 = std::polar(1.0, -2 * M_PI * frequency / sampleRate);
        const std::complex<double> z2 = z1 * z1;
        const std::complex<double> h = (b0 + b1 * z1 + b2 * z2) / (1.0 + a1 * z1 + a2 * z2);
        return 20 * std::log10(std::abs(h));
    }

    void reset() { z1 = z2 = {}; }

    /** Transposed direct form II on `frames` frames of both channels, from `offset`. */
    void process(float *const *channels, int offset, int frames) {
        for (int c = 0; c < 2; ++c) {
            float *x = channels[c] + offset;
            double s1 = z1[size_t(c)], s2 = z2[size_t(c)];
            for (int i = 0; i < frames; ++i) {
                const double in = x[i];
                const double out = b0 * in + s1;
                s1 = b1 * in - a1 * out + s2;
                s2 = b2 * in - a2 * out;
                x[i] = float(out);
            }
            z1[size_t(c)] = s1;
            z2[size_t(c)] = s2;
        }
    }
};

/**
 * A value that glides to its target, one step per sub-block: a one-pole approach (time constant set
 * by `k`) with a minimum step, so the tail ends in a finite time instead of creeping forever, and
 * lands exactly on the target.
 */
struct Glide {
    double current = 0, target = 0;

    bool isSettled() const { return current == target; }

    void snap() { current = target; }

    void step(double k, double minStep) {
        const double delta = target - current;
        const double size = std::max(std::fabs(delta) * k, minStep);
        if (std::fabs(delta) <= size) current = target;
        else current += delta > 0 ? size : -size;
    }
};

/**
 * Parameter smoothing shared by the EQ and the isolation stage: the parameters move once per
 * 32-frame sub-block, with a ~20 ms time constant, and the coefficients are redesigned from them.
 * A jump in gain becomes a glide of well under a decibel per sub-block: no zipper noise, and every
 * glide ends within about 100 ms.
 */
namespace Smoothing {
constexpr int kSubBlockFrames = 32;
constexpr double kTimeConstantSeconds = 0.020;
// Minimum steps per sub-block, which end the one-pole's tail.
constexpr double kMinGainStepDb = 0.05;
constexpr double kMinOctaveStep = 0.005;
constexpr double kMinLogQStep = 0.005;

inline double coefficient(double sampleRate) {
    return 1 - std::exp(-kSubBlockFrames / (kTimeConstantSeconds * sampleRate));
}
} // namespace Smoothing

/**
 * A biquad whose gain, frequency (in octaves) and Q (as a logarithm) glide to their targets. At
 * 0 dB a bell or a shelf is the identity: an idle filter is skipped, and while its gain is 0 dB its
 * frequency and Q jump straight to their targets, so a band that comes in only fades its gain.
 */
struct SmoothedFilter {
    Biquad::Shape shape = Biquad::Shape::Peaking;
    Glide gainDb, octaves{std::log2(1000.0), std::log2(1000.0)}, logQ;
    Biquad filter;
    bool isRunning = false; // its states hold the signal's history

    bool isIdle() const { return gainDb.current == 0 && gainDb.target == 0; }
    bool isSettled() const { return gainDb.isSettled() && octaves.isSettled() && logQ.isSettled(); }

    void setTarget(double frequency, double gain, double q) {
        octaves.target = std::log2(std::clamp(frequency, 20.0, 20000.0));
        gainDb.target = std::clamp(gain, -12.0, 12.0);
        logQ.target = std::log(std::clamp(q, 0.3, 10.0));
        if (gainDb.current == 0) {
            octaves.snap();
            logQ.snap();
        }
    }

    void snap(double sampleRate) {
        gainDb.snap();
        octaves.snap();
        logQ.snap();
        redesign(sampleRate);
    }

    /** One sub-block's worth of glide; redesigns only while something moves. */
    void step(double k, double sampleRate) {
        if (isSettled()) return;
        gainDb.step(k, Smoothing::kMinGainStepDb);
        octaves.step(k, Smoothing::kMinOctaveStep);
        logQ.step(k, Smoothing::kMinLogQStep);
        if (gainDb.current == 0) {
            octaves.snap();
            logQ.snap();
        }
        redesign(sampleRate);
    }

    void redesign(double sampleRate) {
        filter.design(shape, std::exp2(octaves.current), gainDb.current, std::exp(logQ.current), sampleRate);
    }

    void reset() { filter.reset(); }

    void process(float *const *channels, int offset, int frames) {
        if (isIdle()) {
            isRunning = false; // the identity: nothing to do, and its history no longer matters
            return;
        }
        if (!isRunning) {
            filter.reset();
            isRunning = true;
        }
        filter.process(channels, offset, frames);
    }
};
