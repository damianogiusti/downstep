package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeExportRepository
import com.damianogiusti.slowdowner.fake.FakeLibraryRepository
import com.damianogiusti.slowdowner.fake.FakePracticeRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateTrackFromLoopUseCaseTest {

    private val engine = FakeEngine().apply {
        speed = 75f
        pitch = -2f
        loopEnabled = true
        loopStart = 0.2f
        loopEnd = 0.4f
    }
    private val nowPlaying = InMemoryNowPlayingRepository().apply { set(LibraryTrack("1", "Song", "/1.m4a", null, 0)) }
    private val export = FakeExportRepository()
    private val library = FakeLibraryRepository()
    private val practice = FakePracticeRepository()
    private val createTrack = CreateTrackFromLoopUseCase(engine, nowPlaying, export, library, practice)

    @Test
    fun `the loop becomes a track with the original sound`() = runTest {
        val result = createTrack("  Solo  ") {}

        val created = assertIs<CreateTrackResult.Created>(result).track
        assertEquals("Solo", created.title)
        assertEquals(RenderRange(0.2f, 0.4f), export.originalRange)
        assertNull(export.exportedRange)
        assertEquals(listOf(created), library.tracks.value)
    }

    @Test
    fun `the new track starts with the speed and pitch of the source and no loop`() = runTest {
        val created = assertIs<CreateTrackResult.Created>(createTrack("Solo") {}).track

        assertEquals(PracticeSettings(75f, -2f, LoopRegion.WHOLE_FILE), practice.states.value[created.id])
    }

    @Test
    fun `the source keeps its loop`() = runTest {
        createTrack("Solo") {}

        assertTrue(engine.isLoopOn)
        assertEquals(0.2f, engine.loopStart)
        assertTrue(engine.practiceCalls.isEmpty())
    }

    @Test
    fun `when creating during playback then playback stops first`() = runTest {
        engine.isPlaying = true

        createTrack("Solo") {}

        assertFalse(engine.isPlaying)
    }

    @Test
    fun `when the loop is off then nothing is rendered`() = runTest {
        engine.loopEnabled = false

        assertEquals(CreateTrackResult.NoLoop, createTrack("Solo") {})
        assertNull(export.originalRange)
    }

    @Test
    fun `when no library track is open then nothing is rendered`() = runTest {
        nowPlaying.set(null)

        assertEquals(CreateTrackResult.NothingOpen, createTrack("Solo") {})
        assertNull(export.originalRange)
    }

    @Test
    fun `when the title is blank then nothing is rendered`() = runTest {
        assertEquals(CreateTrackResult.InvalidTitle, createTrack("   ") {})
        assertNull(export.originalRange)
    }

    @Test
    fun `when the render fails then no track is added`() = runTest {
        val cause = IllegalStateException("disk full")
        export.result = ExportResult.Failure(cause)

        assertEquals(CreateTrackResult.Failure(cause), createTrack("Solo") {})
        assertTrue(library.tracks.value.isEmpty())
        assertTrue(practice.savedTrackIds.isEmpty())
    }

    @Test
    fun `when the library refuses the file then no settings are saved`() = runTest {
        val cause = IllegalStateException("database")
        library.addResult = ImportResult.Failure(cause)

        assertEquals(CreateTrackResult.Failure(cause), createTrack("Solo") {})
        assertTrue(practice.savedTrackIds.isEmpty())
    }
}
