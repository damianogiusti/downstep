package com.damianogiusti.slowdowner.desktop.windows

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import com.damianogiusti.slowdowner.ui.common.TitleBarRegion
import kotlin.test.Test
import kotlin.test.assertEquals

/** WM_NCHITTEST's answer for the header: point + regions + window state → hit code. */
class TitleBarHitTestTest {

    // A 1200 px wide window whose header is 80 px tall: a pill at the left, the window buttons at the right.
    private val header = IntRect(0, 0, 1200, 80)
    private val pill = IntRect(24, 20, 140, 60)
    private val minimize = IntRect(1062, 0, 1108, 80)
    private val maximize = IntRect(1108, 0, 1154, 80)
    private val close = IntRect(1154, 0, 1200, 80)
    private val regions = TitleBarRegions(
        captions = listOf(header),
        controls = listOf(pill, minimize, close),
        maximizeButton = maximize,
    )

    /** 100 %: SM_CYFRAME 4 + SM_CXPADDEDBORDER 4. */
    private val floating = TitleBarWindowState(width = 1200, resizeBorder = 8)

    private fun hit(x: Int, y: Int, state: TitleBarWindowState = floating, regions: TitleBarRegions = this.regions) =
        titleBarHitTest(x, y, regions, state)

    @Test
    fun `empty header area is the caption`() {
        assertEquals(HitCode.CAPTION, hit(600, 40))
        assertEquals(HitCode.CAPTION, hit(10, 70))
        // Between the pill and the buttons, just outside the pill.
        assertEquals(HitCode.CAPTION, hit(140, 40))
    }

    @Test
    fun `a control keeps its clicks`() {
        assertEquals(HitCode.CLIENT, hit(24, 20))
        assertEquals(HitCode.CLIENT, hit(139, 59))
        assertEquals(HitCode.CLIENT, hit(1080, 40), "minimize")
        assertEquals(HitCode.CLIENT, hit(1199, 79), "close, in the corner")
    }

    @Test
    fun `the maximize button is the system's, for its snap layouts`() {
        assertEquals(HitCode.MAXBUTTON, hit(1108, 40))
        assertEquals(HitCode.MAXBUTTON, hit(1153, 79))
        assertEquals(HitCode.CLIENT, hit(1154, 40), "the next pixel is close")
    }

    @Test
    fun `below the header is the app's`() {
        assertEquals(HitCode.CLIENT, hit(600, 80))
        assertEquals(HitCode.CLIENT, hit(600, 500))
    }

    @Test
    fun `the top strip resizes, its ends as corners`() {
        assertEquals(HitCode.TOP, hit(600, 0))
        assertEquals(HitCode.TOP, hit(600, 7))
        assertEquals(HitCode.CAPTION, hit(600, 8), "just below the border")
        assertEquals(HitCode.TOPLEFT, hit(0, 3))
        assertEquals(HitCode.TOPLEFT, hit(15, 3))
        assertEquals(HitCode.TOP, hit(16, 3))
        assertEquals(HitCode.TOPRIGHT, hit(1199, 3))
        assertEquals(HitCode.TOPRIGHT, hit(1184, 3))
        assertEquals(HitCode.TOP, hit(1183, 3))
    }

    @Test
    fun `the top strip goes over the window buttons`() {
        assertEquals(HitCode.TOP, hit(1130, 2), "over maximize")
        assertEquals(HitCode.TOPRIGHT, hit(1195, 2), "over close")
    }

    @Test
    fun `the top strip scales with the window DPI`() {
        // 150 %: SM_CYFRAME 6 + SM_CXPADDEDBORDER 6.
        val scaled = floating.copy(resizeBorder = 12)
        assertEquals(HitCode.TOP, hit(600, 11, scaled))
        assertEquals(HitCode.CAPTION, hit(600, 12, scaled))
        assertEquals(HitCode.TOPLEFT, hit(23, 2, scaled))
        assertEquals(HitCode.TOP, hit(24, 2, scaled))
    }

    @Test
    fun `a maximized window has no top edge`() {
        val maximized = floating.copy(isMaximized = true)
        assertEquals(HitCode.CAPTION, hit(600, 0, maximized))
        assertEquals(HitCode.CAPTION, hit(0, 0, maximized))
        assertEquals(HitCode.MAXBUTTON, hit(1120, 0, maximized), "the button reaches the screen top")
        assertEquals(HitCode.CLIENT, hit(1199, 0, maximized), "close in the screen corner")
    }

    @Test
    fun `fullscreen is the app's everywhere`() {
        val fullscreen = floating.copy(isFullscreen = true)
        for ((x, y) in listOf(600 to 0, 600 to 40, 1120 to 40, 0 to 0)) {
            assertEquals(HitCode.CLIENT, hit(x, y, fullscreen), "($x, $y)")
        }
    }

    @Test
    fun `an overlay over the header makes it the app's`() {
        val covered = regions.copy(hasOverlay = true)
        for ((x, y) in listOf(600 to 40, 600 to 0, 1120 to 40, 0 to 0, 1199 to 2)) {
            assertEquals(HitCode.CLIENT, hit(x, y, regions = covered), "($x, $y)")
        }
    }

    @Test
    fun `without any header nothing drags`() {
        assertEquals(HitCode.CLIENT, hit(600, 40, regions = TitleBarRegions.NONE))
        assertEquals(HitCode.TOP, hit(600, 2, regions = TitleBarRegions.NONE), "the edge still resizes")
    }

    @Test
    fun `the top strip follows the window client area when Compose's starts lower`() {
        // Compose's child window starts 10 px down: its y 0 is 10 px below the window's top.
        assertEquals(HitCode.TOP, titleBarHitTest(600, -5, regions, floating, fromTop = 5, fromLeft = 600))
        assertEquals(HitCode.CAPTION, titleBarHitTest(600, 0, regions, floating, fromTop = 10, fromLeft = 600))
    }

    @Test
    fun `controls grow to the pixels they touch, captions round`() {
        val bounds = Rect(10.4f, 20.6f, 50.2f, 60.5f)
        assertEquals(IntRect(10, 20, 51, 61), bounds.toRegionPixels(isControl = true))
        assertEquals(IntRect(10, 21, 50, 61), bounds.toRegionPixels(isControl = false))
    }

    @Test
    fun `the snapshot sorts the regions by kind`() {
        val snapshot = snapshotOf(
            listOf(
                TitleBarRegion.Caption to Rect(0f, 0f, 1200f, 80f),
                TitleBarRegion.Control to Rect(24f, 20f, 140f, 60f),
                TitleBarRegion.MaximizeButton to Rect(1108f, 0f, 1154f, 80f),
                TitleBarRegion.Control to Rect(1154f, 0f, 1200f, 80f),
            ),
            hasOverlay = true,
        )
        assertEquals(listOf(header), snapshot.captions)
        assertEquals(listOf(pill, close), snapshot.controls)
        assertEquals(maximize, snapshot.maximizeButton)
        assertEquals(true, snapshot.hasOverlay)
    }
}
