package com.damianogiusti.slowdowner.data.click

import com.damianogiusti.slowdowner.domain.click.ClickSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.prefs.Preferences

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultClickSettingsRepositoryTest {

    // A throwaway node: the real one holds the user's choices.
    private val node = Preferences.userRoot().node("com/damianogiusti/slowdowner/test/click-${System.nanoTime()}")
    private val store = PreferencesClickSettingsStore(node)

    @After
    fun tearDown() = node.removeNode()

    @Test
    fun `when nothing was saved then the metronome and count-in are off, with one bar of count-in`() = runTest {
        assertEquals(ClickSettings(), repository().observe().first())
        assertEquals(4, repository().observe().first().countInBeats)
    }

    @Test
    fun `when the settings are saved then a new repository reads them back`() = runTest {
        val saved = ClickSettings(isMetronomeOn = true, volume = 0.35f, isCountInOn = true, countInBeats = 8, countInEveryLoop = true)
        repository().save(saved)

        assertEquals(saved, repository().observe().first())
        assertEquals("true", node.get("metronome_enabled", null))
        assertEquals("8", node.get("count_in_beats", null))
    }

    @Test
    fun `when a stored value is out of range or unreadable then it is fixed or defaulted`() = runTest {
        node.put("click_volume", "7")
        node.put("count_in_beats", "-3")
        node.put("count_in_enabled", "maybe")

        assertEquals(
            ClickSettings(volume = 1f, countInBeats = ClickSettings.MIN_COUNT_IN_BEATS, isCountInOn = false),
            repository().observe().first(),
        )
    }

    private fun repository() = DefaultClickSettingsRepository(store, UnconfinedTestDispatcher())
}
