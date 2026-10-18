package com.damianogiusti.slowdowner.domain.loops

/**
 * The tempo of a track as fractions 0..1 of the file: how long a beat is, for beat counts, the
 * length of a new loop and the zoom window. It has no phase, and loop edits never snap to it.
 */
class BeatGrid private constructor(val bpm: Float, val durationSeconds: Float) {

    val beatSeconds: Float get() = SECONDS_PER_MINUTE / bpm

    /** One beat as a fraction of the file. */
    val beatFraction: Float get() = beatSeconds / durationSeconds

    /** Beat count between two positions, fractional. */
    fun beatsBetween(start: Float, end: Float): Float = (end - start) / beatFraction

    companion object {
        const val MIN_BPM = 20f
        const val MAX_BPM = 400f
        /** Four beats to a bar (the zoom strip's "n bars"): the design assumes 4/4. */
        const val BEATS_PER_BAR = 4
        private const val SECONDS_PER_MINUTE = 60f

        fun isValidBpm(bpm: Float): Boolean = bpm.isFinite() && bpm in MIN_BPM..MAX_BPM

        /** Null when the tempo or duration cannot make a grid (nothing open, unset tempo). */
        fun of(bpm: Float, durationSeconds: Float): BeatGrid? =
            if (isValidBpm(bpm) && durationSeconds.isFinite() && durationSeconds > 0f) BeatGrid(bpm, durationSeconds) else null
    }
}
