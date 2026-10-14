package com.damianogiusti.slowdowner.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoopEditingTest {

    // 100 s file: 0.01 = 1 s.
    private val duration = 100f

    @Test
    fun `A on a loop that is off starts a loop to the end`() {
        val state = LoopEditing.markStart(LoopState(0.2f, 0.3f, isOn = false, position = 0.5f), duration, delaySeconds = 0f)
        assertEquals(LoopState(0.5f, 1f, isOn = true, position = 0.5f), state)
    }

    @Test
    fun `A is set slightly before the press`() {
        val state = LoopEditing.markStart(LoopState(0f, 1f, isOn = true, position = 0.5f), duration)
        assertEquals(0.4995f, state.start, 1e-6f)
    }

    @Test
    fun `A too close to B keeps one second of loop`() {
        val state = LoopEditing.markStart(LoopState(0.2f, 0.6f, isOn = true, position = 0.598f), duration, 0f)
        assertEquals(0.59f, state.start, 1e-6f)
    }

    @Test
    fun `B ends the loop here and goes back to A`() {
        val state = LoopEditing.markEnd(LoopState(0.2f, 1f, isOn = false, position = 0.35f), duration, 0f)
        assertEquals(LoopState(0.2f, 0.35f, isOn = true, position = 0.2f), state)
    }

    @Test
    fun `B right after A keeps one second of loop`() {
        val state = LoopEditing.markEnd(LoopState(0.2f, 1f, isOn = true, position = 0.203f), duration, 0f)
        assertEquals(0.21f, state.end, 1e-6f)
    }

    @Test
    fun `halving moves the play position back when it falls outside`() {
        val state = LoopEditing.halve(LoopState(0.2f, 0.4f, isOn = true, position = 0.35f), duration)
        assertEquals(LoopState(0.2f, 0.3f, isOn = true, position = 0.2f), state)
    }

    @Test
    fun `halving below one second is refused`() {
        assertNull(LoopEditing.halve(LoopState(0.2f, 0.215f, isOn = true, position = 0.2f), duration))
    }

    @Test
    fun `doubling past the end is refused`() {
        assertEquals(0.6f, LoopEditing.double(LoopState(0.2f, 0.4f, true, 0.3f))?.end ?: 0f, 1e-6f)
        assertNull(LoopEditing.double(LoopState(0.6f, 0.9f, true, 0.7f)))
    }

    @Test
    fun `shifting moves the whole loop and restarts at its start`() {
        val forward = LoopEditing.shiftForward(LoopState(0.2f, 0.3f, true, 0.25f))
        assertEquals(0.3f, forward?.start ?: 0f, 1e-6f)
        assertEquals(0.4f, forward?.end ?: 0f, 1e-6f)
        assertEquals(0.3f, forward?.position ?: 0f, 1e-6f)
        val backward = LoopEditing.shiftBackward(LoopState(0.2f, 0.3f, true, 0.25f))
        assertEquals(0.1f, backward?.start ?: 0f, 1e-6f)
        assertNull(LoopEditing.shiftForward(LoopState(0.6f, 0.9f, true, 0.7f)))
        assertNull(LoopEditing.shiftBackward(LoopState(0.05f, 0.15f, true, 0.1f)))
    }
}
