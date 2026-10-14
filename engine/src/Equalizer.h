#pragma once

#include <algorithm>
#include <array>
#include <cmath>

/**
 * 7-band EQ with RBJ biquads: shelves at the ends, peaking filters between. A smooth curve between
 * the anchors, and a flat setting is exactly transparent.
 */
class Equalizer {
public:
    static constexpr int kBands = 7;
    static constexpr std::array<double, kBands> kFrequencies{60, 150, 400, 1000, 2400, 6000, 15000};

    void configure(int sampleRate, const std::array<double, kBands> &gainsDb) {
        sampleRate_ = sampleRate;
        isFlat_ = true;
        for (int b = 0; b < kBands; ++b) {
            if (gainsDb[size_t(b)] != 0.0) isFlat_ = false;
            const Shape shape = b == 0 ? Shape::LowShelf : b == kBands - 1 ? Shape::HighShelf : Shape::Peaking;
            filters_[size_t(b)].design(shape, kFrequencies[size_t(b)], gainsDb[size_t(b)], sampleRate);
        }
    }

    bool isFlat() const { return isFlat_; }

    void reset() {
        for (auto &f : filters_) f.reset();
    }

    void process(float *const *channels, int frames) {
        if (isFlat_) return;
        for (auto &f : filters_) f.process(channels, frames);
    }

private:
    enum class Shape { LowShelf, Peaking, HighShelf };

    struct Biquad {
        double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
        std::array<double, 2> z1{}, z2{};

        void design(Shape shape, double frequency, double gainDb, int sampleRate) {
            // Keep the top band below Nyquist at low device rates.
            const double f = std::min(frequency, sampleRate * 0.45);
            const double a = std::pow(10.0, gainDb / 40.0);
            const double w = 2 * M_PI * f / sampleRate;
            const double cosw = std::cos(w), sinw = std::sin(w);
            double nb0, nb1, nb2, na0, na1, na2;
            if (shape == Shape::Peaking) {
                const double alpha = sinw / (2 * 1.0); // Q = 1: neighbours overlap into a smooth curve
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

        void reset() { z1 = z2 = {}; }

        // Transposed direct form II, one state pair per channel.
        void process(float *const *channels, int frames) {
            for (int c = 0; c < 2; ++c) {
                float *x = channels[c];
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

    int sampleRate_ = 44100;
    bool isFlat_ = true;
    std::array<Biquad, kBands> filters_{};
};
