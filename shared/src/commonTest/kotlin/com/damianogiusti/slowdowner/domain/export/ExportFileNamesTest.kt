package com.damianogiusti.slowdowner.domain.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportFileNamesTest {

    @Test
    fun `a loop file is the track, the loop's number and its name`() {
        assertEquals("Song - 03 Solo.mp3", ExportFileNames.loop("Song", 3, 5, "Solo"))
    }

    @Test
    fun `the number has two digits, or three past 99 loops`() {
        assertEquals("Song - 01 A.mp3", ExportFileNames.loop("Song", 1, 1, "A"))
        assertEquals("Song - 99 A.mp3", ExportFileNames.loop("Song", 99, 99, "A"))
        assertEquals("Song - 007 A.mp3", ExportFileNames.loop("Song", 7, 100, "A"))
        assertEquals("Song - 100 A.mp3", ExportFileNames.loop("Song", 100, 100, "A"))
    }

    @Test
    fun `characters that are not allowed in file names are replaced in the track and the loop`() {
        assertEquals("AC_DC_ Live - 01 Verse _ Chorus_.mp3", ExportFileNames.loop("AC/DC: Live", 1, 1, "Verse / Chorus?"))
        assertEquals("a_b_c_d_e_f - 01 x.mp3", ExportFileNames.loop("a<b>c|d\"e*f", 1, 1, "x"))
    }

    @Test
    fun `line breaks and runs of spaces become one space`() {
        assertEquals("Song - 01 Bridge part 2.mp3", ExportFileNames.loop("Song", 1, 1, "Bridge\n  part\t2"))
    }

    @Test
    fun `names are trimmed and never end in a dot`() {
        assertEquals("Song - 01 Outro.mp3", ExportFileNames.loop("  Song  ", 1, 1, " Outro... "))
    }

    @Test
    fun `an empty title or loop name falls back to a placeholder`() {
        assertEquals("audio - 01 Loop.mp3", ExportFileNames.loop(" ", 1, 1, "   "))
        assertEquals("audio - 01 Loop.mp3", ExportFileNames.loop("", 1, 1, "..."))
    }

    @Test
    fun `long titles and loop names are capped`() {
        val name = ExportFileNames.loop("t".repeat(300), 1, 1, "n".repeat(100))

        assertEquals("t".repeat(ExportFileNames.MAX_TITLE_LENGTH) + " - 01 " + "n".repeat(40) + ".mp3", name)
        assertTrue(name.length < 255)
    }

    @Test
    fun `repeated names get a number before the extension, ignoring case`() {
        assertEquals(
            listOf("A.mp3", "a (2).mp3", "B.mp3", "A (3).mp3"),
            ExportFileNames.deduplicate(listOf("A.mp3", "a.mp3", "B.mp3", "A.mp3")),
        )
    }

    @Test
    fun `distinct names are kept as they are`() {
        val names = listOf("Song 100%.mp3", "Song - 01 Intro.mp3", "Song - 02 Intro.mp3")

        assertEquals(names, ExportFileNames.deduplicate(names))
    }
}
