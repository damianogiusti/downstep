package com.damianogiusti.slowdowner.data.practice

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.practice.SavePresetResult
import com.damianogiusti.slowdowner.fake.FakePracticeDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPracticeRepositoryTest {

    private val dao = FakePracticeDao(existingTrackIds = setOf("a"))
    private val repository = DefaultPracticeRepository(dao, UnconfinedTestDispatcher(), now = { 42 })
    private val settings = PracticeSettings(75f, -2f, LoopRegion(0.2f, 0.4f, isOn = true))

    @Test
    fun `when a state is saved then it is read back as the same settings`() = runTest {
        repository.saveLastState("a", settings)

        assertEquals(settings, repository.lastState("a"))
        assertEquals(mapOf("a" to settings), repository.observeLastStates().first())
    }

    @Test
    fun `when the track no longer exists then the state is not saved`() = runTest {
        repository.saveLastState("deleted", settings)

        assertNull(repository.lastState("deleted"))
    }

    @Test
    fun `when a preset is saved then it is observed with its new id`() = runTest {
        val saved = repository.savePreset("a", "Solo", settings) as SavePresetResult.Saved

        assertEquals(listOf(saved.preset), repository.observePresets("a").first())
        assertEquals(settings, saved.preset.settings)
    }

    @Test
    fun `when the preset name exists on the track then it is reported as taken`() = runTest {
        repository.savePreset("a", "Solo", settings)

        assertEquals(SavePresetResult.NameTaken, repository.savePreset("a", "Solo", settings))
    }
}
