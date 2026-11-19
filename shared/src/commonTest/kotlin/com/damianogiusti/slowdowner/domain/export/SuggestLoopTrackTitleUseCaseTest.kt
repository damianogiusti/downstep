package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.fake.FakeEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuggestLoopTrackTitleUseCaseTest {

    private val engine = FakeEngine().apply {
        durationSeconds = 4000f
        loopEnabled = true
        loopStart = 0.0105f
        loopEnd = 0.01825f
    }
    private val nowPlaying = InMemoryNowPlayingRepository().apply { set(LibraryTrack("1", "Song", "/1.m4a", null, 0)) }
    private val suggest = SuggestLoopTrackTitleUseCase(engine, nowPlaying)

    @Test
    fun `the title says where the loop is in the source`() {
        assertEquals("Song (loop 0:42–1:13)", suggest())
    }

    @Test
    fun `minutes go past sixty`() {
        engine.loopStart = 0.9f
        engine.loopEnd = 1f

        assertEquals("Song (loop 60:00–66:40)", suggest())
    }

    @Test
    fun `when the loop is off then there is no title`() {
        engine.loopEnabled = false

        assertNull(suggest())
    }

    @Test
    fun `when no library track is open then there is no title`() {
        nowPlaying.set(null)

        assertNull(suggest())
    }
}
