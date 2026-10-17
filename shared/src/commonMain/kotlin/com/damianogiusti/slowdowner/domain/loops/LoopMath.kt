package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import kotlin.math.abs
import kotlin.math.max

/**
 * The loop tools of the player (A/B drag and set, ½, ×2, nudge, new loop, zoom window) as pure
 * functions on fractions 0..1 of the file. Edits are free: positions are kept as they are, never
 * rounded to a beat. Functions return null when the action does not apply (for example doubling
 * past the end).
 */
object LoopMath {
    /** A can't cross B: the two stay at least this far apart, as a fraction of the file. */
    const val MIN_GAP_FRACTION = 0.005f
    const val NEW_LOOP_BEATS = 8
    /** A new loop without a known tempo: about two bars at a common tempo. */
    const val NEW_LOOP_SECONDS = 8f
    /**
     * One nudge: a fixed step in track time, whatever the tempo, speed or track length. Finer than
     * the ±5 s seek buttons, and the same at every tempo, so a loop moves predictably.
     */
    const val NUDGE_SECONDS = 1f
    const val ZOOM_PADDING_RATIO = 0.35f
    const val ZOOM_MIN_PADDING_BEATS = 4

    /**
     * Shortest loop: [MIN_GAP_FRACTION], but never under the engine's own minimum, so the region
     * saved here is the one the engine plays.
     */
    fun minLength(durationSeconds: Float): Float {
        val engineMinimum = if (durationSeconds > 0f) LoopRegion.MIN_LENGTH_SECONDS / durationSeconds else 0f
        return max(MIN_GAP_FRACTION, engineMinimum).coerceAtMost(1f)
    }

    /** A loop between two points in any order, within the file and at least [minLength] long. */
    fun between(first: Float, second: Float, durationSeconds: Float): LoopBounds {
        val minLength = minLength(durationSeconds)
        val start = minOf(first, second).coerceIn(0f, 1f - minLength)
        val end = maxOf(first, second).coerceIn(start + minLength, 1f)
        return LoopBounds(start, end)
    }

    /** A moves to [position] but stays before B. */
    fun moveStart(bounds: LoopBounds, position: Float, durationSeconds: Float): LoopBounds {
        val latest = (bounds.end - minLength(durationSeconds)).coerceAtLeast(0f)
        return bounds.copy(start = position.coerceIn(0f, latest))
    }

    /** B moves to [position] but stays after A. */
    fun moveEnd(bounds: LoopBounds, position: Float, durationSeconds: Float): LoopBounds {
        val earliest = (bounds.start + minLength(durationSeconds)).coerceAtMost(1f)
        return bounds.copy(end = position.coerceIn(earliest, 1f))
    }

    /**
     * The whole loop shifted by [delta] (a fraction of the file, + later), as the drag of its top bar
     * does: it keeps its length and stops at the edges of the file.
     */
    fun move(bounds: LoopBounds, delta: Float): LoopBounds {
        val shift = delta.coerceIn(-bounds.start, (1f - bounds.end).coerceAtLeast(-bounds.start))
        if (shift == 0f) return bounds
        // Pinned to an edge exactly, so a loop pushed against the end stays as long as it was.
        return when {
            shift == -bounds.start -> LoopBounds(0f, bounds.length)
            shift == 1f - bounds.end -> LoopBounds(1f - bounds.length, 1f)
            else -> LoopBounds(bounds.start + shift, bounds.end + shift)
        }
    }

    /** Half as long, A fixed. Null when the half would be shorter than [minLength]. */
    fun halve(bounds: LoopBounds, durationSeconds: Float): LoopBounds? {
        val half = bounds.length / 2
        // Tolerance, so that a loop of exactly twice the minimum can still be halved despite rounding.
        if (half < minLength(durationSeconds) - ROUNDING) return null
        return bounds.copy(end = bounds.start + half)
    }

    /** Twice as long, A fixed. Null when it would run past the end of the file. */
    fun double(bounds: LoopBounds): LoopBounds? {
        var end = bounds.start + bounds.length * 2
        if (end > 1f && end < 1f + ROUNDING) end = 1f
        return if (end <= 1f) bounds.copy(end = end) else null
    }

    /**
     * The whole loop moves by [NUDGE_SECONDS] of track time; [direction] is +1 or -1. Stops at the
     * edges of the file; null when it is already there.
     */
    fun nudge(bounds: LoopBounds, direction: Int, durationSeconds: Float): LoopBounds? {
        if (direction == 0 || durationSeconds <= 0f) return null
        val step = NUDGE_SECONDS / durationSeconds
        val shift = (step * direction.coerceIn(-1, 1)).coerceIn(-bounds.start, 1f - bounds.end)
        if (abs(shift) < ROUNDING) return null
        return LoopBounds(bounds.start + shift, bounds.end + shift)
    }

    /**
     * The loop the `L` key adds at [playhead]: [NEW_LOOP_BEATS] beats long with a [tempo], or
     * [NEW_LOOP_SECONDS] without one, starting exactly at the playhead. Near the end of the file it
     * is moved back so it keeps its length.
     */
    fun newLoopAt(playhead: Float, durationSeconds: Float, tempo: BeatGrid?): LoopBounds {
        val wanted = tempo?.let { it.beatFraction * NEW_LOOP_BEATS }
            ?: if (durationSeconds > 0f) NEW_LOOP_SECONDS / durationSeconds else 1f
        val length = wanted.coerceIn(minLength(durationSeconds), 1f)
        val start = playhead.coerceIn(0f, 1f - length)
        return LoopBounds(start, start + length)
    }

    /**
     * What the zoom strip shows: the loop ± max(35% of its length, [ZOOM_MIN_PADDING_BEATS] beats),
     * within the file. Without a tempo the padding is just the 35%.
     */
    fun zoomWindow(bounds: LoopBounds, tempo: BeatGrid?): LoopBounds {
        val beatsPadding = tempo?.let { it.beatFraction * ZOOM_MIN_PADDING_BEATS } ?: 0f
        val padding = max(bounds.length * ZOOM_PADDING_RATIO, beatsPadding)
        return LoopBounds((bounds.start - padding).coerceAtLeast(0f), (bounds.end + padding).coerceAtMost(1f))
    }

    private const val ROUNDING = 1e-5f
}
