package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import kotlin.math.abs
import kotlin.math.max

/**
 * The loop tools of the player (A/B drag and set, ½, ×2, nudge, new loop, zoom window) as pure
 * functions on fractions 0..1 of the file. `grid` is the beat grid edits snap to: null when snap is
 * off or the tempo is unknown, and then edits are free. Functions return null when the action does
 * not apply (for example doubling past the end).
 */
object LoopMath {
    /** A can't cross B: the two stay at least this far apart, as a fraction of the file. */
    const val MIN_GAP_FRACTION = 0.005f
    const val NEW_LOOP_BEATS = 8
    /** A new loop without a known tempo: about two bars at a common tempo. */
    const val NEW_LOOP_SECONDS = 8f
    /** One nudge without a beat grid. */
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

    fun snap(position: Float, grid: BeatGrid?): Float = grid?.snap(position) ?: position.coerceIn(0f, 1f)

    /** A loop between two points in any order, within the file and at least [minLength] long. */
    fun between(first: Float, second: Float, durationSeconds: Float): LoopBounds {
        val minLength = minLength(durationSeconds)
        val start = minOf(first, second).coerceIn(0f, 1f - minLength)
        val end = maxOf(first, second).coerceIn(start + minLength, 1f)
        return LoopBounds(start, end)
    }

    /** A moves to [position] (snapped) but stays before B. */
    fun moveStart(bounds: LoopBounds, position: Float, durationSeconds: Float, grid: BeatGrid?): LoopBounds {
        val latest = (bounds.end - minLength(durationSeconds)).coerceAtLeast(0f)
        return bounds.copy(start = snap(position, grid).coerceIn(0f, latest))
    }

    /** B moves to [position] (snapped) but stays after A. */
    fun moveEnd(bounds: LoopBounds, position: Float, durationSeconds: Float, grid: BeatGrid?): LoopBounds {
        val earliest = (bounds.start + minLength(durationSeconds)).coerceAtMost(1f)
        return bounds.copy(end = snap(position, grid).coerceIn(earliest, 1f))
    }

    /** Half as long, A fixed. Null when the half would be shorter than [minLength]. */
    fun halve(bounds: LoopBounds, durationSeconds: Float, grid: BeatGrid?): LoopBounds? {
        val half = bounds.length / 2
        // Tolerance, so that a loop of exactly twice the minimum can still be halved despite rounding.
        if (half < minLength(durationSeconds) - ROUNDING) return null
        val end = bounds.start + half
        // An odd beat count halves off the grid: back onto it, unless that would undo the halving.
        val snapped = grid?.snap(end)?.takeIf { it > bounds.start && it < bounds.end } ?: end
        return bounds.copy(end = snapped)
    }

    /** Twice as long, A fixed. Null when it would run past the end of the file. */
    fun double(bounds: LoopBounds): LoopBounds? {
        var end = bounds.start + bounds.length * 2
        if (end > 1f && end < 1f + ROUNDING) end = 1f
        return if (end <= 1f) bounds.copy(end = end) else null
    }

    /**
     * The whole loop moves by one beat, or by [NUDGE_SECONDS] without a grid; [direction] is +1 or
     * -1. Stops at the edges of the file; null when it is already there.
     */
    fun nudge(bounds: LoopBounds, direction: Int, durationSeconds: Float, grid: BeatGrid?): LoopBounds? {
        if (direction == 0 || durationSeconds <= 0f) return null
        val step = grid?.beatFraction ?: (NUDGE_SECONDS / durationSeconds)
        val shift = (step * direction.coerceIn(-1, 1)).coerceIn(-bounds.start, 1f - bounds.end)
        if (abs(shift) < ROUNDING) return null
        return LoopBounds(bounds.start + shift, bounds.end + shift)
    }

    /**
     * The loop the `L` key adds at [playhead]: [NEW_LOOP_BEATS] beats from the snapped playhead, or
     * [NEW_LOOP_SECONDS] without a tempo. [tempo] sets the length even when snap is off. Near the end
     * of the file it is moved back so it keeps its length.
     */
    fun newLoopAt(playhead: Float, durationSeconds: Float, grid: BeatGrid?, tempo: BeatGrid? = grid): LoopBounds {
        val wanted = tempo?.let { it.beatFraction * NEW_LOOP_BEATS }
            ?: if (durationSeconds > 0f) NEW_LOOP_SECONDS / durationSeconds else 1f
        val length = wanted.coerceIn(minLength(durationSeconds), 1f)
        val start = snap(playhead, grid).coerceIn(0f, 1f - length)
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
