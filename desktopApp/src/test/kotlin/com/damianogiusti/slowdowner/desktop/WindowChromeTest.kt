package com.damianogiusti.slowdowner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowChromeTest {

    @Test
    fun `the app icon loads from the SVG at every window icon size`() {
        val painter = appIconPainter()
        assertEquals(1024f, painter.intrinsicSize.width)

        val images = appIconImages(painter)
        assertEquals(listOf(16, 20, 24, 32, 40, 48, 64, 128, 256), images.map { it.width })
        // The mark in the middle: something was drawn, not a blank square.
        val big = images.last()
        val centre = big.getRGB(big.width / 2, big.height / 2)
        assertTrue(centre ushr 24 != 0, "transparent centre")
    }
}
