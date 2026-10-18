package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.Dimension
import java.awt.Rectangle
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowBoundsTest {

    private val node = Preferences.userRoot().node("com/damianogiusti/slowdowner/test/window-${UUID.randomUUID()}")

    @AfterTest
    fun tearDown() = node.removeNode()

    @Test
    fun `on a large screen the first window has the design size`() {
        assertEquals(DesignWindowSize, defaultWindowSize(DpSize(1920.dp, 1032.dp)))
        assertEquals(DesignWindowSize, defaultWindowSize(DpSize(2560.dp, 1400.dp)))
    }

    @Test
    fun `on a laptop screen the first window leaves a margin of the usable area`() {
        // 1366×768 with the Windows taskbar.
        assertEquals(DpSize(1280.dp, 677.dp), defaultWindowSize(DpSize(1366.dp, 720.dp)))
        // A 1280×800 MacBook with menu bar and Dock: the width keeps the progression on one line.
        assertEquals(DpSize(1256.dp, 667.dp), defaultWindowSize(DpSize(1280.dp, 710.dp)))
    }

    @Test
    fun `on a 1280x720 screen the width margin gives way so the progression keeps one line`() {
        // With the Windows taskbar. 1256 less the 16dp side borders leaves the player 1240dp.
        assertEquals(DpSize(1256.dp, 632.dp), defaultWindowSize(DpSize(1280.dp, 672.dp)))
        // Too narrow for one line anyway: the usual margin.
        assertEquals(DpSize(1175.dp, 632.dp), defaultWindowSize(DpSize(1250.dp, 672.dp)))
    }

    @Test
    fun `the first window is never below the minimum, unless the screen is`() {
        assertEquals(MinimumWindowSize, defaultWindowSize(DpSize(1040.dp, 630.dp)))
        assertEquals(DpSize(900.dp, 500.dp), defaultWindowSize(DpSize(900.dp, 500.dp)))
    }

    @Test
    fun `without saved bounds the window opens centred on the primary screen`() {
        val bounds = restoreWindowBounds(null, listOf(Rectangle(0, 0, 1920, 1032), Rectangle(1920, 0, 1280, 720)))

        assertEquals(WindowBounds(320, 106, 1280, 820), bounds)
    }

    @Test
    fun `saved bounds on a screen that is still there come back as they were`() {
        val saved = WindowBounds(100, 50, 1100, 700, isMaximized = true)

        assertEquals(saved, restoreWindowBounds(saved, listOf(Rectangle(0, 0, 1920, 1032))))
    }

    @Test
    fun `saved bounds partly off screen are moved fully onto it`() {
        val bounds = restoreWindowBounds(WindowBounds(1500, -40, 1100, 700), listOf(Rectangle(0, 0, 1920, 1032)))

        assertEquals(WindowBounds(820, 0, 1100, 700), bounds)
    }

    @Test
    fun `saved bounds larger than the screen now are shrunk to its usable area`() {
        // Saved on a 4K screen, opened on a laptop.
        val bounds = restoreWindowBounds(WindowBounds(0, 0, 2400, 1300), listOf(Rectangle(0, 0, 1366, 728)))

        assertEquals(WindowBounds(0, 0, 1366, 728), bounds)
    }

    @Test
    fun `a window saved on an unplugged monitor opens centred on the primary screen`() {
        val bounds = restoreWindowBounds(WindowBounds(2200, 100, 1200, 760, isMaximized = true), listOf(Rectangle(0, 0, 1366, 728)))

        assertEquals(1280, bounds.width)
        assertEquals(684, bounds.height)
        assertEquals((1366 - 1280) / 2, bounds.x)
        assertEquals((728 - 684) / 2, bounds.y)
        assertTrue(bounds.isMaximized, "maximized stays maximized")
    }

    @Test
    fun `a window goes back to the screen that shows most of it`() {
        val screens = listOf(Rectangle(0, 0, 1920, 1032), Rectangle(1920, 0, 1440, 860))
        val bounds = restoreWindowBounds(WindowBounds(1800, 100, 1200, 700), screens)

        assertEquals(WindowBounds(1920, 100, 1200, 700), bounds)
    }

    @Test
    fun `a saved window smaller than the minimum grows to it`() {
        val bounds = restoreWindowBounds(WindowBounds(10, 10, 300, 200), listOf(Rectangle(0, 0, 1920, 1032)))

        assertEquals(WindowBounds(10, 10, 1000, 600), bounds)
    }

    @Test
    fun `the minimum size comes from the screen the window opens on`() {
        val screens = listOf(Rectangle(0, 0, 1920, 1032), Rectangle(1920, 0, 960, 560))

        assertEquals(Dimension(1000, 600), minimumWindowSize(WindowBounds(100, 100, 1200, 800), screens))
        // Restored onto the small second screen: no larger than it, though the primary has room.
        assertEquals(Dimension(960, 560), minimumWindowSize(WindowBounds(1920, 0, 960, 560), screens))
        // Mostly on the small screen.
        assertEquals(Dimension(960, 560), minimumWindowSize(WindowBounds(1800, 0, 1000, 560), screens))
    }

    @Test
    fun `the minimum size falls back to the primary screen, then to the minimum`() {
        val small = Rectangle(0, 0, 900, 500)

        assertEquals(Dimension(900, 500), minimumWindowSize(WindowBounds(5000, 5000, 1000, 600), listOf(small)))
        assertEquals(Dimension(1000, 600), minimumWindowSize(WindowBounds(0, 0, 1000, 600), emptyList()))
    }

    @Test
    fun `the store keeps the bounds between runs`() {
        val store = WindowBoundsStore(node)
        assertNull(store.read())

        store.write(WindowBounds(-1200, 40, 1100, 690, isMaximized = true))

        assertEquals(WindowBounds(-1200, 40, 1100, 690, isMaximized = true), WindowBoundsStore(node).read())
    }

    @Test
    fun `the store reads nothing from incomplete values`() {
        node.putInt("width", 1100)
        node.putInt("height", 700)

        assertNull(WindowBoundsStore(node).read())
    }
}
