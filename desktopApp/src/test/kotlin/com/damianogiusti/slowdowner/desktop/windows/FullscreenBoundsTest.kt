package com.damianogiusti.slowdowner.desktop.windows

import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The borderless fullscreen window keeps covering its monitor, and comes back on the monitor it is on. */
class FullscreenBoundsTest {

    // Two monitors side by side: 1920×1080 at 100 %, then 2560×1440 at 150 % on its right.
    private val left = IntRect(0, 0, 1920, 1080)
    private val right = IntRect(1920, 0, 4480, 1440)
    private val monitorOf: (IntRect) -> IntRect? = { rect -> if (rect.center.x < 1920) left else right }

    @Test
    fun `moved to the next monitor, it covers that monitor`() {
        // Win+Shift+Right: same size, moved by the monitor's width.
        val pos = fullscreenWindowPos(left, 1920, 0, 1920, 1080, flags = 0, monitorOf = monitorOf)
        assertEquals(right, pos)
    }

    @Test
    fun `resized for a new DPI, it covers its monitor again`() {
        // WM_DPICHANGED's suggested rect: the old size scaled up, larger than the monitor.
        val pos = fullscreenWindowPos(left, 0, 0, 2880, 1620, flags = 0, monitorOf = { left })
        assertEquals(left, pos)
    }

    @Test
    fun `a size alone keeps the place, and still covers the monitor`() {
        val pos = fullscreenWindowPos(left, 0, 0, 800, 600, flags = 0x0002 /* SWP_NOMOVE */, monitorOf = monitorOf)
        assertEquals(left, pos)
    }

    @Test
    fun `a move alone keeps the size`() {
        var asked: IntRect? = null
        fullscreenWindowPos(left, 1920, 0, 0, 0, flags = 0x0001 /* SWP_NOSIZE */) { asked = it; right }
        assertEquals(IntRect(1920, 0, 3840, 1080), asked)
    }

    @Test
    fun `no move and no size, or minimizing, is left alone`() {
        assertNull(fullscreenWindowPos(left, 0, 0, 0, 0, flags = 0x0003, monitorOf = monitorOf))
        assertNull(fullscreenWindowPos(left, -32000, -32000, 160, 28, flags = 0, monitorOf = monitorOf))
        assertNull(fullscreenWindowPos(left, 10, 10, 100, 100, flags = 0, monitorOf = { null }))
    }

    @Test
    fun `leaving fullscreen on the same monitor restores the saved bounds`() {
        val saved = IntRect(100, 80, 1380, 900)
        val work = IntRect(0, 0, 1920, 1032)
        assertEquals(saved, boundsAfterFullscreen(saved, work, work))
    }

    @Test
    fun `leaving fullscreen on another monitor keeps the size and the place on it`() {
        val saved = IntRect(100, 80, 1380, 900)
        val from = IntRect(0, 0, 1920, 1032)
        val to = IntRect(1920, 0, 4480, 1392)
        assertEquals(IntRect(2020, 80, 3300, 900), boundsAfterFullscreen(saved, from, to))
    }

    @Test
    fun `a window too big for the other monitor shrinks to fit it`() {
        val saved = IntRect(1950, 40, 4450, 1390)
        val from = IntRect(1920, 0, 4480, 1392)
        val to = IntRect(0, 0, 1920, 1032)
        assertEquals(IntRect(0, 0, 1920, 1032), boundsAfterFullscreen(saved, from, to))
    }

    @Test
    fun `a saved place on a monitor that is gone is centred on the current one`() {
        val saved = IntRect(-1800, 100, -520, 920)
        val to = IntRect(0, 0, 1920, 1032)
        assertEquals(IntRect(320, 106, 1600, 926), boundsAfterFullscreen(saved, null, to))
    }
}
