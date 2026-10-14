package com.damianogiusti.slowdowner.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    @Test
    fun `when a tag has a v then it is a version`() {
        assertEquals("1.0.12", AppVersion.parse("v1.0.12").toString())
    }

    @Test
    fun `when text is not a version then parse is null`() {
        assertNull(AppVersion.parse("dev"))
        assertNull(AppVersion.parse("1.0.x"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("1..2"))
    }

    @Test
    fun `when parts are numbers then they compare as numbers, not text`() {
        assertTrue(version("1.0.10") > version("1.0.9"))
        assertTrue(version("1.1.0") > version("1.0.99"))
        assertTrue(version("2.0.0") > version("1.9.9"))
    }

    @Test
    fun `when parts are missing then they count as zero`() {
        assertEquals(version("1.0"), version("1.0.0"))
        assertEquals(version("1.0").hashCode(), version("1.0.0").hashCode())
        assertTrue(version("1.0.1") > version("1.0"))
    }

    private fun version(text: String) = requireNotNull(AppVersion.parse(text))
}
