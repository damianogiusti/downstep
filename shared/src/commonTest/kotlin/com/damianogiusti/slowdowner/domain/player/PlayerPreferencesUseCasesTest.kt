package com.damianogiusti.slowdowner.domain.player

import com.damianogiusti.slowdowner.fake.FakePlayerPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerPreferencesUseCasesTest {

    private val repository = FakePlayerPreferencesRepository()

    @Test
    fun `when nothing was chosen then Fine is off`() = runTest {
        assertEquals(PlayerPreferences(isPitchFine = false), ObservePlayerPreferencesUseCase(repository)().first())
    }

    @Test
    fun `when Fine is turned on then it is saved and observed`() = runTest {
        SetPitchFineUseCase(repository)(true)

        assertEquals(PlayerPreferences(isPitchFine = true), ObservePlayerPreferencesUseCase(repository)().first())
    }

    @Test
    fun `when Fine is turned off then it is saved and observed`() = runTest {
        val on = FakePlayerPreferencesRepository(PlayerPreferences(isPitchFine = true))

        SetPitchFineUseCase(on)(false)

        assertEquals(false, on.preferences.value.isPitchFine)
    }

    @Test
    fun `when nothing was chosen then loop Fine and the waveform are off`() = runTest {
        val preferences = ObservePlayerPreferencesUseCase(repository)().first()

        assertFalse(preferences.isLoopFine)
        assertFalse(preferences.isWaveformShown)
    }

    @Test
    fun `when loop Fine is turned on then the other choices are kept`() = runTest {
        val pitchFine = FakePlayerPreferencesRepository(PlayerPreferences(isPitchFine = true))

        SetLoopFineUseCase(pitchFine)(true)

        assertEquals(PlayerPreferences(isPitchFine = true, isLoopFine = true), pitchFine.preferences.value)
    }

    @Test
    fun `when the waveform is shown then the other choices are kept`() = runTest {
        val loopFine = FakePlayerPreferencesRepository(PlayerPreferences(isLoopFine = true))

        SetWaveformShownUseCase(loopFine)(true)

        assertTrue(loopFine.preferences.value.isWaveformShown)
        assertTrue(loopFine.preferences.value.isLoopFine)
    }
}
