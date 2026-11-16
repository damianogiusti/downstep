package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.fake.FakeStemsDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class RoomStemsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dao = FakeStemsDao()
    private val stemsDir by lazy { folder.newFolder("stems") }
    private val store by lazy { RoomStemsStore(dao, stemsDir) }

    private val song = TrackStemsDto(
        "a", "model", "STEMS",
        listOf(StemFileDto("VOCALS", "vocals.wav", 1f), StemFileDto("DRUMS", "drums.wav", 0.5f)),
    )

    @Test
    fun `when stems are added then they are observed with their files and gains`() = runTest {
        store.add(song)

        val stored = store.observe("a").first()
        assertEquals(song.copy(stems = song.stems.sortedBy { it.stem }), stored?.copy(stems = stored.stems.sortedBy { it.stem }))
        assertEquals(listOf("a"), store.observeSeparatedTrackIds().first())
    }

    @Test
    fun `when stems are added again then the earlier ones are replaced`() = runTest {
        store.add(song)
        store.add(song.copy(model = "other", stems = listOf(StemFileDto("BASS", "bass.wav", 1f))))

        val stored = store.observe("a").first()
        assertEquals("other", stored?.model)
        assertEquals(listOf("BASS"), stored?.stems?.map { it.stem })
    }

    @Test
    fun `when the mode is set then it is stored`() = runTest {
        store.add(song)

        store.setMode("a", "FULL_TRACK")

        assertEquals("FULL_TRACK", store.observe("a").first()?.mode)
    }

    @Test
    fun `when a gain is set then only that stem changes`() = runTest {
        store.add(song)

        store.setGain("a", "DRUMS", 0.1f)

        assertEquals(mapOf("VOCALS" to 1f, "DRUMS" to 0.1f), store.observe("a").first()?.stems?.associate { it.stem to it.gain })
    }

    @Test
    fun `when stems are deleted then the rows and the files go`() = runTest {
        store.add(song)
        val dir = store.dirFor("a")
        File(dir, "vocals.wav").writeText("x")
        File(dir, "drums.wav").writeText("x")

        store.delete("a")

        assertNull(store.observe("a").first())
        assertFalse(dir.exists())
        assertTrue(stemsDir.exists())
    }

    @Test
    fun `when another track has files then deleting leaves them`() = runTest {
        val other = File(store.dirFor("b"), "vocals.wav").apply { writeText("x") }
        store.dirFor("a")

        store.delete("a")

        assertTrue(other.exists())
    }

    @Test
    fun `when the folder has a subfolder then it is kept with its content`() = runTest {
        val dir = store.dirFor("a")
        val nested = File(dir, "nested").apply { mkdirs() }
        val inside = File(nested, "keep.txt").apply { writeText("x") }
        val direct = File(dir, "vocals.wav").apply { writeText("x") }

        store.delete("a")

        assertTrue(inside.exists())
        assertFalse(direct.exists())
    }

    @Test
    fun `when the folder holds a link then the link is not followed`() = runTest {
        val outside = folder.newFolder("outside")
        val target = File(outside, "precious.txt").apply { writeText("x") }
        val dir = store.dirFor("a")
        try {
            Files.createSymbolicLink(File(dir, "link.wav").toPath(), target.toPath())
        } catch (e: UnsupportedOperationException) {
            assumeTrue("no symbolic links here", false)
        }

        store.delete("a")

        assertTrue(target.exists())
    }

    @Test
    fun `when the track folder is a link then nothing behind it is deleted`() = runTest {
        val outside = folder.newFolder("outside")
        val target = File(outside, "precious.txt").apply { writeText("x") }
        try {
            Files.createSymbolicLink(File(stemsDir, "a").toPath(), outside.toPath())
        } catch (e: UnsupportedOperationException) {
            assumeTrue("no symbolic links here", false)
        }

        store.delete("a")

        assertTrue(target.exists())
    }

    @Test
    fun `when there is no folder then deleting does not fail`() = runTest {
        store.delete("never-separated")
    }

    @Test
    fun `when the id could leave the stems folder then it is rejected`() = runTest {
        for (id in listOf("", ".", "..", "../x", "a/b", "a\\b", "x..y", "a\u0000b")) {
            try {
                store.dirFor(id)
                fail("accepted '$id'")
            } catch (e: IllegalArgumentException) {
                // expected
            }
            try {
                store.delete(id)
                fail("accepted '$id'")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `when a normal track id is used then the folder is inside the stems folder`() {
        assertEquals(File(stemsDir, "yt_a-B_1"), store.dirFor("yt_a-B_1"))
        assertTrue(File(stemsDir, "yt_a-B_1").isDirectory)
    }
}
