package com.damianogiusti.slowdowner.data.library

import com.damianogiusti.slowdowner.fake.FakeTrackDao
import com.damianogiusti.slowdowner.fake.RecordingLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RoomLibraryStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dao = FakeTrackDao()
    private val logger = RecordingLogger()
    private val store by lazy {
        RoomLibraryStore(dao, LegacyJsonLibraryIndex(folder.root, logger), folder.root, logger)
    }

    @Test
    fun `when a legacy index exists then the first read imports it and retires the file`() = runTest {
        val audio = folder.newFile("yt_abc.m4a")
        writeLegacyIndex("""[{"id":"yt_abc","title":"Song","filePath":"${audio.path}","sourceUrl":"u","addedAt":5}]""")

        val tracks = store.observe().first()

        assertEquals(listOf("yt_abc"), tracks.map { it.id })
        assertFalse(File(folder.root, "index.json").exists())
        assertTrue(File(folder.root, "index.json.imported").exists())
    }

    @Test
    fun `when a legacy track is already in the database then the stored row wins`() = runTest {
        val audio = folder.newFile("yt_abc.m4a")
        dao.upsert(TrackEntity("yt_abc", "Renamed", audio.path, "u", 9))
        writeLegacyIndex("""[{"id":"yt_abc","title":"Song","filePath":"${audio.path}","sourceUrl":"u","addedAt":5}]""")

        assertEquals("Renamed", store.find("yt_abc")?.title)
    }

    @Test
    fun `when a row points to a missing file then observe hides it`() = runTest {
        dao.upsert(TrackEntity("gone", "Gone", "${folder.root}/gone.m4a", null, 1))

        assertEquals(emptyList<LibraryTrackDto>(), store.observe().first())
    }

    @Test
    fun `when a track is removed then its row and its file are deleted`() = runTest {
        val audio = folder.newFile("local_1.mp3")
        store.add(LibraryTrackDto("local_1", "Mine", audio.path, null, 1))

        store.remove("local_1")

        assertNull(dao.find("local_1"))
        assertFalse(audio.exists())
    }

    @Test
    fun `when a legacy track is removed before any read then the import does not bring it back`() = runTest {
        val audio = folder.newFile("yt_abc.m4a")
        writeLegacyIndex("""[{"id":"yt_abc","title":"Song","filePath":"${audio.path}","sourceUrl":"u","addedAt":5}]""")

        store.remove("yt_abc")

        assertEquals(emptyList<LibraryTrackDto>(), store.observe().first())
    }

    @Test
    fun `when the legacy index cannot be renamed then the import still completes and reports it`() = runTest {
        val audio = folder.newFile("yt_abc.m4a")
        writeLegacyIndex("""[{"id":"yt_abc","title":"Song","filePath":"${audio.path}","sourceUrl":"u","addedAt":5}]""")
        // A directory with the backup name makes the rename fail.
        File(folder.root, "index.json.imported").mkdir()
        File(folder.root, "index.json.imported/keep").writeText("x")

        val tracks = store.observe().first()

        assertEquals(listOf("yt_abc"), tracks.map { it.id })
        assertEquals(1, logger.at('W').size)
    }

    @Test
    fun `fileFor places the audio in the library folder`() {
        assertEquals(File(folder.root, "yt_abc.m4a"), store.fileFor("yt_abc", "m4a"))
    }

    private fun writeLegacyIndex(json: String) = File(folder.root, "index.json").writeText(json)
}
