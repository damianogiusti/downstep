package com.damianogiusti.slowdowner.ui.effects

import com.damianogiusti.slowdowner.domain.effects.EqBand
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * The EQ graph's geometry, in pixels of a [width] × [height] plot: frequency on a log axis from 20 Hz
 * to 20 kHz left to right, gain on a linear axis of ±[AXIS_DB] around 0 dB in the middle (the bands
 * stop at ±12 dB; the margin keeps a node at the limit off the edge). Pure: drawn and tested alike.
 */
object EqGraphMath {
    const val MIN_HZ = EqBand.MIN_FREQUENCY_HZ
    const val MAX_HZ = EqBand.MAX_FREQUENCY_HZ
    const val AXIS_DB = 15f

    /** Q is multiplied by this per wheel notch: four notches double or halve it. */
    val Q_STEP_PER_NOTCH = 2f.pow(0.25f)

    /** Vertical pixels of a Q drag (Shift or Alt) that double or halve Q, at density 1. */
    const val Q_DRAG_PIXELS_PER_DOUBLING = 60f

    /** How near a node a press must land to take it, at density 1. */
    const val NODE_HIT_RADIUS = 16f

    /**
     * How far a press may wander, at density 1, and still be a click (half of a double click that
     * resets the gain). Further, it is a drag: pressing the node again right after is another nudge.
     */
    const val CLICK_WANDER = 3f

    /** Vertical grid lines, and the frequencies labelled under them. */
    val GRID_HZ = listOf(20f, 50f, 100f, 200f, 500f, 1_000f, 2_000f, 5_000f, 10_000f, 20_000f)
    val GRID_DB = listOf(-12f, -6f, 0f, 6f, 12f)

    private val DECADES = log10(MAX_HZ / MIN_HZ)

    fun frequencyToX(frequencyHz: Float, width: Float): Float =
        width * log10(frequencyHz.coerceIn(MIN_HZ, MAX_HZ) / MIN_HZ) / DECADES

    fun xToFrequency(x: Float, width: Float): Float {
        if (width <= 0f) return MIN_HZ
        return (MIN_HZ * 10f.pow(DECADES * (x / width).coerceIn(0f, 1f))).coerceIn(MIN_HZ, MAX_HZ)
    }

    fun gainToY(gainDb: Float, height: Float): Float = height / 2 - gainDb / AXIS_DB * height / 2

    /** Clamped to what a band can do (±12 dB), wherever the pointer is. */
    fun yToGain(y: Float, height: Float): Float {
        if (height <= 0f) return 0f
        return ((height / 2 - y) / (height / 2) * AXIS_DB).coerceIn(EqBand.MIN_GAIN_DB, EqBand.MAX_GAIN_DB)
    }

    /**
     * Q after a wheel turn of [scrollDelta] notches. Scrolling up (negative, as Compose reports it)
     * widens the bell, down narrows it. Clamped.
     */
    fun qFromScroll(q: Float, scrollDelta: Float): Float =
        (q * Q_STEP_PER_NOTCH.pow(scrollDelta)).coerceIn(EqBand.MIN_Q, EqBand.MAX_Q)

    /**
     * Q after a modifier drag of [dy] pixels from where it started at [startQ]: like the wheel,
     * dragging up widens, down narrows; [pixelsPerDoubling] scales with the density.
     */
    fun qFromDrag(startQ: Float, dy: Float, pixelsPerDoubling: Float = Q_DRAG_PIXELS_PER_DOUBLING): Float =
        (startQ * 2f.pow(dy / pixelsPerDoubling)).coerceIn(EqBand.MIN_Q, EqBand.MAX_Q)

    /**
     * A band dragged by ([dx], [dy]) pixels from where it was when the press began: the node follows
     * the pointer, frequency and gain clamped to their ranges. Q stays.
     */
    fun moveBand(start: EqBand, dx: Float, dy: Float, width: Float, height: Float): EqBand = start.copy(
        frequencyHz = xToFrequency(frequencyToX(start.frequencyHz, width) + dx, width),
        gainDb = yToGain(gainToY(start.gainDb, height) + dy, height),
    )

    /** The node nearest ([x], [y]) within [radius] pixels, or null. Ties go to the later band, drawn on top. */
    fun hitBand(bands: List<EqBand>, x: Float, y: Float, width: Float, height: Float, radius: Float): Int? {
        var best: Int? = null
        var bestDistance = radius
        bands.forEachIndexed { index, band ->
            val distance = hypot(frequencyToX(band.frequencyHz, width) - x, gainToY(band.gainDb, height) - y)
            if (distance <= bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    /** [count] frequencies spread evenly on the log axis, both ends included: where the curves are sampled. */
    fun sampleFrequencies(count: Int): List<Float> =
        List(count) { MIN_HZ * 10f.pow(DECADES * it / (count - 1).coerceAtLeast(1)) }
}
