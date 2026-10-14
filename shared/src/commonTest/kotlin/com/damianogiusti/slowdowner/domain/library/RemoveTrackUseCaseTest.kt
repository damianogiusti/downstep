package com.damianogiusti.slowdowner.domain.library

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.CloseTrackUseCase
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class RemoveTrackUseCaseTest {

    private val sources = FakeAudioSourceRepository()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val library = RecordingLibraryRepository()
    private val removeTrack = RemoveTrackUseCase(library, nowPlaying, CloseTrackUseCase(sources, nowPlaying))

    private val playing = LibraryTrack("1", "Playing", "/1.m4a", null, 0)
    private val other = LibraryTrack("2", "Other", "/2.m4a", null, 0)

    @Test
    fun `removing the open track closes it first`() = runTest {
        nowPlaying.set(playing)

        removeTrack(playing)

        assertEquals(1, sources.closed)
        assertNull(nowPlaying.observe().value)
        assertEquals(listOf("1"), library.removed)
    }

    @Test
    fun `removing another track leaves playback alone`() = runTest {
        nowPlaying.set(playing)

        removeTrack(other)

        assertEquals(0, sources.closed)
        assertEquals(playing, nowPlaying.observe().value)
        assertEquals(listOf("2"), library.removed)
    }

    private class RecordingLibraryRepository : LibraryRepository {
        val removed = mutableListOf<String>()
        override fun observeTracks(): Flow<List<LibraryTrack>> = emptyFlow()
        override suspend fun remove(id: String) { removed += id }
        override fun findImportableUrl(text: String?) = null
        override suspend fun importFromUrl(url: String, onProgress: (ImportProgress) -> Unit) =
            error("unused")
        override suspend fun importDocument(contentUri: String) = error("unused")
    }
}
