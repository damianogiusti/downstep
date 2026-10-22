package com.damianogiusti.slowdowner.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioFilesTest {

    @Test
    fun `when the file is audio then it is importable whatever the letter case`() {
        assertTrue(AudioFiles.isImportable(File("Autumn Leaves.MP3")))
        assertTrue(AudioFiles.isImportable(File("solo.flac")))
    }

    @Test
    fun `when a browser saved the download as mpeg then it is importable`() {
        assertTrue(AudioFiles.isImportable(File("track.mpeg")))
    }

    @Test
    fun `when the file is not audio then it is not importable`() {
        assertFalse(AudioFiles.isImportable(File("guest.igvm")))
        assertFalse(AudioFiles.isImportable(File("notes.txt")))
        assertFalse(AudioFiles.isImportable(File("no-extension")))
    }

    @Test
    fun `the Windows pattern lists every extension`() {
        assertEquals("*.mp3;*.wav", AudioFiles.windowsPattern(linkedSetOf("mp3", "wav")))
    }

    @Test
    fun `the logged extension is short and never empty`() {
        assertEquals("igvm", AudioFiles.logExtension(File("guest.IGVM")))
        assertEquals("backupan", AudioFiles.logExtension(File("song.backupanna")))
        assertEquals("none", AudioFiles.logExtension(File("no-extension")))
    }
}
