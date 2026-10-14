package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.fake.FakeEngine
import kotlin.test.assertEquals
import kotlin.test.Test

class StepUseCasesTest {

    private val engine = FakeEngine()

    @Test
    fun `when speed is off the grid then a step up snaps to the next multiple of five`() {
        engine.speed = 73f

        StepSpeedUseCase(engine)(1)

        assertEquals(75f, engine.speedPercent)
    }

    @Test
    fun `when speed is on the grid then a step moves by five`() {
        engine.speed = 74.9998f

        StepSpeedUseCase(engine)(1)

        assertEquals(80f, engine.speedPercent)
    }

    @Test
    fun `when speed is off the grid then a step down snaps to the previous multiple of five`() {
        engine.speed = 73f

        StepSpeedUseCase(engine)(-1)

        assertEquals(70f, engine.speedPercent)
    }

    @Test
    fun `when stepping past the limits then speed is clamped`() {
        engine.speed = 200f

        StepSpeedUseCase(engine)(1)

        assertEquals(200f, engine.speedPercent)
    }

    @Test
    fun `when stepping pitch then it moves by whole semitones within range`() {
        engine.pitch = 11.6f

        StepPitchUseCase(engine)(1)

        assertEquals(12f, engine.pitchSemitones)
    }

    @Test
    fun `when handles are dropped in any order then the loop is set between them`() {
        SetLoopRegionUseCase(engine)(0.6f, 0.2f)

        assertEquals(listOf("loop:${LoopRegion(0.2f, 0.6f, isOn = true)}"), engine.practiceCalls)
    }
}
