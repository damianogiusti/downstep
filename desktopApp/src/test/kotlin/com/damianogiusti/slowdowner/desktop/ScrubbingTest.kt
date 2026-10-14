package com.damianogiusti.slowdowner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

/** What CrashReporting removes from exception messages before they leave the computer. */
class ScrubbingTest {

    @Test
    fun `the home folder is replaced with a tilde`() {
        assertEquals(
            "Cannot read ~/Music/Take 3.mp3",
            scrubHome("Cannot read /Users/anna/Music/Take 3.mp3", home = "/Users/anna"),
        )
        assertEquals("C:\\nothing", scrubHome("C:\\nothing", home = ""))
    }

    @Test
    fun `a Windows home is replaced in every form`() {
        val home = "C:\\Users\\Anna"

        assertEquals("~\\Music\\a.mp3", scrubHome("C:\\Users\\Anna\\Music\\a.mp3", home))
        assertEquals("file:///~/Music/a.mp3", scrubHome("file:///c:/Users/anna/Music/a.mp3", home))
    }

    @Test
    fun `paths go away with the file name, spaces included`() {
        assertEquals(
            "<path> (No such file or directory)",
            scrubPaths("/Volumes/Band/Autumn Leaves.mp3 (No such file or directory)"),
        )
        assertEquals("Cannot move <path>", scrubPaths("Cannot move /a/My Song.m4a.staging to /a/My Song.m4a"))
        assertEquals("Access denied: <path>", scrubPaths("Access denied: D:\\Band\\Solo.wav"))
        assertEquals("Cannot open <path>", scrubPaths("Cannot open ~/Music/Take 3.mp3"))
        // Too much is better than too little: the closing quote goes too.
        assertEquals("Bad file '<path>", scrubPaths("Bad file 'file:///Users/anna/Song.mp3'"))
    }

    @Test
    fun `links and plain text are kept`() {
        val link = "https://www.youtube.com/watch?v=abc"

        assertEquals(link, scrubPaths(link))
        assertEquals("HTTP 403 for $link", scrubPaths("HTTP 403 for $link"))
        assertEquals("Unsupported backup version 3", scrubPaths("Unsupported backup version 3"))
    }
}
