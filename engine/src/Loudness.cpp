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
 * The channels 0 and 1 of the sum of the streams at gain 1, for a frame range. One stream is read in place.
 * Stems are summed into a small scratch (one segment), so measuring them never needs a summed copy of the
 * whole track. `right` is null only when every stream is mono: then the sum plays on both speakers.
 */
class FrameReader {
public:
    FrameReader(const PcmBuffer *const *streams, size_t count, size_t maxFrames) : streams_(streams), count_(count) {
        isMono_ = true;
        for (size_t s = 0; s < count; ++s) isMono_ = isMono_ && streams[s]->channels == 1;
        if (count > 1) {
            left_.resize(maxFrames);
            if (!isMono_) right_.resize(maxFrames);
        }
    }

    bool isMono() const { return isMono_; }

    void read(size_t first, size_t frames, const float *&left, const float *&right) {
        if (count_ == 1) {
            left = streams_[0]->data[0].data() + first;
            right = isMono_ ? nullptr : streams_[0]->data[1].data() + first;
            return;
        }
        std::fill_n(left_.begin(), frames, 0.f);
        if (!isMono_) std::fill_n(right_.begin(), frames, 0.f);
        for (size_t s = 0; s < count_; ++s) {
            const float *l = streams_[s]->channelData(0) + first;
            for (size_t i = 0; i < frames; ++i) left_[i] += l[i];
            if (isMono_) continue;
            const float *r = streams_[s]->channelData(1) + first;
            for (size_t i = 0; i < frames; ++i) right_[i] += r[i];
        }
        left = left_.data();
        right = isMono_ ? nullptr : right_.data();
    }

private:
    const PcmBuffer *const *streams_;
    size_t count_;
    bool isMono_;
    std::vector<float> left_, right_;
};

/**
 * Sum of squares of the K-weighted channels for the 100 ms segments [first, last). Both channels in one
 * loop: two independent filter chains run side by side, about twice as fast. The filters start from
 * zero [warmUpFrames] before the first segment (fewer at the start of the file), so a range measured on
 * its own thread matches the one measured in a single pass.
 */
void segmentEnergies(const PcmBuffer *const *streams, size_t count, int segmentFrames, int64_t warmUpFrames, size_t first,
                     size_t last, std::vector<double> &energies) {
    const auto stages = kWeighting(streams[0]->sampleRate);
    KWeightingFilter left{stages}, right{stages};
    FrameReader reader(streams, count, size_t(segmentFrames));
    const size_t start = first * size_t(segmentFrames);
    const size_t warmUpFrom = start - size_t(std::min<int64_t>(warmUpFrames, int64_t(start)));
    for (size_t i = warmUpFrom; i < start; i += size_t(segmentFrames)) {
        const size_t n = std::min(size_t(segmentFrames), start - i);
        const float *l, *r;
        reader.read(i, n, l, r);
        for (size_t k = 0; k < n; ++k) {
            left.process(l[k]);
            if (r) right.process(r[k]);
        }
    }
    for (size_t segment = first; segment < last; ++segment) {
        double sum = 0;
        const float *l, *r;
        reader.read(segment * size_t(segmentFrames), size_t(segmentFrames), l, r);
        if (r) {
            for (int i = 0; i < segmentFrames; ++i) {
                const double x = left.process(l[i]), y = right.process(r[i]);
                sum += x * x + y * y;
            }
        } else {
            // A mono file is one channel in memory but plays on both speakers: count it twice.
            for (int i = 0; i < segmentFrames; ++i) {
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
std::vector<double> segmentEnergies(const PcmBuffer *const *streams, size_t count, int segmentFrames, int threads) {
    const PcmBuffer &pcm = *streams[0];
    std::vector<double> energies(size_t(pcm.frames / segmentFrames), 0.0);
    const size_t segments = energies.size();
    const size_t ranges = std::max<size_t>(1, std::min(size_t(std::max(threads, 1)), segments));
    const int64_t warmUpFrames = pcm.sampleRate;
    const auto measure = [&](size_t k) {
        segmentEnergies(streams, count, segmentFrames, warmUpFrames, segments * k / ranges, segments * (k + 1) / ranges,
                        energies);
    };
    std::vector<std::thread> workers;
    size_t next = 1;
    try {
        workers.reserve(ranges - 1);
        for (; next < ranges; ++next) workers.emplace_back(measure, next);
    } catch (const std::exception &) {
        // No thread or memory to spare (std::system_error, std::bad_alloc): the ranges not handed out
        // are measured here instead.
    }
    for (size_t k = next; k < ranges; ++k) measure(k);
    measure(0);
    for (auto &worker : workers) worker.join();
    return energies;
}

double integratedLoudness(const PcmBuffer *const *streams, size_t count, int threads);

} // namespace

int loudnessThreads(const PcmBuffer &pcm) {
    // A thread per 30 s at least: below that, starting it costs more than it saves.
    const int64_t perThread = int64_t(pcm.sampleRate) * 30;
    if (pcm.sampleRate <= 0 || pcm.frames < 2 * perThread) return 1;
    const int64_t hardware = std::max<int64_t>(std::thread::hardware_concurrency(), 1);
    return int(std::min({hardware, int64_t(kMaxLoudnessThreads), pcm.frames / perThread}));
}

int loudnessThreads(const PcmSource &source) { return loudnessThreads(*source.streams.front()); }

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
    const PcmBuffer *stream = &pcm;
    return integratedLoudness(&stream, 1, threads);
}

double integratedLoudness(const PcmSource &source) { return integratedLoudness(source, loudnessThreads(source)); }

double integratedLoudness(const PcmSource &source, int threads) {
    std::vector<const PcmBuffer *> streams;
    for (const auto &stream : source.streams) streams.push_back(stream.get());
    return integratedLoudness(streams.data(), streams.size(), threads);
}

namespace {

double integratedLoudness(const PcmBuffer *const *streams, size_t count, int threads) {
    const PcmBuffer &pcm = *streams[0];
    const double silence = -std::numeric_limits<double>::infinity();
    const int segmentFrames = int(std::lround(pcm.sampleRate * 0.1));
    if (segmentFrames <= 0 || pcm.channels <= 0) return silence;

    const std::vector<double> energies = segmentEnergies(streams, count, segmentFrames, threads);
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

} // namespace

double normalizationGainDb(double lufs) {
    if (!std::isfinite(lufs)) return 0.0;
    return std::clamp(kTargetLufs - lufs, -kMaxCutDb, kMaxBoostDb);
}

} // namespace loudness
