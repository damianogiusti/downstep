package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.logging.ConsoleLogger
import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactTest {

    private val lines = mutableListOf<String>()
    private val logger = ConsoleLogger { lines += it }

    @Test
    fun `when the mail link is built then the subject uses percent encoding for spaces`() {
        val uri = contactMailUri("1.2.3", "macOS", address = "a@b.c")

        assertEquals("mailto:a@b.c?subject=Downstep%201.2.3%20%28macOS%29", uri.toString())
    }

    @Test
    fun `when the address is built then its parts are joined with an at sign`() {
        assertEquals(listOf("contact", "downstep.app"), CONTACT_ADDRESS.split("@"))
    }

    @Test
    fun `when contact is opened then the source is logged`() {
        ContactActions(logger, "1.0").opened(ContactSource.MAC_MENU)

        assertEquals(listOf("I/Contact opened {source=mac_menu}"), lines)
    }

    @Test
    fun `when the address is copied then it reaches the clipboard and the use is logged`() {
        var copied: String? = null

        ContactActions(logger, "1.0", copy = { copied = it }).copyAddress()

        assertEquals(CONTACT_ADDRESS, copied)
        assertEquals(listOf("I/Contact used {action=copy}"), lines)
    }

    @Test
    fun `when the clipboard fails then it is a warning, not a crash`() {
        ContactActions(logger, "1.0", copy = { throw IllegalStateException("busy") }).copyAddress()

        assertTrue(lines.last().startsWith("W/Cannot copy the contact address"), lines.last())
    }

    @Test
    fun `when the mail app opens then writeMail is true and the mailto goes to the address`() {
        var opened: URI? = null

        val result = ContactActions(logger, "1.0", mail = { opened = it }).writeMail()

        assertTrue(result)
        assertEquals("mailto", opened?.scheme)
        assertTrue(opened.toString().startsWith("mailto:$CONTACT_ADDRESS?subject=Downstep%201.0%20%28"), opened.toString())
        assertEquals(listOf("I/Contact used {action=mail}"), lines)
    }

    @Test
    fun `when no mail app can open then writeMail is false and a warning is logged`() {
        val result = ContactActions(logger, "1.0", mail = { throw IOException("no mail app") }).writeMail()

        assertFalse(result)
        assertTrue(lines.last().startsWith("W/Cannot open the mail app"), lines.last())
    }
}
