package com.damianogiusti.slowdowner.ui.player.components.waveform

import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hit-testing the loops on the overview, and telling a click from a drag. Pure maths, no window. */
class LoopPressTest {

    // x = 24 + fraction × 1000, as in WaveformGesturesTest.
    private val axis = WaveformAxis(left = 24f, width = 1000f, from = 0f, to = 1f)
    private val active = HitRegion(7, LoopBounds(0.2f, 0.4f), isActive = true)
    private val other = HitRegion(8, LoopBounds(0.6f, 0.8f), isActive = false)
    private val regions = listOf(active, other)

    @Test
    fun `when pressed on the top bar then the whole loop is grabbed, active or not`() {
        assertEquals(LoopHit(7, LoopHandle.Bar), hit(324f, 4f))
        assertEquals(LoopHit(8, LoopHandle.Bar), hit(724f, 4f))
    }

    @Test
    fun `when pressed near an edge then that edge is grabbed at any height, active or not`() {
        assertEquals(LoopHit(7, LoopHandle.Start), hit(226f, 100f))
        assertEquals(LoopHit(7, LoopHandle.End), hit(421f, 4f))
        assertEquals(LoopHit(8, LoopHandle.Start), hit(627f, 150f))
        assertEquals(LoopHit(8, LoopHandle.End), hit(820f, 4f))
    }

    @Test
    fun `when pressed in a loop below its top bar or outside every loop then nothing is grabbed`() {
        assertNull(hit(324f, 100f))
        assertNull(hit(724f, 20f))
        assertNull(hit(524f, 4f))
    }

    @Test
    fun `when loops overlap then the active one wins, then the shorter one`() {
        val wide = HitRegion(9, LoopBounds(0.1f, 0.9f), isActive = false)
        val inside = HitRegion(10, LoopBounds(0.5f, 0.55f), isActive = false)

        assertEquals(LoopHit(7, LoopHandle.Bar), hitLoop(324f, 4f, listOf(wide, active), axis, 8f, 16f))
        assertEquals(LoopHit(10, LoopHandle.Bar), hitLoop(549f, 4f, listOf(wide, inside), axis, 8f, 16f))
        assertEquals(LoopHit(9, LoopHandle.Bar), hitLoop(824f, 4f, listOf(wide, inside), axis, 8f, 16f))
    }

    @Test
    fun `when a loop is too narrow for its bar and both edges then they share it by thirds`() {
        // 15 px wide, under three reaches of 8 px.
        val narrow = listOf(HitRegion(3, LoopBounds(0.5f, 0.515f), isActive = true))

        assertEquals(LoopHit(3, LoopHandle.Bar), hitLoop(531.5f, 4f, narrow, axis, 8f, 16f))
        assertEquals(LoopHit(3, LoopHandle.Start), hitLoop(525f, 4f, narrow, axis, 8f, 16f))
        assertEquals(LoopHit(3, LoopHandle.End), hitLoop(538f, 4f, narrow, axis, 8f, 16f))
        assertEquals(LoopHit(3, LoopHandle.Start), hitLoop(531f, 100f, narrow, axis, 8f, 16f), "below the bar it is all edges")
    }

    @Test
    fun `when the pointer moves no more than the threshold then it is a click`() {
        assertFalse(exceedsDragThreshold(100f, 104f, 4f))
        assertFalse(exceedsDragThreshold(100f, 96f, 4f))
        assertTrue(exceedsDragThreshold(100f, 104.5f, 4f))
        assertTrue(exceedsDragThreshold(100f, 95f, 4f))
    }

    @Test
    fun `when the top bar is clicked without dragging then it is a bar click where it was released`() {
        val press = press(LoopHit(8, LoopHandle.Bar), other, downX = 724f)

        press.moveTo(726f)
        press.moveTo(727f)

        assertFalse(press.isDrag)
        assertEquals(PressResult.BarClick(8, 0.703f), press.release(727f).rounded())
    }

    @Test
    fun `when the top bar is dragged past the threshold then the loop moves keeping its length`() {
        val press = press(LoopHit(8, LoopHandle.Bar), other, downX = 724f)

        assertFalse(press.moveTo(727f))
        assertTrue(press.moveTo(729f), "the move that starts the drag")
        assertFalse(press.moveTo(774f))

        val result = assertIs<PressResult.Commit>(press.release(774f))
        assertEquals(8, result.loopId)
        assertEquals(0.65f, result.bounds.start, 1e-5f)
        assertEquals(0.85f, result.bounds.end, 1e-5f)
    }

    @Test
    fun `when the top bar is dragged past the end of the file then the loop stops there with its length`() {
        val press = press(LoopHit(8, LoopHandle.Bar), other, downX = 724f)

        press.moveTo(1040f)

        val result = assertIs<PressResult.Commit>(press.release(1040f))
        assertEquals(1f, result.bounds.end)
        assertEquals(0.2f, result.bounds.length, 1e-5f)
    }

    @Test
    fun `when a drag goes back to where it started then nothing is committed`() {
        val press = press(LoopHit(7, LoopHandle.Bar), active, downX = 324f)

        press.moveTo(400f)
        press.moveTo(324f)

        assertTrue(press.isDrag, "a drag never turns back into a click")
        assertEquals(PressResult.None, press.release(324f))
    }

    @Test
    fun `when an edge is dragged then the loop is resized and A never crosses B`() {
        val resize = press(LoopHit(8, LoopHandle.End), other, downX = 824f)
        resize.moveTo(924f)
        assertEquals(PressResult.Commit(8, LoopBounds(0.6f, 0.9f)), resize.release(924f).rounded())

        val crossing = press(LoopHit(8, LoopHandle.Start), other, downX = 624f)
        crossing.moveTo(1000f)
        val bounds = assertIs<PressResult.Commit>(crossing.release(1000f)).bounds
        assertEquals(0.8f, bounds.end)
        assertTrue(bounds.start < bounds.end)
    }

    @Test
    fun `when a drag picks up a loop that is not active then it is grabbed, the active one is not`() {
        val inactive = press(LoopHit(8, LoopHandle.Start), other, downX = 624f)
        assertNull(inactive.grabbedInactiveLoop, "a press is not a grab yet")
        inactive.moveTo(640f)
        assertEquals(8L, inactive.grabbedInactiveLoop)

        val own = press(LoopHit(7, LoopHandle.Bar), active, downX = 324f)
        own.moveTo(360f)
        assertNull(own.grabbedInactiveLoop)
    }

    @Test
    fun `when an edge of the active loop is clicked then the seek stays inside the loop`() {
        val press = press(LoopHit(7, LoopHandle.End), active, downX = 430f)

        assertEquals(PressResult.Seek(0.4f), press.release(430f))
    }

    @Test
    fun `when an edge of another loop is clicked then it is a plain click`() {
        val press = press(LoopHit(8, LoopHandle.Start), other, downX = 620f)

        assertEquals(PressResult.Seek(0.596f), press.release(620f).rounded())
    }

    @Test
    fun `when the bare waveform is clicked or dragged then it seeks where released`() {
        val click = LoopPress(null, null, 524f, 4f, axis, 100f)
        assertEquals(PressResult.Seek(0.5f), click.release(524f))

        val drag = LoopPress(null, null, 524f, 4f, axis, 100f)
        drag.moveTo(574f)
        assertNull(drag.grabbedInactiveLoop)
        assertEquals(PressResult.Seek(0.55f), drag.release(574f).rounded())
    }

    @Test
    fun `when released beyond the threshold without a move event then it is still a drag`() {
        val press = press(LoopHit(8, LoopHandle.Bar), other, downX = 724f)

        assertIs<PressResult.Commit>(press.release(824f))
    }

    private fun hit(x: Float, y: Float) = hitLoop(x, y, regions, axis, edgeReach = 8f, barHeight = 16f)

    private fun press(hit: LoopHit, region: HitRegion, downX: Float) = LoopPress(hit, region, downX, threshold = 4f, axis, durationSeconds = 100f)

    /** Fractions from pixels carry float noise: compare at 1e-4. */
    private fun PressResult.rounded(): PressResult = when (this) {
        is PressResult.Seek -> copy(position = position.round())
        is PressResult.BarClick -> copy(position = position.round())
        is PressResult.Commit -> copy(bounds = LoopBounds(bounds.start.round(), bounds.end.round()))
        PressResult.None -> this
    }

    private fun Float.round(): Float = kotlin.math.round(this * 10_000f) / 10_000f
}
