package com.damianogiusti.slowdowner.domain.loops

import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Beats of a track as fractions 0..1 of the file. Anchored at the start of the file: there is no
 * downbeat detection, so a song that does not start on a beat drifts off the grid by that offset.
 */
class BeatGrid private constructor(val bpm: Float, val durationSeconds: Float) {

    val beatSeconds: Float get() = SECONDS_PER_MINUTE / bpm

    /** One beat as a fraction of the file. */
    val beatFraction: Float get() = beatSeconds / durationSeconds

    /** The nearest beat to [position], within the file. */
    fun snap(position: Float): Float = ((position / beatFraction).roundToLong() * beatFraction).coerceIn(0f, 1f)

    /** Beat count between two positions, fractional. */
    fun beatsBetween(start: Float, end: Float): Float = (end - start) / beatFraction

    /** Positions of every beat in [from]..[to], for beat and bar lines; the index says which beat it is. */
    fun beatsIn(from: Float, to: Float): List<Beat> {
        val first = floor(from.coerceAtLeast(0f) / beatFraction).toLong().let { if (it * beatFraction < from) it + 1 else it }
        return generateSequence(first) { it + 1 }
            .map { index -> Beat(index, index * beatFraction) }
            .takeWhile { it.position <= to.coerceAtMost(1f) }
            .toList()
    }

    data class Beat(val index: Long, val position: Float) {
        val isBarStart: Boolean get() = index % BEATS_PER_BAR == 0L
    }

    companion object {
        const val MIN_BPM = 20f
        const val MAX_BPM = 400f
        /** Bar lines every 4 beats: the design assumes 4/4. */
        const val BEATS_PER_BAR = 4
        private const val SECONDS_PER_MINUTE = 60f

        fun isValidBpm(bpm: Float): Boolean = bpm.isFinite() && bpm in MIN_BPM..MAX_BPM

        /** Null when the tempo or duration cannot make a grid (nothing open, unset tempo). */
        fun of(bpm: Float, durationSeconds: Float): BeatGrid? =
            if (isValidBpm(bpm) && durationSeconds.isFinite() && durationSeconds > 0f) BeatGrid(bpm, durationSeconds) else null
    }
}
