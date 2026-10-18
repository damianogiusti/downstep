package com.damianogiusti.slowdowner.domain.tempo

import com.damianogiusti.slowdowner.domain.loops.BeatGrid

/**
 * Tap tempo: the BPM of the last few taps. A pause longer than [RESET_MILLIS] starts over, so a new
 * tempo never averages with the previous one. [now] is a monotonic clock in milliseconds.
 */
class TapTempo(private val now: () -> Long) {

    private val taps = ArrayDeque<Long>()

    /** Records a tap. The tempo once there are two taps close enough, rounded to 0.1 BPM; else null. */
    fun tap(): Float? {
        val time = now()
        if (taps.isNotEmpty() && time - taps.last() > RESET_MILLIS) taps.clear()
        taps.addLast(time)
        while (taps.size > MAX_TAPS) taps.removeFirst()
        return bpm
    }

    /** The tempo of the taps so far, or null with fewer than two. */
    val bpm: Float?
        get() {
            if (taps.size < 2) return null
            val interval = (taps.last() - taps.first()).toFloat() / (taps.size - 1)
            if (interval <= 0f) return null
            val bpm = kotlin.math.round(MILLIS_PER_MINUTE / interval * 10f) / 10f
            return bpm.coerceIn(BeatGrid.MIN_BPM, BeatGrid.MAX_BPM)
        }

    val tapCount: Int get() = taps.size

    fun reset() = taps.clear()

    companion object {
        /** Longer than a beat at 30 BPM: slower tapping is a new start, not a tempo. */
        const val RESET_MILLIS = 2_000L
        /** The average over the last eight taps: steady, yet it follows a change within a bar or two. */
        const val MAX_TAPS = 8
        private const val MILLIS_PER_MINUTE = 60_000f
    }
}
