#pragma once

#include "Pcm.h"

/** A track's tempo and where its beats fall, at 100% speed. */
struct TempoEstimate {
    double bpm = 0;
    /** The first beat, in seconds of the file: 0 <= offset < 60 / bpm. */
    double beatOffsetSeconds = 0;
    /** 0..1: how clearly the onsets repeat at that tempo. */
    double confidence = 0;
};

/**
 * Tempo and beat phase from the onsets: an onset-strength envelope (the rise of the log energy of
 * the signal and of its attacks, in 5 ms hops), autocorrelated over 40..240 BPM with a mild
 * preference for tempos near 120, then refined, period and phase together, by a comb over the whole
 * track. Bars are not detected: beat 0 is simply the first beat.
 *
 * `hintBpm` > 0 keeps that tempo and finds only its phase (a tempo the user typed or tapped).
 * Returns false for silence, noise, music with no steady pulse, or under four seconds of audio.
 * Pure and blocking (a fraction of a second for a song): call it off the UI and audio threads.
 */
bool detectTempo(const PcmBuffer &pcm, double hintBpm, TempoEstimate &out);
