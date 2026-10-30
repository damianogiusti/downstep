package com.damianogiusti.slowdowner.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    private val install = "For Mac with Apple silicon (macOS 11 or later) and for Windows 10 and 11 (64 bit)."

    @Test
    fun `the changes are the list before the separator`() {
        val body = "\n- Normalize volume: every song plays at a similar level.\n- Bug fixes and small improvements.\n\n---\n\n$install\n"

        assertEquals(
            listOf("Normalize volume: every song plays at a similar level.", "Bug fixes and small improvements."),
            ReleaseNotes.changes(body),
        )
    }

    @Test
    fun `when the notes have no separator then there are no changes to show`() {
        // The releases before 1.2.0: only the install instructions.
        assertTrue(ReleaseNotes.changes(install).isEmpty())
        assertTrue(ReleaseNotes.changes(null).isEmpty())
    }

    @Test
    fun `when GitHub returns Windows line endings then the changes are the same`() {
        val body = "- One\r\n- Two\r\n\r\n---\r\n\r\n$install"

        assertEquals(listOf("One", "Two"), ReleaseNotes.changes(body))
    }

    @Test
    fun `headings and paragraphs are left out`() {
        val body = "## 1.2.0\n\nSome words.\n* One\n\n---\n"

        assertEquals(listOf("One"), ReleaseNotes.changes(body))
    }

    @Test
    fun `links and bold become plain text but a lone underscore stays`() {
        val body = "- **Loops** see [the guide](https://downstep.app/guides)\n- Keeps file_name as is\n---\n"

        assertEquals(listOf("Loops see the guide", "Keeps file_name as is"), ReleaseNotes.changes(body))
    }

    @Test
    fun `a long list is cut so the dialog keeps its buttons on screen`() {
        val body = (1..12).joinToString("\n") { "- Change $it" } + "\n---\n"

        assertEquals(8, ReleaseNotes.changes(body).size)
    }
}
