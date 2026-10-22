package com.damianogiusti.slowdowner.desktop

import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LaunchPropertiesTest {

    private fun properties(
        isNewInstallation: Boolean? = false,
        hasLaunchHistory: Boolean = true,
        region: Locale = Locale.ITALY,
        display: Locale = Locale.ITALIAN,
    ) = launchProperties(isNewInstallation, hasLaunchHistory, region, display, ZoneId.of("Europe/Rome"))

    @Test
    fun `country language and time zone come from the system settings`() {
        val properties = properties(region = Locale.ITALY, display = Locale.ENGLISH)

        assertEquals("IT", properties["country"])
        assertEquals("en", properties["language"])
        assertEquals("Europe/Rome", properties["timeZone"])
    }

    @Test
    fun `when the system has no region then the country is unknown`() {
        assertEquals("unknown", properties(region = Locale.ENGLISH)["country"])
    }

    @Test
    fun `when the id is new but the preferences have a history then the data folder was deleted`() {
        val properties = properties(isNewInstallation = true, hasLaunchHistory = true)

        assertEquals("new", properties["installation"])
        assertEquals("returning", properties["history"])
    }

    @Test
    fun `when the id and the history are both new then it is a first installation`() {
        val properties = properties(isNewInstallation = true, hasLaunchHistory = false)

        assertEquals("new", properties["installation"])
        assertEquals("fresh", properties["history"])
    }

    @Test
    fun `without crash reporting there is no installation property`() {
        assertFalse("installation" in properties(isNewInstallation = null))
    }
}
