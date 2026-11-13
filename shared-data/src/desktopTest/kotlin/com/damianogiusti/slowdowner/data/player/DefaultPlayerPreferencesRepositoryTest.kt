package com.damianogiusti.slowdowner.data.player

import com.damianogiusti.slowdowner.domain.player.PlayerPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPlayerPreferencesRepositoryTest {

    private val store = FakeStore()
    private val repository = DefaultPlayerPreferencesRepository(store, UnconfinedTestDispatcher())

    @Test
    fun `when nothing is stored then Fine is off`() = runTest {
        assertEquals(PlayerPreferences(isPitchFine = false), repository.observe().first())
    }

    @Test
    fun `when Fine is saved then it is observed and written to the store`() = runTest {
        repository.save(PlayerPreferences(isPitchFine = true))

        assertEquals(PlayerPreferences(isPitchFine = true), repository.observe().first())
        assertEquals(PlayerPreferencesDto(isPitchFine = true), store.stored)
    }

    @Test
    fun `when a new repository starts then it reloads from the store`() = runTest {
        repository.save(PlayerPreferences(isPitchFine = true))

        val reloaded = DefaultPlayerPreferencesRepository(store, UnconfinedTestDispatcher())

        assertEquals(true, reloaded.current().isPitchFine)
    }

    private class FakeStore : PlayerPreferencesStore {
        var stored: PlayerPreferencesDto? = null
        override fun read() = stored
        override fun write(preferences: PlayerPreferencesDto) { stored = preferences }
    }
}
