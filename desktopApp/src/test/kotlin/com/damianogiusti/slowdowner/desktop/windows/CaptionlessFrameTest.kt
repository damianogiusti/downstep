package com.damianogiusti.slowdowner.desktop.windows

import androidx.compose.ui.unit.IntRect
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

/** WM_NCCALCSIZE's client area without the caption. */
class CaptionlessFrameTest {

    // A floating window at 100 %: 8 px invisible borders left, right and below, a 31 px caption.
    private val window = IntRect(100, 50, 1380, 870)
    private val standard = IntRect(108, 81, 1372, 862)

    @Test
    fun `a floating window loses its caption and keeps its other borders`() {
        val area = captionlessClientArea(window, standard, isMaximized = false, isFullscreen = false, maximizedTopInset = 8)
        assertEquals(IntRect(108, 50, 1372, 862), area)
    }

    // Maximized on a 1920×1080 screen with a 48 px taskbar below: the window overhangs it by 8 px.
    private val maximizedWindow = IntRect(-8, -8, 1928, 1040)
    private val maximizedStandard = IntRect(0, 23, 1920, 1032)

    @Test
    fun `a maximized window starts at the screen top, not above it`() {
        val area = captionlessClientArea(maximizedWindow, maximizedStandard, isMaximized = true, isFullscreen = false, maximizedTopInset = 8)
        assertEquals(IntRect(0, 0, 1920, 1032), area)
    }

    @Test
    fun `the overhang scales with the DPI`() {
        // 150 %: a 12 px overhang.
        val window = IntRect(-12, -12, 1932, 1044)
        val standard = IntRect(0, 34, 1920, 1032)
        val area = captionlessClientArea(window, standard, isMaximized = true, isFullscreen = false, maximizedTopInset = 12)
        assertEquals(0, area.top)
    }

    @Test
    fun `an auto-hide taskbar edge stays reachable`() {
        val window = IntRect(-8, -8, 1928, 1088)
        val standard = IntRect(0, 23, 1920, 1080)
        val bottom = captionlessClientArea(window, standard, true, false, 8, setOf(ScreenEdge.Bottom))
        assertEquals(IntRect(0, 0, 1920, 1080 - AUTO_HIDE_GAP), bottom)
        val left = captionlessClientArea(window, standard, true, false, 8, setOf(ScreenEdge.Left))
        assertEquals(IntRect(AUTO_HIDE_GAP, 0, 1920, 1080), left)
        val top = captionlessClientArea(window, standard, true, false, 8, setOf(ScreenEdge.Top))
        assertEquals(IntRect(0, AUTO_HIDE_GAP, 1920, 1080), top)
        val right = captionlessClientArea(window, standard, true, false, 8, setOf(ScreenEdge.Right))
        assertEquals(IntRect(0, 0, 1920 - AUTO_HIDE_GAP, 1080), right)
    }

    @Test
    fun `an auto-hide taskbar does not change a floating window`() {
        val area = captionlessClientArea(window, standard, false, false, 8, setOf(ScreenEdge.Bottom))
        assertEquals(IntRect(108, 50, 1372, 862), area)
    }

    @Test
    fun `fullscreen is the whole window`() {
        val monitor = IntRect(0, 0, 1920, 1080)
        assertEquals(monitor, captionlessClientArea(monitor, IntRect(0, 0, 1920, 1080), isMaximized = false, isFullscreen = true, maximizedTopInset = 8))
        assertEquals(monitor, captionlessClientArea(monitor, IntRect(8, 31, 1912, 1072), isMaximized = true, isFullscreen = true, maximizedTopInset = 8))
    }

    @Test
    fun `the minimum size holds in dp at any scale`() {
        val minimum = Dimension(1000, 600)
        assertEquals(Dimension(1000, 600), minimumTrackSize(Dimension(1000, 600), minimum, dpi = 96))
        // 125 %: AWT asked for its dp as pixels.
        assertEquals(Dimension(1250, 750), minimumTrackSize(Dimension(1000, 600), minimum, dpi = 120))
        assertEquals(Dimension(1500, 900), minimumTrackSize(Dimension(136, 39), minimum, dpi = 144))
    }

    @Test
    fun `a larger minimum from before stays`() {
        assertEquals(Dimension(1300, 800), minimumTrackSize(Dimension(1300, 800), Dimension(1000, 600), dpi = 120))
    }
}