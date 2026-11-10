package com.damianogiusti.slowdowner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class CarbonMenuCrashVersionTest {
    @Test
    fun `only macOS 14 before 14_2 crashes on a screen menu`() {
        val versions = listOf("14", "14.0", "14.1", "14.1.2", "14.2", "14.6.1", "13.6.1", "15.0", "26.1", "")
        assertEquals(
            listOf(true, true, true, true, false, false, false, false, false, false),
            versions.map(::isCarbonMenuCrashVersion),
        )
    }
}
