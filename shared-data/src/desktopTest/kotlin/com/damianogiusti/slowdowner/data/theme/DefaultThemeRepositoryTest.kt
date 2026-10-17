package com.damianogiusti.slowdowner.data.theme

import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.prefs.Preferences

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultThemeRepositoryTest {

    // A throwaway node: the real one holds the user's choice.
    private val node = Preferences.userRoot().node("com/damianogiusti/slowdowner/test/theme-${System.nanoTime()}")
    private val store = PreferencesThemeStore(node)

    @After
    fun tearDown() = node.removeNode()

    @Test
    fun `when nothing was saved then the theme is dark`() = runTest {
        assertEquals(ThemeMode.Dark, repository().observe().first())
    }

    @Test
    fun `when a theme is saved then a new repository reads it back`() = runTest {
        repository().save(ThemeMode.Light)

        assertEquals(ThemeMode.Light, repository().observe().first())
    }

    @Test
    fun `when the stored name is unknown then the default is used`() = runTest {
        store.write("Sepia")

        assertEquals(ThemeMode.DEFAULT, repository().observe().first())
    }

    private fun repository() = DefaultThemeRepository(store, UnconfinedTestDispatcher())
}
