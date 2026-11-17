#include "Loudness.h"

#include <algorithm>
#include <cmath>
#include <exception>
#include <limits>
#include <thread>
#include <vector>

namespace loudness {

namespace {

constexpr double kAbsoluteGateLufs = -70.0;
constexpr double kRelativeGateLu = -10.0;
constexpr int kSegmentsPerBlock = 4; // 400 ms blocks moving by 100 ms: 75% overlap

double toLufs(double meanSquare) { return -0.691 + 10.0 * std::log10(meanSquare); }

/** The two K-weighting stages for one channel, transposed direct form II. */
struct KWeightingFilter {
    std::array<Biquad, 2> stages;
    std::array<double, 2> s1{}, s2{};

    double process(double x) {
        for (size_t f = 0; f < 2; ++f) {
            const Biquad &q = stages[f];
            const double y = q.b0 * x + s1[f];
            s1[f] = q.b1 * x - q.a1 * y + s2[f];
            s2[f] = q.b2 * x - q.a2 * y;
            x = y;
        }
        return x;
    }
};

/**
 * Sum of squares of the K-weighted channels for the 100 ms segments [first, last). Both channels in one
 * loop: two independent filter chains run side by side, about twice as fast. The filters start from
 * zero [warmUpFrames] before the first segment (fewer at the start of the file), so a range measured on
 * its own thread matches the one measured in a single pass.
 */
void segmentEnergies(const PcmBuffer &pcm, int segmentFrames, int64_t warmUpFrames, size_t first, size_t last,
                     std::vector<double> &energies) {
    const auto stages = kWeighting(pcm.sampleRate);
    KWeightingFilter left{stages}, right{stages};
    const float *l = pcm.data[0].data();
    const float *r = pcm.channels > 1 ? pcm.data[1].data() : nullptr;
    const size_t start = first * size_t(segmentFrames);
    for (size_t i = start - size_t(std::min<int64_t>(warmUpFrames, int64_t(start))); i < start; ++i) {
        left.process(l[i]);
        if (r) right.process(r[i]);
    }
    for (size_t segment = first; segment < last; ++segment) {
        double sum = 0;
        const size_t begin = segment * size_t(segmentFrames);
        const size_t end = begin + size_t(segmentFrames);
        if (r) {
            for (size_t i = begin; i < end; ++i) {
                const double x = left.process(l[i]), y = right.process(r[i]);
                sum += x * x + y * y;
            }
        } else {
            // A mono file is one channel in memory but plays on both speakers: count it twice.
            for (size_t i = begin; i < end; ++i) {
                const double x = left.process(l[i]);
                sum += 2.0 * x * x;
            }
        }
        energies[segment] = sum;
    }
}

/**
 * Per 100 ms segment, the partial tail left out. The filters are recursive, so one pass is serial: about
 * 2.4 s for 2.5 hours of 48 kHz stereo (M1 Pro). Split into ranges on [threads] threads, each range
 * warms its filters up on the second before it. Both K-weighting stages settle within a few hundred
 * milliseconds (the 38 Hz high-pass is the slowest), so the result matches the single pass to about
 * 1e-6 LU.
 */
std::vector<double> segmentEnergies(const PcmBuffer &pcm, int segmentFrames, int threads) {
    std::vector<double> energies(size_t(pcm.frames / segmentFrames), 0.0);
    const size_t segments = energies.size();
    const size_t count = std::max<size_t>(1, std::min(size_t(std::max(threads, 1)), segments));
    const int64_t warmUpFrames = pcm.sampleRate;
    const auto measure = [&](size_t k) {
        segmentEnergies(pcm, segmentFrames, warmUpFrames, segments * k / count, segments * (k + 1) / count, energies);
    };
    std::vector<std::thread> workers;
    size_t next = 1;
    try {
        workers.reserve(count - 1);
        for (; next < count; ++next) workers.emplace_back(measure, next);
    } catch (const std::exception &) {
        // No thread or memory to spare (std::system_error, std::bad_alloc): the ranges not handed out
        // are measured here instead.
    }
    for (size_t k = next; k < count; ++k) measure(k);
    measure(0);
    for (auto &worker : workers) worker.join();
    return energies;
}

} // namespace

int loudnessThreads(const PcmBuffer &pcm) {
    // A thread per 30 s at least: below that, starting it costs more than it saves.
    const int64_t perThread = int64_t(pcm.sampleRate) * 30;
    if (pcm.sampleRate <= 0 || pcm.frames < 2 * perThread) return 1;
    const int64_t hardware = std::max<int64_t>(std::thread::hardware_concurrency(), 1);
    return int(std::min({hardware, int64_t(kMaxLoudnessThreads), pcm.frames / perThread}));
}

std::array<Biquad, 2> kWeighting(int sampleRate) {
    // The analog prototypes of the BS.1770 filters, so the curve is the same at any sample rate. At
    // 48 kHz they give the coefficients printed in the recommendation.
    const double rate = double(sampleRate);

    const double shelfFrequency = 1681.974450955533;
    const double shelfGainDb = 3.999843853973347;
    const double shelfQ = 0.7071752369554196;
    double k = std::tan(M_PI * shelfFrequency / rate);
    const double vh = std::pow(10.0, shelfGainDb / 20.0);
    const double vb = std::pow(vh, 0.4996667741545416);
    double a0 = 1.0 + k / shelfQ + k * k;
    const Biquad shelf{
        (vh + vb * k / shelfQ + k * k) / a0,
        2.0 * (k * k - vh) / a0,
        (vh - vb * k / shelfQ + k * k) / a0,
        2.0 * (k * k - 1.0) / a0,
        (1.0 - k / shelfQ + k * k) / a0,
    };

    const double highPassFrequency = 38.13547087602444;
    const double highPassQ = 0.5003270373238773;
    k = std::tan(M_PI * highPassFrequency / rate);
    a0 = 1.0 + k / highPassQ + k * k;
    const Biquad highPass{1.0, -2.0, 1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / highPassQ + k * k) / a0};

    return {shelf, highPass};
}

double integratedLoudness(const PcmBuffer &pcm) { return integratedLoudness(pcm, loudnessThreads(pcm)); }

double integratedLoudness(const PcmBuffer &pcm, int threads) {
    const double silence = -std::numeric_limits<double>::infinity();
    const int segmentFrames = int(std::lround(pcm.sampleRate * 0.1));
    if (segmentFrames <= 0 || pcm.channels <= 0) return silence;

    const std::vector<double> energies = segmentEnergies(pcm, segmentFrames, threads);
    if (energies.size() < size_t(kSegmentsPerBlock)) return silence;

    // Mean square of each block, summed over the channels (all weights are 1).
    const double blockFrames = double(segmentFrames) * kSegmentsPerBlock;
    std::vector<double> blocks(energies.size() - kSegmentsPerBlock + 1);
    for (size_t b = 0; b < blocks.size(); ++b) {
        double sum = 0;
        for (int s = 0; s < kSegmentsPerBlock; ++s) sum += energies[b + size_t(s)];
        blocks[b] = sum / blockFrames;
    }

    const auto gatedMean = [&](double gateLufs) {
        double sum = 0;
        size_t count = 0;
        for (const double block : blocks) {
            if (block > 0 && toLufs(block) > gateLufs) {
                sum += block;
                ++count;
            }
        }
        return count > 0 ? sum / double(count) : 0.0;
    };
    const double aboveAbsolute = gatedMean(kAbsoluteGateLufs);
    if (aboveAbsolute <= 0) return silence;
    // Below -60 LUFS the relative gate would fall under the absolute one: both apply.
    const double aboveRelative = gatedMean(std::max(kAbsoluteGateLufs, toLufs(aboveAbsolute) + kRelativeGateLu));
    return aboveRelative > 0 ? toLufs(aboveRelative) : silence;
}

double normalizationGainDb(double lufs) {
    if (!std::isfinite(lufs)) return 0.0;
    return std::clamp(kTargetLufs - lufs, -kMaxCutDb, kMaxBoostDb);
}

} // namespace loudness
