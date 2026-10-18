package com.damianogiusti.slowdowner.ui.player.components.waveform

import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.LoopMath
import com.damianogiusti.slowdowner.ui.player.Peaks
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// Pure maths of the two waveforms, kept apart from drawing so it can be tested without a window.

/**
 * Maps [from]..[to] (fractions of the file) onto [left]..[left] + [width] pixels. The overview shows
 * 0..1, the zoom strip its window. Regions, bars, the playhead and clicks all use the same axis, so a
 * click on a bar seeks to that bar.
 */
internal class WaveformAxis(val left: Float, val width: Float, val from: Float, val to: Float) {
    private val span: Float get() = (to - from).takeIf { it > 0f } ?: 1f

    fun x(fraction: Float): Float = left + (fraction - from) / span * width

    /** The file position under [x], within the axis. */
    fun fraction(x: Float): Float {
        if (width <= 0f) return from
        return (from + (x - left) / width * span).coerceIn(from, to).coerceIn(0f, 1f)
    }

    fun contains(fraction: Float): Boolean = fraction in from..to
}

/**
 * [count] bar levels for [from]..[to], each the loudest peak of [peaks] under it. Peaks are mapped by
 * their own range: the zoom strip may draw the previous window's peaks for a frame, and parts of the
 * view they do not cover stay at 0.
 */
internal fun resamplePeaks(peaks: Peaks?, from: Float, to: Float, count: Int): FloatArray {
    val levels = FloatArray(count.coerceAtLeast(0))
    val values = peaks?.values ?: return levels
    val peaksSpan = peaks.to - peaks.from
    if (values.isEmpty() || peaksSpan <= 0f || count <= 0 || to <= from) return levels
    val n = values.size
    val barSpan = (to - from) / count
    for (i in 0 until count) {
        val barStart = from + i * barSpan
        val barEnd = barStart + barSpan
        if (barEnd <= peaks.from || barStart >= peaks.to) continue
        val first = floor((barStart - peaks.from) / peaksSpan * n).toInt().coerceIn(0, n - 1)
        val last = (ceil((barEnd - peaks.from) / peaksSpan * n).toInt() - 1).coerceIn(first, n - 1)
        var level = 0f
        for (k in first..last) level = max(level, values[k])
        levels[i] = level.coerceIn(0f, 1f)
    }
    return levels
}

/**
 * Levels scaled so the loudest bar fills the height: quiet recordings still read as a waveform. The
 * gain is capped, so near-silence does not turn into noise.
 */
internal fun normalizeLevels(levels: FloatArray, maxGain: Float = MAX_GAIN): FloatArray {
    val loudest = levels.maxOrNull() ?: return levels
    if (loudest <= 0f) return levels
    val gain = (1f / loudest).coerceAtMost(maxGain)
    return FloatArray(levels.size) { (levels[it] * gain).coerceIn(0f, 1f) }
}

private const val MAX_GAIN = 8f

/**
 * Bars of [count] equal slices of the axis range: how many are played ([playedBars]) and which lie
 * in a loop ([barsIn]). A bar counts by its centre, as in the design.
 */
internal fun playedBars(position: Float, from: Float, to: Float, count: Int): Int {
    if (to <= from) return 0
    return floor((position - from) / (to - from) * count + 0.5f).toInt().coerceIn(0, count)
}

/** Indices of the bars whose centre lies in [start]..[end) (end exclusive). Empty when none. */
internal fun barsIn(start: Float, end: Float, from: Float, to: Float, count: Int): IntRange {
    if (to <= from || end <= start) return IntRange.EMPTY
    val first = ceil((start - from) / (to - from) * count - 0.5f).toInt().coerceIn(0, count)
    val endExclusive = ceil((end - from) / (to - from) * count - 0.5f).toInt().coerceIn(0, count)
    return first until endExclusive
}

/** What a press on a loop holds: its A edge, its B edge, or its top bar (the whole loop). */
internal enum class LoopHandle { Start, End, Bar }

/** The A or B handle within [reach] pixels of [x]; the nearer one when both are (a very short loop). */
internal fun hitHandle(x: Float, startX: Float, endX: Float, reach: Float): LoopHandle? {
    val toStart = abs(x - startX)
    val toEnd = abs(x - endX)
    return when {
        toStart > reach && toEnd > reach -> null
        toStart < toEnd -> LoopHandle.Start
        toEnd < toStart -> LoopHandle.End
        // Same distance: A and B drawn on top of each other. Left of them grabs A, right grabs B.
        x <= startX -> LoopHandle.Start
        else -> LoopHandle.End
    }
}

/**
 * The loop while [handle] is dragged to [position]: free (no beat grid), and A never crosses B. The top
 * bar moves the whole loop by the pointer's travel from [grabbedAt], keeping its length within the file.
 */
internal fun dragLoop(
    handle: LoopHandle,
    bounds: LoopBounds,
    position: Float,
    durationSeconds: Float,
    grabbedAt: Float = position,
): LoopBounds =
    when (handle) {
        LoopHandle.Start -> LoopMath.moveStart(bounds, position, durationSeconds)
        LoopHandle.End -> LoopMath.moveEnd(bounds, position, durationSeconds)
        LoopHandle.Bar -> LoopMath.move(bounds, position - grabbedAt)
    }
