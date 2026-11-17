package com.damianogiusti.slowdowner.ui.player.components.timeline

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// Pure maths of the timeline and its waveform, kept apart from drawing so it can be tested without a window.

/** The part of the file a timeline shows: the whole of it, or the zoom around the loop. Fractions 0..1. */
internal data class TimelineWindow(val from: Float, val to: Float) {
    val span: Float get() = (to - from).takeIf { it > 0f } ?: 1f

    companion object {
        val WHOLE = TimelineWindow(0f, 1f)
    }
}

/** Maps the [window] onto 0..[width] pixels. Bars, markers, the playhead and presses all use it. */
internal class TimelineAxis(private val window: TimelineWindow, private val width: Float) {
    fun x(fraction: Float): Float = (fraction - window.from) / window.span * width

    /** The file position under [x], kept inside the window. */
    fun fraction(x: Float): Float {
        if (width <= 0f) return window.from
        return (window.from + x / width * window.span).coerceIn(window.from, window.to).coerceIn(0f, 1f)
    }
}

/**
 * The zoom around a loop: the loop, plus on each side 35% of its length or [MIN_ZOOM_PADDING_SECONDS],
 * whichever is longer, so A and B can be moved a little past where they are.
 */
internal fun zoomWindow(loopStart: Float, loopEnd: Float, durationSeconds: Float): TimelineWindow {
    val minimum = if (durationSeconds > 0f) MIN_ZOOM_PADDING_SECONDS / durationSeconds else 0f
    val padding = max((loopEnd - loopStart) * ZOOM_PADDING_RATIO, minimum)
    return TimelineWindow((loopStart - padding).coerceAtLeast(0f), (loopEnd + padding).coerceAtMost(1f))
}

/**
 * The gain that makes the loudest level fill the height: a quiet recording still reads as a waveform.
 * Capped, so near-silence does not turn into noise. The zoom uses the overview's gain, so the two agree.
 */
internal fun loudnessGain(levels: FloatArray): Float {
    val loudest = levels.maxOrNull() ?: return 1f
    if (loudest <= 0f) return 1f
    return (1f / loudest).coerceAtMost(MAX_GAIN)
}

internal fun FloatArray.scaledBy(gain: Float): FloatArray = FloatArray(size) { (this[it] * gain).coerceIn(0f, 1f) }

/** How many of [count] bars across the [window] the playhead has passed: a bar counts by its centre. */
internal fun playedBars(position: Float, window: TimelineWindow, count: Int): Int =
    floor((position - window.from) / window.span * count + 0.5f).toInt().coerceIn(0, count)

/** Indices of the bars whose centre lies in [start]..[end). Empty when none. */
internal fun barsIn(start: Float, end: Float, window: TimelineWindow, count: Int): IntRange {
    if (end <= start) return IntRange.EMPTY
    val first = ceil((start - window.from) / window.span * count - 0.5f).toInt().coerceIn(0, count)
    val endExclusive = ceil((end - window.from) / window.span * count - 0.5f).toInt().coerceIn(0, count)
    return first until endExclusive
}

internal enum class LoopMarker { A, B }

/**
 * The marker a press at [x] grabs: the nearer one within [reach] pixels. When both are as near (a very
 * short loop, A and B drawn on each other), the side decides, like the labels: A's sits left of its line,
 * B's right of its own.
 */
internal fun hitMarker(x: Float, startX: Float, endX: Float, reach: Float): LoopMarker? {
    val toStart = abs(x - startX)
    val toEnd = abs(x - endX)
    return when {
        toStart > reach && toEnd > reach -> null
        toEnd <= reach && toStart <= reach -> if (x <= (startX + endX) / 2) LoopMarker.A else LoopMarker.B
        toStart <= reach -> LoopMarker.A
        else -> LoopMarker.B
    }
}

private const val ZOOM_PADDING_RATIO = 0.35f
private const val MIN_ZOOM_PADDING_SECONDS = 1f
private const val MAX_GAIN = 8f
