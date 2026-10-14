package com.damianogiusti.slowdowner.data.playlists

import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.fake.FakePlaylistDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPlaylistRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tracks by lazy {
        listOf("a", "b", "c").map { TrackEntity(it, it.uppercase(), folder.newFile("$it.m4a").path, null, 0) }
    }
    private val dao by lazy { FakePlaylistDao(tracks) }
    private var time = 10L
    // The combine in observePlaylist yields: the dispatcher must share runTest's scheduler.
    private val TestScope.repository
        get() = DefaultPlaylistRepository(dao, UnconfinedTestDispatcher(testScheduler), now = { time++ })

    @Test
    fun `when tracks are added then they keep the order they were added in`() = runTest {
        val id = repository.create("Jazz")
        repository.addTrack(id, "b")
        repository.addTrack(id, "a")

        assertEquals(listOf("b", "a"), repository.observePlaylist(id).first()?.tracks?.map { it.id })
    }

    @Test
    fun `when a track is added twice then it stays once in its first place`() = runTest {
        val id = repository.create("Jazz")
        repository.addTrack(id, "a")
        repository.addTrack(id, "b")
        repository.addTrack(id, "a")

        val playlist = repository.observePlaylist(id).first()
        assertEquals(listOf("a", "b"), playlist?.tracks?.map { it.id })
        assertEquals(2, playlist?.playlist?.trackCount)
    }

    @Test
    fun `when reordered then positions follow the new order`() = runTest {
        val id = repository.create("Jazz")
        listOf("a", "b", "c").forEach { repository.addTrack(id, it) }

        repository.reorder(id, listOf("c", "a", "b"))

        assertEquals(listOf("c", "a", "b"), repository.observePlaylist(id).first()?.tracks?.map { it.id })
    }

    @Test
    fun `when a playlist changes then it moves to the top of the list`() = runTest {
        val first = repository.create("First")
        repository.create("Second")

        repository.addTrack(first, "a")

        assertEquals(listOf("First", "Second"), repository.observePlaylists().first().map { it.name })
    }

    @Test
    fun `when a track file is missing then the playlist hides it`() = runTest {
        val id = repository.create("Jazz")
        repository.addTrack(id, "a")
        repository.addTrack(id, "b")
        tracks.first { it.id == "a" }.let { java.io.File(it.filePath).delete() }

        assertEquals(listOf("b"), repository.observePlaylist(id).first()?.tracks?.map { it.id })
    }

    @Test
    fun `when membership is observed then it lists the playlists holding the track`() = runTest {
        val jazz = repository.create("Jazz")
        val solos = repository.create("Solos")
        repository.create("Empty")
        repository.addTrack(jazz, "a")
        repository.addTrack(solos, "a")

        assertEquals(setOf(jazz, solos), repository.observePlaylistIdsContaining("a").first())
    }

    @Test
    fun `when the playlist was deleted meanwhile then adding a track reports it`() = runTest {
        val id = repository.create("Jazz")
        repository.delete(id)

        assertFalse(repository.addTrack(id, "a"))
    }

    @Test
    fun `when the track was deleted meanwhile then adding it reports it`() = runTest {
        val id = repository.create("Jazz")

        assertFalse(repository.addTrack(id, "gone"))
        assertTrue(repository.observePlaylist(id).first()?.tracks.orEmpty().isEmpty())
    }

    @Test
    fun `when deleted then the playlist is observed as null`() = runTest {
        val id = repository.create("Jazz")

        repository.delete(id)

        assertNull(repository.observePlaylist(id).first())
    }
}
