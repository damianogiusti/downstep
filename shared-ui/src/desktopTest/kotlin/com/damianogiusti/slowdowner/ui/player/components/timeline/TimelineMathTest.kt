package com.damianogiusti.slowdowner.ui.player.components.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineMathTest {

    @Test
    fun `a zoomed axis maps its window to the full width and back`() {
        val axis = TimelineAxis(TimelineWindow(0.4f, 0.6f), width = 200f)

        assertEquals(0f, axis.x(0.4f), 0.001f)
        assertEquals(100f, axis.x(0.5f), 0.001f)
        assertEquals(0.55f, axis.fraction(150f), 0.001f)
    }

    @Test
    fun `a press outside the window lands on its edges`() {
        val axis = TimelineAxis(TimelineWindow(0.4f, 0.6f), width = 200f)

        assertEquals(0.4f, axis.fraction(-30f))
        assertEquals(0.6f, axis.fraction(260f))
    }

    @Test
    fun `the zoom pads the loop by a third of its length`() {
        val window = zoomWindow(loopStart = 0.4f, loopEnd = 0.6f, durationSeconds = 100f)

        assertEquals(0.33f, window.from, 0.001f)
        assertEquals(0.67f, window.to, 0.001f)
    }

    @Test
    fun `a short loop is padded by at least a second`() {
        val window = zoomWindow(loopStart = 0.5f, loopEnd = 0.501f, durationSeconds = 200f)

        assertEquals(0.495f, window.from, 0.0001f)
        assertEquals(0.506f, window.to, 0.0001f)
    }

    @Test
    fun `the zoom stays inside the file`() {
        val window = zoomWindow(loopStart = 0f, loopEnd = 1f, durationSeconds = 100f)

        assertEquals(TimelineWindow.WHOLE, window)
    }

    @Test
    fun `the gain fills the height with the loudest bar, capped for near silence`() {
        assertEquals(2f, loudnessGain(floatArrayOf(0.1f, 0.5f)))
        assertEquals(8f, loudnessGain(floatArrayOf(0.01f)))
        assertEquals(1f, loudnessGain(floatArrayOf(0f, 0f)))
        assertTrue(floatArrayOf(0.2f, 1f).contentEquals(floatArrayOf(0.1f, 0.5f).scaledBy(2f)))
    }

    @Test
    fun `a bar is played once the playhead passes its centre`() {
        assertEquals(0, playedBars(0.04f, TimelineWindow.WHOLE, 10))
        assertEquals(1, playedBars(0.05f, TimelineWindow.WHOLE, 10))
        assertEquals(10, playedBars(1f, TimelineWindow.WHOLE, 10))
    }

    @Test
    fun `the loop takes the bars whose centre it covers`() {
        assertEquals(2 until 5, barsIn(0.2f, 0.5f, TimelineWindow.WHOLE, 10))
        assertTrue(barsIn(0.5f, 0.5f, TimelineWindow.WHOLE, 10).isEmpty())
    }

    @Test
    fun `a press grabs the nearer marker within reach`() {
        assertEquals(LoopMarker.A, hitMarker(x = 105f, startX = 100f, endX = 200f, reach = 24f))
        assertEquals(LoopMarker.B, hitMarker(x = 190f, startX = 100f, endX = 200f, reach = 24f))
        assertNull(hitMarker(x = 150f, startX = 100f, endX = 200f, reach = 24f))
    }

    @Test
    fun `A and B a few pixels apart split by side, like their labels`() {
        assertEquals(LoopMarker.A, hitMarker(x = 90f, startX = 100f, endX = 103f, reach = 24f))
        assertEquals(LoopMarker.B, hitMarker(x = 112f, startX = 100f, endX = 103f, reach = 24f))
    }
}
