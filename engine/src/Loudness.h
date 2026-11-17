#pragma once

#include "Pcm.h"

#include <array>

/**
 * Integrated loudness after ITU-R BS.1770-4 (the measure behind EBU R128): K-weighting, 400 ms blocks
 * with 75% overlap, an absolute gate at -70 LUFS and a relative gate 10 LU below the gated mean.
 */
namespace loudness {

/**
 * -16 LUFS: the level AES recommends for streaming, close to Apple Music. -14 (Spotify, YouTube) would be
 * 2 dB louder, but more quiet tracks would then lean on the limiter after the boost. Practice is about
 * hearing details, so headroom wins over the last 2 dB.
 */
constexpr double kTargetLufs = -16.0;
/** A quiet recording is raised at most this much: more would mostly lift noise and hiss. */
constexpr double kMaxBoostDb = 12.0;
/** Only a guard against odd measurements: real masters need at most about -12 dB to reach the target. */
constexpr double kMaxCutDb = 20.0;

/** One biquad, a0 normalised to 1. */
struct Biquad {
    double b0, b1, b2, a1, a2;
};

/** The two K-weighting stages at `sampleRate`: the high-shelf pre-filter, then the RLB high-pass. */
std::array<Biquad, 2> kWeighting(int sampleRate);

/**
 * Integrated loudness of channels 0 and 1, in LUFS, weight 1 each (what the player outputs). -infinity
 * when no block passes the absolute gate: silence, or a file shorter than one 400 ms block.
 */
double integratedLoudness(const PcmBuffer &pcm);

/** The same on `threads` threads (1 = a single pass on the caller's), for tests. */
double integratedLoudness(const PcmBuffer &pcm, int threads);

/** Enough threads to measure hours of audio in a fraction of a second. */
constexpr int kMaxLoudnessThreads = 8;

/** The threads integratedLoudness uses for `pcm`: one per 30 s at least (so 1 under a minute), up to the cores and the cap. */
int loudnessThreads(const PcmBuffer &pcm);

/** Gain that brings `lufs` to the target, clamped. 0 dB for silence. */
double normalizationGainDb(double lufs);

} // namespace loudness
