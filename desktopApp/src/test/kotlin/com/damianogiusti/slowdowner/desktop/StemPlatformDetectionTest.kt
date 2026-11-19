package com.damianogiusti.slowdowner.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StemPlatformDetectionTest {

    @Test
    fun `an Intel Mac is deprecated`() {
        assertTrue(detectStemPlatform("Mac OS X", "x86_64").isDeprecated)
        assertTrue(detectStemPlatform("Mac OS X", "amd64").isDeprecated)
    }

    @Test
    fun `an Apple silicon Mac is not deprecated`() {
        assertFalse(detectStemPlatform("Mac OS X", "aarch64").isDeprecated)
    }

    @Test
    fun `Windows is not deprecated on either CPU`() {
        assertFalse(detectStemPlatform("Windows 11", "amd64").isDeprecated)
        assertFalse(detectStemPlatform("Windows 11", "aarch64").isDeprecated)
    }

    @Test
    fun `missing properties are not deprecated`() {
        assertFalse(detectStemPlatform(null, null).isDeprecated)
    }
}
