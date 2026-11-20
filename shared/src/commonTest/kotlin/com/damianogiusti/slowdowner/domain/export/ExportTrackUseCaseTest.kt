package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeExportRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class ExportTrackUseCaseTest {

    private val engine = FakeEngine()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val repository = FakeExportRepository()
    private val exportTrack = ExportTrackUseCase(engine, nowPlaying, repository)

    private val track = LibraryTrack("1", "Song", "/1.m4a", null, 0)

    @Test
    fun `when no library track is open then nothing is exported`() = runTest {
        val result = exportTrack(ExportPart.WHOLE_TRACK) {}

        assertEquals(ExportResult.NothingOpen, result)
        assertNull(repository.exportedName)
    }

    @Test
    fun `when the engine is closed then nothing is exported`() = runTest {
        nowPlaying.set(track)
        engine.isOpen = false

        assertEquals(ExportResult.NothingOpen, exportTrack(ExportPart.WHOLE_TRACK) {})
    }

    @Test
    fun `when exporting during playback then playback stops first`() = runTest {
        nowPlaying.set(track)
        engine.isPlaying = true

        exportTrack(ExportPart.WHOLE_TRACK) {}

        assertFalse(engine.isPlaying)
        assertEquals("Song 100%.mp3", repository.exportedName)
    }

    @Test
    fun `the whole track is rendered from start to end`() = runTest {
        nowPlaying.set(track)
        engine.loopEnabled = true
        engine.loopStart = 0.2f
        engine.loopEnd = 0.4f

        exportTrack(ExportPart.WHOLE_TRACK) {}

        assertEquals(RenderRange.WHOLE_FILE, repository.exportedRange)
        assertEquals("Song 100%.mp3", repository.exportedName)
    }

    @Test
    fun `the loop is rendered alone and named after its place`() = runTest {
        nowPlaying.set(track)
        engine.durationSeconds = 200f
        engine.speed = 75f
        engine.loopEnabled = true
        engine.loopStart = 0.21f
        engine.loopEnd = 0.365f

        exportTrack(ExportPart.LOOP) {}

        assertEquals(RenderRange(0.21f, 0.365f), repository.exportedRange)
        assertEquals("Song 75% loop 0m42s-1m13s.mp3", repository.exportedName)
    }

    @Test
    fun `when the loop is off then the loop is not exported`() = runTest {
        nowPlaying.set(track)
        engine.isPlaying = true

        assertEquals(ExportResult.NoLoop, exportTrack(ExportPart.LOOP) {})
        assertNull(repository.exportedName)
        assertTrue(engine.isPlaying)
    }

    @Test
    fun `when the loop has no length then the loop is not exported`() = runTest {
        nowPlaying.set(track)
        engine.loopEnabled = true
        engine.loopStart = 0.4f
        engine.loopEnd = 0.4f

        assertEquals(ExportResult.NoLoop, exportTrack(ExportPart.LOOP) {})
        assertNull(repository.exportedName)
    }

    @Test
    fun `a range must be inside the file and not empty`() {
        assertFailsWith<IllegalArgumentException> { RenderRange(0.5f, 0.5f) }
        assertFailsWith<IllegalArgumentException> { RenderRange(-0.1f, 0.5f) }
        assertFailsWith<IllegalArgumentException> { RenderRange(0.2f, 1.1f) }
    }

    @Test
    fun `file name carries speed and a non-zero pitch`() {
        assertEquals("Song 57% +6st.mp3", ExportTrackUseCase.fileName("Song", 57.3f, 6f))
        assertEquals("Song 125% -2st.mp3", ExportTrackUseCase.fileName("Song", 125f, -2f))
        assertEquals("Song 75%.mp3", ExportTrackUseCase.fileName("Song", 75f, 0f))
    }

    @Test
    fun `file name shows cents with two decimals`() {
        assertEquals("Song 75% +2.15st.mp3", ExportTrackUseCase.fileName("Song", 75f, 2.15f))
        assertEquals("Song 75% -0.30st.mp3", ExportTrackUseCase.fileName("Song", 75f, -0.3f))
        assertEquals("Song 75% +0.05st.mp3", ExportTrackUseCase.fileName("Song", 75f, 0.05f))
        assertEquals("Song 75% -11.95st.mp3", ExportTrackUseCase.fileName("Song", 75f, -11.95f))
    }

    @Test
    fun `file name replaces characters that are not allowed in file names`() {
        assertEquals("AC_DC_ Live 100%.mp3", ExportTrackUseCase.fileName("AC/DC: Live", 100f, 0f))
        assertEquals("audio 100%.mp3", ExportTrackUseCase.fileName("  ", 100f, 0f))
    }
}
