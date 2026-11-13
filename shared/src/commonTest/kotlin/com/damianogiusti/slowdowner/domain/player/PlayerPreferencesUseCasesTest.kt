package com.damianogiusti.slowdowner.domain.player

import com.damianogiusti.slowdowner.fake.FakePlayerPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
