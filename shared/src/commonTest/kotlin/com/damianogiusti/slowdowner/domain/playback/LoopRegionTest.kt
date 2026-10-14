package com.damianogiusti.slowdowner.domain.playback

import kotlin.test.assertEquals
import kotlin.test.Test

class LoopRegionTest {

    @Test
    fun `when points are given in reverse order then start is the smaller one`() {
        assertEquals(LoopRegion(0.2f, 0.6f, isOn = true), LoopRegion.between(0.6f, 0.2f, durationSeconds = 100f))
    }

    @Test
    fun `when the loop is shorter than one second then end moves to one second after start`() {
        val region = LoopRegion.between(0.5f, 0.501f, durationSeconds = 100f)

        assertEquals(0.5f, region.start)
        assertEquals(0.51f, region.end, 0.0001f)
    }

    @Test
    fun `when points are outside the file then they are clamped`() {
        assertEquals(LoopRegion(0f, 1f, isOn = true), LoopRegion.between(-0.3f, 1.4f, durationSeconds = 100f))
    }

    @Test
    fun `when a short loop sits at the very end then it still fits in the file`() {
        val region = LoopRegion.between(1f, 1f, durationSeconds = 100f)

        assertEquals(0.99f, region.start, 0.0001f)
        assertEquals(1f, region.end, 0.0001f)
    }
}
