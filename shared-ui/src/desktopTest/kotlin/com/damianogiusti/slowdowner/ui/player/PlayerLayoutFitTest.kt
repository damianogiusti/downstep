package com.damianogiusti.slowdowner.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerLayoutFitTest {

    private val parts = PlayerPartHeights()

    @Test
    fun `when the window is tall then nothing is compressed and the overview grows up to its cap`() {
        val fit = PlayerLayoutFit.of(WIDE, 1200.dp, isFocusMode = false, hasLoopCards = true)

        assertEquals(parts.overviewGrown, fit.overviewHeight)
        assertEquals(parts.zoom, fit.zoomHeight)
        assertFalse(fit.compactCards || fit.compactLoopCards || fit.compactChips || fit.compactTransport || fit.overflows)
    }

    @Test
    fun `when there is a little less room than the design then only the overview shrinks`() {
        val fit = PlayerLayoutFit.of(WIDE, fullRest(hasLoopCards = true) + 180.dp, isFocusMode = false, hasLoopCards = true)

        assertEquals(180.dp, fit.overviewHeight)
        assertFalse(fit.compactCards)
        assertEquals(parts.zoom, fit.zoomHeight)
    }

    @Test
    fun `when the overview would go below its first floor then the cards get compact before anything else`() {
        val height = fullRest(hasLoopCards = true) + parts.overviewShrunk - 1.dp
        val fit = PlayerLayoutFit.of(WIDE, height, isFocusMode = false, hasLoopCards = true)

        assertTrue(fit.compactCards && fit.compactLoopCards)
        assertFalse(fit.compactChips || fit.compactTransport)
        assertEquals(parts.zoom, fit.zoomHeight)
        // The overview stays at the floor it had a dp taller: the room the cards gave stays below them.
        assertEquals(parts.overviewShrunk, fit.overviewHeight)
    }

    @Test
    fun `when the window is short then chrome and zoom give way and the overview keeps at least its minimum`() {
        listOf(560.dp, 600.dp, 640.dp).forEach { height ->
            val fit = PlayerLayoutFit.of(WIDE, height, isFocusMode = false, hasLoopCards = true)

            assertTrue(fit.overviewHeight >= parts.overviewMin, "$height: ${fit.overviewHeight}")
            assertTrue(fit.overflows || used(fit, WIDE, hasLoopCards = true) <= height, "$height: $fit does not fit")
        }
    }

    @Test
    fun `when even the most compact layout is too tall then the cards overflow at the smallest sizes`() {
        val fit = PlayerLayoutFit.of(WIDE, 400.dp, isFocusMode = false, hasLoopCards = true)

        assertTrue(fit.overflows)
        assertEquals(parts.overviewMin, fit.overviewHeight)
        assertEquals(parts.zoomMin, fit.zoomHeight)
        assertTrue(fit.compactCards && fit.compactLoopCards && fit.compactChips && fit.compactTransport)
    }

    @Test
    fun `when there is no active loop then the space of its cards goes to the overview`() {
        val height = 620.dp
        val withLoop = PlayerLayoutFit.of(WIDE, height, isFocusMode = false, hasLoopCards = true)
        val without = PlayerLayoutFit.of(WIDE, height, isFocusMode = false, hasLoopCards = false)

        assertTrue(without.overviewHeight > withLoop.overviewHeight || (!without.compactCards && withLoop.compactCards))
    }

    @Test
    fun `when the window is narrow then the wrapped progression sentence is counted`() {
        val height = fullRest(hasLoopCards = true) + parts.overviewShrunk
        val wide = PlayerLayoutFit.of(WIDE, height, isFocusMode = false, hasLoopCards = true)
        val narrow = PlayerLayoutFit.of(NARROW, height, isFocusMode = false, hasLoopCards = true)

        assertFalse(wide.compactCards)
        assertTrue(narrow.compactCards)
    }

    @Test
    fun `the keyboard hints show only on a wide window and never in focus mode`() {
        assertTrue(PlayerLayoutFit.of(WIDE, 800.dp, isFocusMode = false, hasLoopCards = false).showKeyboardHints)
        assertFalse(PlayerLayoutFit.of(NARROW, 800.dp, isFocusMode = false, hasLoopCards = false).showKeyboardHints)
        assertFalse(PlayerLayoutFit.of(WIDE, 800.dp, isFocusMode = true, hasLoopCards = false).showKeyboardHints)
    }

    @Test
    fun `focus mode keeps its tall overview when there is room and fits the minimum window`() {
        val tall = PlayerLayoutFit.of(WIDE, 900.dp, isFocusMode = true, hasLoopCards = true)
        assertEquals(parts.overviewFocus, tall.overviewHeight)
        assertFalse(tall.compactChips)

        val minimum = PlayerLayoutFit.of(NARROW, 568.dp, isFocusMode = true, hasLoopCards = true)
        assertFalse(minimum.overflows)
        assertFalse(minimum.compactCards, "focus mode has no cards")
        assertTrue(used(minimum, NARROW, hasLoopCards = true, isFocusMode = true) <= 568.dp)
    }

    @Test
    fun `focus mode compresses the overview and chrome on a very short window`() {
        val fit = PlayerLayoutFit.of(WIDE, 380.dp, isFocusMode = true, hasLoopCards = true)

        assertTrue(fit.compactChips && fit.compactTransport)
        assertTrue(fit.overviewHeight < parts.overviewShrunk)
        assertTrue(used(fit, WIDE, hasLoopCards = true, isFocusMode = true) <= 380.dp)
    }

    @Test
    fun `the fit never grows when the window shrinks`() {
        var previous: Dp? = null
        for (height in 1000 downTo 450 step 5) {
            val fit = PlayerLayoutFit.of(WIDE, height.dp, isFocusMode = false, hasLoopCards = true)
            val used = used(fit, WIDE, hasLoopCards = true)
            previous?.let { assertTrue(used <= it, "$height dp: $used after $it") }
            previous = used
        }
    }

    @Test
    fun `the overview never grows when the window gets shorter, at any width, in both modes`() {
        for (width in listOf(WIDE, NARROW, parts.loopCardsOneLineWidth, parts.loopCardsOneLineWidth - 1.dp)) {
            for (isFocusMode in listOf(false, true)) {
                for (hasLoopCards in listOf(false, true)) {
                    var previous: PlayerLayoutFit? = null
                    for (height in 1300 downTo 300) {
                        val fit = PlayerLayoutFit.of(width, height.dp, isFocusMode, hasLoopCards)
                        val what = "$width × $height dp, focus $isFocusMode, loop cards $hasLoopCards"
                        previous?.let {
                            assertTrue(fit.overviewHeight <= it.overviewHeight, "$what: ${fit.overviewHeight} after ${it.overviewHeight}")
                            assertTrue(fit.zoomHeight <= it.zoomHeight, "$what: zoom ${fit.zoomHeight} after ${it.zoomHeight}")
                            val used = used(fit, width, hasLoopCards, isFocusMode)
                            assertTrue(used <= used(it, width, hasLoopCards, isFocusMode), "$what: uses $used")
                        }
                        previous = fit
                    }
                }
            }
        }
    }

    @Test
    fun `the loading bar and the export banner above the cards take their height from the fit`() {
        val banner = 52.dp
        listOf(560.dp, 637.dp, 700.dp, 760.dp, 900.dp).forEach { height ->
            val fit = PlayerLayoutFit.of(WIDE, height, isFocusMode = false, hasLoopCards = true, extraTop = banner)

            assertEquals(PlayerLayoutFit.of(WIDE, height - banner, isFocusMode = false, hasLoopCards = true), fit)
            assertTrue(fit.overflows || used(fit, WIDE, hasLoopCards = true) + banner <= height, "$height: $fit does not fit")
        }
    }

    @Test
    fun `without the banner's height the cards would scroll under an export`() {
        // The design-size window under the Windows frame, and the export banner's measured height.
        val height = 772.dp
        val banner = 84.dp

        assertTrue(used(PlayerLayoutFit.of(WIDE, height, isFocusMode = false, hasLoopCards = true), WIDE, hasLoopCards = true) + banner > height)
        val fit = PlayerLayoutFit.of(WIDE, height, isFocusMode = false, hasLoopCards = true, extraTop = banner)
        assertFalse(fit.overflows)
        assertTrue(used(fit, WIDE, hasLoopCards = true) + banner <= height)
    }

    /** The height of everything but the overview, with nothing compressed. */
    private fun fullRest(hasLoopCards: Boolean) =
        parts.header + parts.cards + (if (hasLoopCards) parts.loopCards else 0.dp) + parts.chips + parts.zoom + parts.transport

    private fun used(fit: PlayerLayoutFit, width: Dp, hasLoopCards: Boolean, isFocusMode: Boolean = false): Dp {
        val chrome = (if (fit.compactChips) parts.chipsCompact else parts.chips) + fit.zoomHeight + fit.overviewHeight +
            (if (fit.compactTransport) parts.transportCompact else parts.transport)
        if (isFocusMode) return parts.focusHeader + chrome
        val loopCards = if (!hasLoopCards) 0.dp else (if (fit.compactLoopCards) parts.loopCardsCompact else parts.loopCards) +
            (if (width < parts.loopCardsOneLineWidth) parts.loopCardsWrap else 0.dp)
        return parts.header + (if (fit.compactCards) parts.cardsCompact else parts.cards) + loopCards + chrome
    }

    private companion object {
        val WIDE = 1280.dp
        val NARROW = 1000.dp
    }
}
