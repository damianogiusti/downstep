package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class PracticeSettingsTest {

    private val preset = PracticeSettings(75f, -2f, LoopRegion(0.2f, 0.4f, isOn = true))

    @Test
    fun `when values differ only by engine rounding then settings match`() {
        assertTrue(preset.matches(PracticeSettings(74.9998f, -2f, LoopRegion(0.2002f, 0.4f, isOn = true))))
    }

    @Test
    fun `when speed differs by a step then settings do not match`() {
        assertFalse(preset.matches(preset.copy(speedPercent = 80f)))
    }

    @Test
    fun `when the loop is off then its parked points are ignored`() {
        val off = PracticeSettings(loop = LoopRegion(0.1f, 0.3f, isOn = false))

        assertTrue(off.matches(PracticeSettings(loop = LoopRegion.WHOLE_FILE)))
    }

    @Test
    fun `when only one side loops then settings do not match`() {
        assertFalse(preset.matches(preset.copy(loop = preset.loop.copy(isOn = false))))
    }
}
