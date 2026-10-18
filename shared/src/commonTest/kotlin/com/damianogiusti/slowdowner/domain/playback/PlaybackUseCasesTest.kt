package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeEngine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.Test

class PlaybackUseCasesTest {

    private val sources = FakeAudioSourceRepository()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val track = LibraryTrack("1", "Song", "/song.m4a", null, 0)

    @Test
    fun `open track loads its file and marks it as now playing`() = runTest {
        openTrack()(track)

        assertEquals(AudioSource.LocalFile("/song.m4a"), sources.current)
        assertEquals(track, nowPlaying.observe().value)
    }

    @Test
    fun `failed open clears now playing`() = runTest {
        nowPlaying.set(track)
        sources.openSucceeds = false

        assertFalse(openTrack()(track))
        assertNull(nowPlaying.observe().value)
    }

    private fun TestScope.openTrack() = OpenTrackUseCase(sources, nowPlaying, StandardTestDispatcher(testScheduler))

    @Test
    fun `end of track rewinds to the start`() {
        val engine = FakeEngine().apply { isPlaying = true; position = 1f }

        HandleEndOfTrackUseCase(engine)()

        assertFalse(engine.isPlaying)
        assertEquals(0f, engine.position)
    }

    @Test
    fun `end of track rewinds to the loop start when looping`() {
        val engine = FakeEngine().apply { loopEnabled = true; loopStart = 0.25f; position = 1f }

        HandleEndOfTrackUseCase(engine)()

        assertEquals(0.25f, engine.position)
    }
}
