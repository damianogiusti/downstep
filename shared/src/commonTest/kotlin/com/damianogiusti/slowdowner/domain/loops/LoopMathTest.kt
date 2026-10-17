package com.damianogiusti.slowdowner.domain.loops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoopMathTest {

    // 120 bpm over 100 s: a beat is 0.5 s, so 0.005 of the file. Easy numbers below.
    private val duration = 100f
    private val grid = BeatGrid.of(120f, duration)!!

    @Test
    fun `when A is dragged past B then it stops before B`() {
        val loop = LoopBounds(0.2f, 0.3f)

        val moved = LoopMath.moveStart(loop, 0.5f, duration)

        assertEquals(0.3f - LoopMath.minLength(duration), moved.start, TOLERANCE)
        assertEquals(0.3f, moved.end)
    }

    @Test
    fun `when B is dragged before A then it stops after A`() {
        val moved = LoopMath.moveEnd(LoopBounds(0.2f, 0.3f), 0.1f, duration)

        assertEquals(0.2f + LoopMath.minLength(duration), moved.end, TOLERANCE)
    }

    @Test
    fun `when the file is short then the gap is the engine minimum of one second`() {
        // 0.5% of 50 s is 0.25 s: the engine would not play a loop that short.
        assertEquals(1f / 50f, LoopMath.minLength(50f), TOLERANCE)
        assertEquals(LoopMath.MIN_GAP_FRACTION, LoopMath.minLength(600f), TOLERANCE)
    }

    @Test
    fun `when A and B are moved then they land exactly where asked, off any beat`() {
        // 0.2137 and 0.2526 are between beats of the 120 bpm grid: nothing rounds them.
        val movedStart = LoopMath.moveStart(LoopBounds(0.2f, 0.3f), 0.2137f, duration)
        val movedEnd = LoopMath.moveEnd(LoopBounds(0.2f, 0.3f), 0.2526f, duration)

        assertEquals(0.2137f, movedStart.start, TOLERANCE)
        assertEquals(0.2526f, movedEnd.end, TOLERANCE)
    }

    @Test
    fun `when halved then A stays and the length halves`() {
        val halved = LoopMath.halve(LoopBounds(0.2f, 0.24f), duration)!!

        assertEquals(0.2f, halved.start)
        assertEquals(0.22f, halved.end, TOLERANCE)
    }

    @Test
    fun `when the half would be shorter than the minimum then halving does not apply`() {
        assertNull(LoopMath.halve(LoopBounds(0.2f, 0.209f), duration))
    }

    @Test
    fun `when doubled then A stays and the length doubles`() {
        val doubled = LoopMath.double(LoopBounds(0.2f, 0.3f))!!

        assertEquals(0.2f, doubled.start)
        assertEquals(0.4f, doubled.end, TOLERANCE)
    }

    @Test
    fun `when doubling would pass the end then it does not apply`() {
        assertNull(LoopMath.double(LoopBounds(0.6f, 0.9f)))
    }

    @Test
    fun `when halved off a beat then the half is kept as it is`() {
        // 0.2137..0.2437 is 6 beats: the half ends at 0.2287, between beats, and stays there.
        val halved = LoopMath.halve(LoopBounds(0.2137f, 0.2437f), duration)!!

        assertEquals(0.2287f, halved.end, TOLERANCE)
    }

    @Test
    fun `when nudged then the loop moves by one second and keeps its length`() {
        val nudged = LoopMath.nudge(LoopBounds(0.2f, 0.3f), direction = -1, duration)!!

        assertEquals(0.19f, nudged.start, TOLERANCE)
        assertEquals(0.29f, nudged.end, TOLERANCE)
    }

    @Test
    fun `when nudged then the step is one second of the track whatever its length`() {
        // 1 s of a 20 s track is 5% of it; of a 400 s track, 0.25%.
        val short = LoopMath.nudge(LoopBounds(0.2f, 0.3f), direction = 1, 20f)!!
        val long = LoopMath.nudge(LoopBounds(0.2f, 0.3f), direction = 1, 400f)!!

        assertEquals(0.25f, short.start, TOLERANCE)
        assertEquals(0.2025f, long.start, TOLERANCE)
        assertEquals(1f, LoopMath.NUDGE_SECONDS)
    }

    @Test
    fun `when the direction is zero or nothing is open then nudging does not apply`() {
        assertNull(LoopMath.nudge(LoopBounds(0.2f, 0.3f), direction = 0, duration))
        assertNull(LoopMath.nudge(LoopBounds(0.2f, 0.3f), direction = 1, 0f))
    }

    @Test
    fun `when nudged against the edge then it stops there and then no longer applies`() {
        val atStart = LoopMath.nudge(LoopBounds(0.002f, 0.1f), direction = -1, duration)!!

        assertEquals(0f, atStart.start, TOLERANCE)
        assertEquals(0.098f, atStart.end, TOLERANCE)
        assertNull(LoopMath.nudge(atStart, direction = -1, duration))
    }

    @Test
    fun `when a loop is added at the playhead then it starts there and is 8 beats long`() {
        val loop = LoopMath.newLoopAt(0.4012f, duration, grid)

        assertEquals(0.4012f, loop.start, TOLERANCE)
        assertEquals(0.4412f, loop.end, TOLERANCE)
    }

    @Test
    fun `when a loop is added without a tempo then it is 8 seconds from the playhead`() {
        val loop = LoopMath.newLoopAt(0.4012f, duration, tempo = null)

        assertEquals(0.4012f, loop.start, TOLERANCE)
        assertEquals(0.4812f, loop.end, TOLERANCE)
    }

    @Test
    fun `when a loop is added near the end then it moves back to keep its length`() {
        val loop = LoopMath.newLoopAt(0.99f, duration, grid)

        assertEquals(1f, loop.end, TOLERANCE)
        assertEquals(0.04f, loop.length, TOLERANCE)
    }

    @Test
    fun `when the loop is long then the zoom pads it by 35 percent of its length`() {
        val window = LoopMath.zoomWindow(LoopBounds(0.4f, 0.6f), grid)

        assertEquals(0.33f, window.start, TOLERANCE)
        assertEquals(0.67f, window.end, TOLERANCE)
    }

    @Test
    fun `when the loop is short then the zoom pads it by 4 beats and stays in the file`() {
        val window = LoopMath.zoomWindow(LoopBounds(0.01f, 0.02f), grid)

        assertEquals(0f, window.start, TOLERANCE)
        assertEquals(0.04f, window.end, TOLERANCE)
    }

    @Test
    fun `when points come in any order then between orders them and keeps the minimum`() {
        assertEquals(LoopBounds(0.2f, 0.4f), LoopMath.between(0.4f, 0.2f, duration))
        val tiny = LoopMath.between(0.5f, 0.5f, duration)
        assertEquals(0.01f, tiny.length, TOLERANCE)
    }

    @Test
    fun `when the tempo is out of range then there is no grid`() {
        assertNull(BeatGrid.of(5f, duration))
        assertNull(BeatGrid.of(120f, 0f))
        assertNotNull(BeatGrid.of(92f, duration))
    }

    @Test
    fun `when counting beats then a loop of 4 seconds at 120 bpm is 8 beats`() {
        assertEquals(8f, grid.beatsBetween(0.4f, 0.44f), TOLERANCE)
    }

    @Test
    fun `when the user set a tempo then it wins over the detected one`() {
        assertEquals(92f, TrackLoops("a", bpm = 92f, detectedBpm = 120f).effectiveBpm)
        assertEquals(120f, TrackLoops("a", detectedBpm = 120f).effectiveBpm)
        assertNull(TrackLoops("a").effectiveBpm)
        assertNotNull(TrackLoops("a", detectedBpm = 120f).beatGrid(duration))
        assertNull(TrackLoops("a").beatGrid(duration))
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
