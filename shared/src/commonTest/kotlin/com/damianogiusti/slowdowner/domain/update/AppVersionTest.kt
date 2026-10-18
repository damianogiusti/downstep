package com.damianogiusti.slowdowner.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `when a version has the dev marker then it is a development build`() {
        val dev = version("1.1.1-dev+3f2a9c1")

        assertTrue(dev.isDevelopment)
        assertEquals("1.1.1-dev+3f2a9c1", dev.toString())
        assertTrue(version("1.1.1-dev").isDevelopment)
        assertFalse(version("v1.1.1").isDevelopment)
    }

    @Test
    fun `when a dev build is compared then only its numbers count`() {
        assertEquals(version("1.1.1"), version("1.1.1-dev+3f2a9c1"))
        assertTrue(version("1.1.2") > version("1.1.1-dev+3f2a9c1"))
        assertTrue(version("1.1.1-dev") > version("1.1.0"))
    }

    @Test
    fun `when the suffix is not the dev marker then parse is null`() {
        assertNull(AppVersion.parse("1.1.1-beta"))
        assertNull(AppVersion.parse("1.1.1+3f2a9c1"))
        assertNull(AppVersion.parse("1.1.1-dev+"))
        assertNull(AppVersion.parse("-dev"))
    }

    private fun version(text: String) = requireNotNull(AppVersion.parse(text))
}
