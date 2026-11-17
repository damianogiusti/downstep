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
    fun `when stepping pitch then the cents are kept`() {
        engine.pitch = 2.15f

        StepPitchUseCase(engine)(1)
        assertEquals(3.15f, engine.pitchSemitones)

        StepPitchUseCase(engine)(-2)
        assertEquals(1.15f, engine.pitchSemitones)
    }

    @Test
    fun `when stepping pitch in Fine mode then it moves by five cents`() {
        engine.pitch = 2f

        StepPitchUseCase(engine)(1, PitchStep.Fine)
        assertEquals(2.05f, engine.pitchSemitones)

        StepPitchUseCase(engine)(-3, PitchStep.Fine)
        assertEquals(1.9f, engine.pitchSemitones)
    }

    @Test
    fun `when stepping in Fine mode then float noise is rounded away`() {
        engine.pitch = 2.15f

        StepPitchUseCase(engine)(1, PitchStep.Fine)

        assertEquals(2.2f, engine.pitchSemitones)
    }

    @Test
    fun `when stepping pitch past the ends then it is clamped`() {
        engine.pitch = 11.98f
        StepPitchUseCase(engine)(1, PitchStep.Fine)
        assertEquals(12f, engine.pitchSemitones)

        engine.pitch = -11.98f
        StepPitchUseCase(engine)(-1)
        assertEquals(-12f, engine.pitchSemitones)
    }

    @Test
    fun `when handles are dropped in any order then the loop is set between them`() {
        SetLoopRegionUseCase(engine)(0.6f, 0.2f)

        assertEquals(listOf("loop:${LoopRegion(0.2f, 0.6f, isOn = true)}"), engine.practiceCalls)
    }
}
