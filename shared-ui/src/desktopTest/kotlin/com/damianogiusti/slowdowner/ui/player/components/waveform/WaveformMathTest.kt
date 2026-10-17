package com.damianogiusti.slowdowner.ui.player.components.waveform

import com.damianogiusti.slowdowner.domain.loops.BeatGrid
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.ui.player.Peaks
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaveformMathTest {

    @Test
    fun `the axis maps fractions to pixels and back`() {
        val axis = WaveformAxis(left = 24f, width = 1000f, from = 0f, to = 1f)

        assertEquals(24f, axis.x(0f))
        assertEquals(524f, axis.x(0.5f))
        assertEquals(0.25f, axis.fraction(274f))
    }

    @Test
    fun `clicks outside the bars land on the ends of the axis`() {
        val axis = WaveformAxis(left = 24f, width = 1000f, from = 0f, to = 1f)

        assertEquals(0f, axis.fraction(3f))
        assertEquals(1f, axis.fraction(1040f))
    }

    @Test
    fun `a zoomed axis maps its window`() {
        val axis = WaveformAxis(left = 12f, width = 400f, from = 0.2f, to = 0.4f)

        assertEquals(12f, axis.x(0.2f))
        assertEquals(212f, axis.x(0.3f), 0.01f)
        assertEquals(0.3f, axis.fraction(212f), 1e-5f)
        assertEquals(0.4f, axis.fraction(900f))
    }

    @Test
    fun `resampling keeps the loudest peak under each bar`() {
        val peaks = Peaks(0f, 1f, floatArrayOf(0.1f, 0.9f, 0.2f, 0.3f, 0.5f, 0.4f, 0f, 0.1f))

        assertContentEquals(floatArrayOf(0.9f, 0.3f, 0.5f, 0.1f), resamplePeaks(peaks, 0f, 1f, 4))
    }

    @Test
    fun `resampling to more bars than peaks repeats them`() {
        val peaks = Peaks(0f, 1f, floatArrayOf(0.2f, 0.8f))

        assertContentEquals(floatArrayOf(0.2f, 0.2f, 0.8f, 0.8f), resamplePeaks(peaks, 0f, 1f, 4))
    }

    @Test
    fun `peaks are mapped by their own range, and what they do not cover is flat`() {
        // Peaks of the previous zoom window: 0.25..0.5, drawn over 0..0.5 for a frame.
        val peaks = Peaks(0.25f, 0.5f, floatArrayOf(0.4f, 0.6f))

        assertContentEquals(floatArrayOf(0f, 0f, 0.4f, 0.6f), resamplePeaks(peaks, 0f, 0.5f, 4))
    }

    @Test
    fun `no peaks draw flat`() {
        assertContentEquals(FloatArray(3), resamplePeaks(null, 0f, 1f, 3))
        assertContentEquals(FloatArray(3), resamplePeaks(Peaks(0f, 1f, FloatArray(0)), 0f, 1f, 3))
    }

    @Test
    fun `levels are scaled so the loudest fills the height, with a capped gain`() {
        assertContentEquals(floatArrayOf(0.5f, 1f), normalizeLevels(floatArrayOf(0.25f, 0.5f)))
        assertContentEquals(floatArrayOf(0.25f), normalizeLevels(floatArrayOf(0.03125f)))
        assertContentEquals(floatArrayOf(0f, 0f), normalizeLevels(floatArrayOf(0f, 0f)))
    }

    @Test
    fun `a bar is played once the playhead passes its centre`() {
        assertEquals(0, playedBars(0f, 0f, 1f, 10))
        assertEquals(0, playedBars(0.04f, 0f, 1f, 10))
        assertEquals(1, playedBars(0.05f, 0f, 1f, 10))
        assertEquals(10, playedBars(1f, 0f, 1f, 10))
        assertEquals(0, playedBars(0.1f, 0.2f, 0.4f, 10))
        assertEquals(10, playedBars(0.5f, 0.2f, 0.4f, 10))
    }

    @Test
    fun `the loop takes the bars whose centre it covers`() {
        assertEquals(2 until 5, barsIn(0.2f, 0.5f, 0f, 1f, 10))
        assertEquals(3 until 5, barsIn(0.26f, 0.5f, 0f, 1f, 10))
        assertTrue(barsIn(0.21f, 0.24f, 0f, 1f, 10).isEmpty())
        assertEquals(0 until 10, barsIn(0f, 1f, 0.2f, 0.4f, 10))
    }

    @Test
    fun `a press grabs the nearer handle within reach`() {
        assertEquals(LoopHandle.Start, hitHandle(105f, startX = 100f, endX = 300f, reach = 8f))
        assertEquals(LoopHandle.End, hitHandle(293f, startX = 100f, endX = 300f, reach = 8f))
        assertNull(hitHandle(200f, startX = 100f, endX = 300f, reach = 8f))
        assertEquals(LoopHandle.End, hitHandle(104f, startX = 100f, endX = 106f, reach = 8f))
    }

    @Test
    fun `A and B on top of each other split by side`() {
        assertEquals(LoopHandle.Start, hitHandle(98f, startX = 100f, endX = 100f, reach = 8f))
        assertEquals(LoopHandle.End, hitHandle(102f, startX = 100f, endX = 100f, reach = 8f))
    }

    @Test
    fun `dragging A past B stops before it`() {
        val bounds = LoopBounds(0.2f, 0.4f)

        val dragged = dragLoop(LoopHandle.Start, bounds, 0.9f, durationSeconds = 100f, grid = null)

        assertEquals(0.4f, dragged.end)
        assertTrue(dragged.start < dragged.end)
    }

    @Test
    fun `dragging B before A stops after it`() {
        val dragged = dragLoop(LoopHandle.End, LoopBounds(0.2f, 0.4f), 0.05f, durationSeconds = 100f, grid = null)

        assertEquals(0.2f, dragged.start)
        assertTrue(dragged.end > dragged.start)
    }

    @Test
    fun `drags snap to the beat grid`() {
        // 60 BPM over 100 s: a beat every 1% of the file.
        val grid = BeatGrid.of(60f, 100f)

        val dragged = dragLoop(LoopHandle.End, LoopBounds(0.2f, 0.4f), 0.4537f, durationSeconds = 100f, grid = grid)

        assertEquals(0.45f, dragged.end, 1e-5f)
    }

    @Test
    fun `the drag preview stays until the state catches up`() {
        val drag = LoopDragState()
        val committed = LoopBounds(0.2f, 0.4f)

        drag.begin(committed)
        drag.move(LoopBounds(0.25f, 0.4f))
        drag.settle()
        assertEquals(LoopBounds(0.25f, 0.4f), drag.shown(committed))

        drag.end(moved = true)
        assertEquals(LoopBounds(0.25f, 0.4f), drag.shown(committed))

        drag.settle()
        assertEquals(committed, drag.shown(committed))
    }

    @Test
    fun `a drag that moved nothing leaves no preview`() {
        val drag = LoopDragState()
        val committed = LoopBounds(0.2f, 0.4f)

        drag.begin(committed)
        drag.end(moved = false)

        assertNull(drag.bounds)
    }
}
