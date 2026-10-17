#include "TempoDetector.h"

#include <algorithm>
#include <cmath>
#include <numeric>
#include <vector>

namespace {

constexpr double kEnvelopeRate = 200.0; // onset envelope: 5 ms hops
constexpr double kMinBpm = 40.0, kMaxBpm = 240.0;
constexpr double kPreferredBpm = 120.0;
constexpr double kPreferenceOctaves = 1.0; // the width of that preference
// A tempo is doubled when its off-beats are nearly as strong as its beats (and the result stays
// below this): a pulse of 87 with every half beat struck is 174.
constexpr double kMaxDoubledBpm = 200.0;
constexpr double kOffBeatRatio = 0.7;
constexpr double kMinConfidence = 0.12;
constexpr double kMinSeconds = 4.0;
constexpr double kSilence = 1e-8; // mean square, about -80 dBFS
constexpr double kLocalMeanSeconds = 0.25;

/** The value between hops, linearly. */
double at(const std::vector<double> &x, double index) {
    if (index < 0) return 0;
    const size_t i = size_t(index);
    if (i + 1 >= x.size()) return i < x.size() ? x[i] : 0;
    const double t = index - double(i);
    return x[i] * (1 - t) + x[i + 1] * t;
}

/** Autocorrelation at a fractional lag, per overlapping pair. */
double autocorrelation(const std::vector<double> &x, double lag) {
    const size_t whole = size_t(lag);
    if (whole + 1 >= x.size()) return 0;
    const double t = lag - double(whole);
    double sum = 0;
    const size_t pairs = x.size() - whole - 1;
    for (size_t k = 0; k < pairs; ++k) sum += x[k] * (x[k + whole] * (1 - t) + x[k + whole + 1] * t);
    return sum / double(pairs);
}

/** Mean envelope value on the beats of `period` starting at `phase` (both in hops). */
double comb(const std::vector<double> &x, double period, double phase) {
    double sum = 0;
    int count = 0;
    for (double p = phase; p < double(x.size()) - 1; p += period, ++count) sum += at(x, p);
    return count ? sum / count : 0;
}

struct Fit {
    double period = 0, phase = 0, score = -1;
};

/** The best phase for each period in [from, to] (`periods` of them), phases `phaseStep` apart in [phaseFrom, phaseTo). */
Fit search(const std::vector<double> &x, double from, double to, int periods, double phaseFrom, double phaseTo,
           double phaseStep) {
    Fit best;
    for (int i = 0; i < periods; ++i) {
        const double period = periods > 1 ? from + (to - from) * i / (periods - 1) : from;
        for (double phase = phaseFrom; phase < phaseTo; phase += phaseStep) {
            const double wrapped = std::fmod(phase + period, period);
            const double score = comb(x, period, wrapped);
            if (score > best.score) best = {period, wrapped, score};
        }
    }
    return best;
}

} // namespace

bool detectTempo(const PcmBuffer &pcm, double hintBpm, TempoEstimate &out) {
    out = {};
    if (pcm.sampleRate <= 0 || pcm.channels < 1 || pcm.seconds() < kMinSeconds) return false;
    const int hop = std::max(1, int(std::lround(pcm.sampleRate / kEnvelopeRate)));
    const double envelopeRate = double(pcm.sampleRate) / hop;
    const size_t hops = size_t(pcm.frames / hop);

    // Energy per hop of the mono mix, and of its first difference: the attacks stand out there.
    std::vector<double> energy(hops), attack(hops);
    const float *left = pcm.channelData(0);
    const float *right = pcm.channelData(1);
    float previous = 0.f;
    for (size_t k = 0; k < hops; ++k) {
        double e = 0, a = 0;
        for (size_t i = k * size_t(hop), end = i + size_t(hop); i < end; ++i) {
            const float x = 0.5f * (left[i] + right[i]);
            const float d = x - previous;
            e += double(x) * x;
            a += double(d) * d;
            previous = x;
        }
        energy[k] = e / hop;
        attack[k] = a / hop;
    }
    const double meanEnergy = std::accumulate(energy.begin(), energy.end(), 0.0) / double(hops);
    if (!(meanEnergy > kSilence)) return false;

    // Onset strength: how much the log energy rises, in both bands. The floor, relative to the
    // track's level, keeps quiet passages from turning their noise into onsets.
    std::vector<double> onset(hops, 0.0);
    for (const auto *band : {&energy, &attack}) {
        const double mean = std::accumulate(band->begin(), band->end(), 0.0) / double(hops);
        const double floor = 1e-3 * mean + 1e-12;
        double last = std::log(floor + (*band)[0]);
        for (size_t k = 1; k < hops; ++k) {
            const double now = std::log(floor + (*band)[k]);
            onset[k] += std::max(0.0, now - last);
            last = now;
        }
    }
    // Minus the local mean, rectified: only what stands out of its surroundings.
    {
        const size_t half = size_t(kLocalMeanSeconds * envelopeRate);
        std::vector<double> sums(hops + 1, 0.0);
        for (size_t k = 0; k < hops; ++k) sums[k + 1] = sums[k] + onset[k];
        std::vector<double> rectified(hops);
        for (size_t k = 0; k < hops; ++k) {
            const size_t from = k > half ? k - half : 0, to = std::min(hops, k + half + 1);
            rectified[k] = std::max(0.0, onset[k] - (sums[to] - sums[from]) / double(to - from));
        }
        onset.swap(rectified);
    }

    // Smoothed (a Gaussian, sigma one hop): a beat period is rarely a whole number of hops, and the
    // sharp onsets of a period of 93.75 hops would otherwise line up best at twice that.
    {
        constexpr double kernel[] = {0.054, 0.242, 0.399, 0.242, 0.054};
        std::vector<double> smooth(hops, 0.0);
        for (size_t k = 0; k < hops; ++k)
            for (int j = -2; j <= 2; ++j) {
                const auto i = int64_t(k) + j;
                if (i >= 0 && i < int64_t(hops)) smooth[k] += kernel[j + 2] * onset[size_t(i)];
            }
        onset.swap(smooth);
    }

    // Autocorrelation over the tempo range.
    const int minLag = int(std::floor(envelopeRate * 60.0 / kMaxBpm));
    const int maxLag = int(std::ceil(envelopeRate * 60.0 / kMinBpm));
    if (hops < size_t(maxLag) * 3) return false;
    const double zero = autocorrelation(onset, 0);
    if (!(zero > 0)) return false;
    std::vector<double> correlation(size_t(maxLag + 2), 0.0);
    for (int lag = minLag - 1; lag <= maxLag + 1; ++lag) correlation[size_t(lag)] = autocorrelation(onset, lag);
    double mean = 0;
    for (int lag = minLag; lag <= maxLag; ++lag) mean += correlation[size_t(lag)];
    mean /= double(maxLag - minLag + 1);

    double period; // in hops
    if (hintBpm > 0) {
        period = envelopeRate * 60.0 / hintBpm;
    } else {
        int best = minLag;
        double bestScore = -1;
        for (int lag = minLag; lag <= maxLag; ++lag) {
            const double bpm = envelopeRate * 60.0 / lag;
            const double octaves = std::log2(bpm / kPreferredBpm) / kPreferenceOctaves;
            const double score = correlation[size_t(lag)] * std::exp(-0.5 * octaves * octaves);
            if (score > bestScore) {
                bestScore = score;
                best = lag;
            }
        }
        // Parabolic peak between the neighbouring lags.
        const double a = correlation[size_t(best - 1)], b = correlation[size_t(best)], c = correlation[size_t(best + 1)];
        const double curvature = a - 2 * b + c;
        period = best + (curvature < 0 ? std::clamp(0.5 * (a - c) / curvature, -0.5, 0.5) : 0.0);
        const double confidence = (autocorrelation(onset, period) - mean) / (zero - mean);
        if (!(confidence >= kMinConfidence)) return false;
        // Every half beat struck as hard as the beat: the pulse is twice as fast.
        const double half = period / 2;
        if (half >= minLag && envelopeRate * 60.0 / half <= kMaxDoubledBpm &&
            autocorrelation(onset, half) >= kOffBeatRatio * autocorrelation(onset, period))
            period = half;
    }

    // Period and phase together: a comb over the whole track is far more precise than one lag.
    const std::vector<double> &smooth = onset;
    Fit fit;
    if (hintBpm > 0) {
        fit = search(smooth, period, period, 1, 0, period, 1.0);
        fit = search(smooth, period, period, 1, fit.phase - 1, fit.phase + 1, 0.05);
    } else {
        fit = search(smooth, period * 0.98, period * 1.02, 41, 0, period * 1.02, 1.0);
        fit = search(smooth, fit.period * 0.999, fit.period * 1.001, 41, fit.phase - 2, fit.phase + 2, 0.25);
        fit = search(smooth, fit.period, fit.period, 1, fit.phase - 0.25, fit.phase + 0.25, 0.01);
    }

    const double confidence = (autocorrelation(onset, fit.period) - mean) / (zero - mean);
    out.bpm = hintBpm > 0 ? hintBpm : envelopeRate * 60.0 / fit.period;
    // A hop's energy rises with an attack inside it: hop k starts at k · hop frames.
    out.beatOffsetSeconds = std::fmod(fit.phase / envelopeRate, 60.0 / out.bpm);
    out.confidence = std::clamp(confidence, 0.0, 1.0);
    return std::isfinite(out.bpm) && out.bpm > 0;
}
