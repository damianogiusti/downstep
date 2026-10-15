package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.logging.ConsoleLogger
import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DonationTest {

    private val lines = mutableListOf<String>()
    private val logger = ConsoleLogger { lines += it }

    @Test
    fun `when donate is opened then PayPal opens and the source is logged`() {
        var opened: URI? = null

        openDonation(DonateSource.MAC_MENU, logger) { opened = it }

        assertEquals(URI("https://www.paypal.me/damianogiusti"), opened)
        assertEquals(listOf("I/Donate opened {source=mac_menu}"), lines)
    }

    @Test
    fun `when the browser cannot open then it is a warning, not a crash`() {
        openDonation(DonateSource.LIBRARY_MENU, logger) { throw IOException("no browser") }

        assertEquals("I/Donate opened {source=library_menu}", lines.first())
        assertTrue(lines.last().startsWith("W/Cannot open the donation page"), lines.last())
    }
}
