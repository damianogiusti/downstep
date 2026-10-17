package com.damianogiusti.slowdowner.data.theme

import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.domain.theme.ThemeSelection
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
    fun `when nothing was saved then the theme is the dark Downstep palette`() = runTest {
        assertEquals(ThemeSelection(ThemeMode.Dark, "downstep"), repository().observe().first())
    }

    @Test
    fun `when a theme is saved then a new repository reads it back`() = runTest {
        repository().save(ThemeSelection(ThemeMode.Light, "forest"))

        assertEquals(ThemeSelection(ThemeMode.Light, "forest"), repository().observe().first())
    }

    @Test
    fun `when only a mode was saved, as before themes, then it comes back with the Downstep palette`() = runTest {
        // What the versions before themes wrote: the "mode" key alone.
        node.put("mode", "Light")
        node.flush()

        assertEquals(ThemeSelection(ThemeMode.Light, ThemeSelection.DEFAULT_THEME_ID), repository().observe().first())
    }

    @Test
    fun `when a theme is saved then the mode key keeps the format older versions read`() = runTest {
        repository().save(ThemeSelection(ThemeMode.Light, "dusk"))

        assertEquals("Light", node.get("mode", null))
        assertEquals("dusk", node.get("theme", null))
    }

    @Test
    fun `when the stored mode name is unknown then the default mode is used`() = runTest {
        store.write("Sepia", "graphite")

        assertEquals(ThemeSelection(ThemeMode.DEFAULT, "graphite"), repository().observe().first())
    }

    @Test
    fun `when the stored theme id is unknown then it is kept for the UI to fall back`() = runTest {
        // A newer version's palette: the UI shows Downstep, and going back to that version finds it.
        store.write("Dark", "from-the-future")

        assertEquals("from-the-future", repository().observe().first().themeId)
    }

    @Test
    fun `when a theme is saved then observers get the change`() = runTest {
        val repository = repository()
        assertEquals(ThemeSelection.DEFAULT, repository.observe().first())

        repository.save(ThemeSelection(ThemeMode.Light, "midnight"))

        assertEquals(ThemeSelection(ThemeMode.Light, "midnight"), repository.observe().first())
    }

    private fun repository() = DefaultThemeRepository(store, UnconfinedTestDispatcher())
}
