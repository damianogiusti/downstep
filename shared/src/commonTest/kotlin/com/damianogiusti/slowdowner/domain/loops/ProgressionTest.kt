package com.damianogiusti.slowdowner.domain.loops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressionTest {

    // The design's defaults: from 70% to 100%, +3% every 2 passes.
    private val ramp = Progression(enabled = true)

    @Test
    fun `when passes add up then the speed rises by a step every few passes`() {
        assertEquals(70f, ramp.speedAfter(0))
        assertEquals(70f, ramp.speedAfter(1))
        assertEquals(73f, ramp.speedAfter(2))
        assertEquals(76f, ramp.speedAfter(5))
    }

    @Test
    fun `when the ramp reaches the target then it stays there`() {
        assertEquals(100f, ramp.speedAfter(20))
        assertEquals(100f, ramp.speedAfter(500))
        assertTrue(ramp.isAtTarget(20))
    }

    @Test
    fun `when ramping then the status tells the next step and when it comes`() {
        assertEquals(ProgressionStatus.Ramping(76f, 3f, 1), ramp.status(5))
        assertEquals(ProgressionStatus.Ramping(76f, 3f, 2), ramp.status(4))
    }

    @Test
    fun `when the last step is smaller than the step then the status shows what is left`() {
        // 70 + 3 × 9 = 97: only 3 more to 100. With from 71: 98, only 2 left.
        val status = ramp.copy(fromPercent = 71f).status(18)

        assertEquals(ProgressionStatus.Ramping(98f, 2f, 2), status)
    }

    @Test
    fun `when at target or off then the status says so`() {
        assertEquals(ProgressionStatus.AtTarget(100f), ramp.status(30))
        assertEquals(ProgressionStatus.Off, ramp.copy(enabled = false).status(3))
    }

    @Test
    fun `when the target is below the start then the ramp goes down`() {
        val down = Progression(enabled = true, fromPercent = 100f, toPercent = 90f, stepPercent = 5f, everyPasses = 1)

        assertEquals(95f, down.speedAfter(1))
        assertEquals(90f, down.speedAfter(9))
    }

    @Test
    fun `when progressing then the fraction follows from to target`() {
        assertEquals(0f, ramp.progressFraction(0))
        assertEquals(0.2f, ramp.progressFraction(4), 1e-4f)
        assertEquals(1f, ramp.progressFraction(40))
    }

    @Test
    fun `when values are out of range then coerced brings them back`() {
        val wild = Progression(fromPercent = 5f, toPercent = 900f, stepPercent = 0f, everyPasses = 0).coerced()

        assertEquals(Progression(fromPercent = 25f, toPercent = 200f, stepPercent = 1f, everyPasses = 1), wild)
    }
}
