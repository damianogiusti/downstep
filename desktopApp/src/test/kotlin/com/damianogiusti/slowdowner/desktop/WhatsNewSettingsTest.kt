package com.damianogiusti.slowdowner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsNewSettingsTest {

    @Test
    fun `when the app is not packaged then there is no version, so no popup and nothing recorded`() {
        assertNull(WhatsNewSettings.current("1.0.0", hasLaunchHistory = true, isPackaged = false).appVersion)
    }

    @Test
    fun `when the app is packaged then the version and the history are kept`() {
        val settings = WhatsNewSettings.current("1.3.0", hasLaunchHistory = true, isPackaged = true)

        assertEquals("1.3.0", settings.appVersion)
        assertEquals(true, settings.hasLaunchHistory)
    }
}
