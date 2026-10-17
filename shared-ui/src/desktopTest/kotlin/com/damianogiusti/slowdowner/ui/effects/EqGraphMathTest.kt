package com.damianogiusti.slowdowner.ui.effects

import com.damianogiusti.slowdowner.domain.effects.EqBand
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EqGraphMathTest {

    private val width = 1_000f
    private val height = 300f

    @Test
    fun `the frequency axis is logarithmic from 20 Hz to 20 kHz`() {
        assertEquals(0f, EqGraphMath.frequencyToX(20f, width), 1e-3f)
        assertEquals(width, EqGraphMath.frequencyToX(20_000f, width), 1e-3f)
        // Three decades: each takes a third, and the middle is the geometric mean.
        assertEquals(width / 3, EqGraphMath.frequencyToX(200f, width), 1e-2f)
        assertEquals(width * 2 / 3, EqGraphMath.frequencyToX(2_000f, width), 1e-2f)
        assertEquals(sqrt(20f * 20_000f), EqGraphMath.xToFrequency(width / 2, width), 0.01f)
    }

    @Test
    fun `pixels and frequencies convert back and forth`() {
        listOf(20f, 63f, 440f, 1_000f, 7_777f, 20_000f).forEach { hz ->
            assertEquals(hz, EqGraphMath.xToFrequency(EqGraphMath.frequencyToX(hz, width), width), hz * 1e-4f)
        }
    }

    @Test
    fun `a pointer beyond the plot gives the ends of the frequency axis`() {
        assertEquals(20f, EqGraphMath.xToFrequency(-50f, width))
        assertEquals(20_000f, EqGraphMath.xToFrequency(width + 50f, width))
        assertEquals(0f, EqGraphMath.frequencyToX(5f, width))
    }

    @Test
    fun `the gain axis is linear around 0 dB in the middle`() {
        assertEquals(height / 2, EqGraphMath.gainToY(0f, height))
        assertEquals(0f, EqGraphMath.gainToY(EqGraphMath.AXIS_DB, height))
        assertEquals(height, EqGraphMath.gainToY(-EqGraphMath.AXIS_DB, height))
        assertEquals(6f, EqGraphMath.yToGain(EqGraphMath.gainToY(6f, height), height), 1e-4f)
    }

    @Test
    fun `a gain beyond what a band can do is clamped to 12 dB`() {
        assertEquals(EqBand.MAX_GAIN_DB, EqGraphMath.yToGain(0f, height))
        assertEquals(EqBand.MIN_GAIN_DB, EqGraphMath.yToGain(height + 100f, height))
    }

    @Test
    fun `each wheel notch changes Q by a quarter of a doubling, clamped`() {
        assertEquals(1.1892f, EqGraphMath.qFromScroll(1f, 1f), 1e-4f)
        assertEquals(2f, EqGraphMath.qFromScroll(1f, 4f), 1e-4f)
        assertEquals(0.5f, EqGraphMath.qFromScroll(1f, -4f), 1e-4f)
        assertEquals(EqBand.MAX_Q, EqGraphMath.qFromScroll(9f, 10f))
        assertEquals(EqBand.MIN_Q, EqGraphMath.qFromScroll(0.4f, -10f))
    }

    @Test
    fun `a Q drag doubles the width per step, down narrower, clamped`() {
        val step = EqGraphMath.Q_DRAG_PIXELS_PER_DOUBLING
        assertEquals(2f, EqGraphMath.qFromDrag(1f, step), 1e-4f)
        assertEquals(0.5f, EqGraphMath.qFromDrag(1f, -step), 1e-4f)
        assertEquals(1f, EqGraphMath.qFromDrag(1f, 0f))
        assertEquals(4f, EqGraphMath.qFromDrag(1f, 2 * 30f, pixelsPerDoubling = 30f), 1e-4f)
        assertEquals(EqBand.MAX_Q, EqGraphMath.qFromDrag(8f, 1_000f))
        assertEquals(EqBand.MIN_Q, EqGraphMath.qFromDrag(0.5f, -1_000f))
    }

    @Test
    fun `a dragged node follows the pointer and keeps its Q`() {
        val start = EqBand(200f, 0f, 2f)

        // A third of the width is a decade; a tenth of the height is 3 dB.
        val moved = EqGraphMath.moveBand(start, dx = width / 3, dy = -height / 10, width, height)

        assertEquals(2_000f, moved.frequencyHz, 0.5f)
        assertEquals(3f, moved.gainDb, 1e-3f)
        assertEquals(2f, moved.q)
    }

    @Test
    fun `a node dragged past the edges stops at the limits`() {
        val moved = EqGraphMath.moveBand(EqBand(10_000f, 10f, 1f), dx = 5_000f, dy = -5_000f, width, height)
        val other = EqGraphMath.moveBand(EqBand(30f, -10f, 1f), dx = -5_000f, dy = 5_000f, width, height)

        assertEquals(EqBand(20_000f, 12f, 1f), moved)
        assertEquals(EqBand(20f, -12f, 1f), other)
    }

    @Test
    fun `a press takes the nearest node within reach`() {
        val bands = listOf(EqBand(100f, 0f), EqBand(1_000f, 6f), EqBand(1_100f, 6f))
        val x = EqGraphMath.frequencyToX(1_000f, width)
        val y = EqGraphMath.gainToY(6f, height)

        assertEquals(1, EqGraphMath.hitBand(bands, x - 3f, y, width, height, radius = 16f))
        assertEquals(2, EqGraphMath.hitBand(bands, EqGraphMath.frequencyToX(1_100f, width) + 2f, y, width, height, 16f))
        assertNull(EqGraphMath.hitBand(bands, x, y + 40f, width, height, 16f))
        assertEquals(0, EqGraphMath.hitBand(bands, EqGraphMath.frequencyToX(100f, width), height / 2 + 10f, width, height, 16f))
    }

    @Test
    fun `the curve is sampled evenly on the log axis, ends included`() {
        val samples = EqGraphMath.sampleFrequencies(4)

        assertEquals(4, samples.size)
        assertEquals(20f, samples.first(), 1e-3f)
        assertEquals(200f, samples[1], 1e-2f)
        assertEquals(20_000f, samples.last(), 0.5f)
    }
}
