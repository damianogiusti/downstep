package com.damianogiusti.slowdowner.domain.tempo

/** What a tempo analysis found: the first beat at [beatOffsetSeconds] of the file, [confidence] 0..1. */
data class TempoEstimate(val bpm: Float, val beatOffsetSeconds: Float, val confidence: Float)

/**
 * Tempo detection over the track open in the engine (onset-energy autocorrelation in the C engine).
 * Platforms without one bind nothing: [DetectTrackTempoUseCase.isAvailable] is then false and the
 * tempo can only be typed or tapped.
 */
interface TempoDetector {
    /**
     * The open track's tempo and first beat; null when nothing is open or the track has no steady
     * pulse. [hintBpm] keeps that tempo and finds only where its beats fall. Runs off the caller's
     * thread (a fraction of a second for a song).
     */
    suspend fun detect(hintBpm: Float? = null): TempoEstimate?
}
