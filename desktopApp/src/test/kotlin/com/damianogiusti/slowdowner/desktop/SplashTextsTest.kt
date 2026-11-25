package com.damianogiusti.slowdowner.desktop

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplashTextsTest {

    @Test
    fun `the splash lines load from the app resources and all end with an ellipsis`() = runTest {
        val texts = SplashTexts.load()

        assertEquals(10, texts.lines.size)
        assertEquals(texts.lines.size, texts.lines.toSet().size)
        texts.lines.forEach { line -> assertTrue(line.endsWith("…"), "\"$line\" should end with …") }
    }

    @Test
    fun `the splash uses the app's font`() = runTest {
        assertEquals("Inter", SplashTexts.load().font.family)
    }
}
