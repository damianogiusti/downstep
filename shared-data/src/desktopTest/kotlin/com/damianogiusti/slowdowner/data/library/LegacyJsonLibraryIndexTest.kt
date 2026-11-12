package com.damianogiusti.slowdowner.data.library

import com.damianogiusti.slowdowner.fake.RecordingLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class LegacyJsonLibraryIndexTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val logger = RecordingLogger()
    private val index by lazy { LegacyJsonLibraryIndex(folder.root, logger) }

    @Test
    fun `when the index lists existing files then read returns them`() {
        val audio = folder.newFile("yt_abc.m4a")
        writeIndex("""[{"id":"yt_abc","title":"Song","filePath":${JsonPrimitive(audio.path)},"sourceUrl":"https://y","addedAt":5}]""")

        assertEquals(listOf(LibraryTrackDto("yt_abc", "Song", audio.path, "https://y", 5)), index.read())
    }

    @Test
    fun `when an entry has an empty source url then it is read as null`() {
        val audio = folder.newFile("local_1.mp3")
        writeIndex("""[{"id":"local_1","title":"Mine","filePath":${JsonPrimitive(audio.path)},"sourceUrl":"","addedAt":1}]""")

        assertEquals(null, index.read().single().sourceUrl)
    }

    @Test
    fun `when an entry points to a missing file then it is skipped`() {
        writeIndex("""[{"id":"gone","title":"Gone","filePath":${JsonPrimitive(File(folder.root, "gone.m4a").path)},"addedAt":1}]""")

        assertEquals(emptyList<LibraryTrackDto>(), index.read())
    }

    @Test
    fun `when the index is corrupt then read returns nothing and reports the error`() {
        writeIndex("not json")

        assertEquals(emptyList<LibraryTrackDto>(), index.read())
        assertEquals(1, logger.at('E').size)
    }

    @Test
    fun `when retired then the index is kept as a backup and no longer exists`() {
        writeIndex("[]")

        index.retire()

        assertFalse(index.exists())
        assertTrue(File(folder.root, "index.json.imported").exists())
    }

    private fun writeIndex(json: String) = File(folder.root, "index.json").writeText(json)
}
