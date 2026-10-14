package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.fake.FakeEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.Test

class ExportTrackUseCaseTest {

    private val engine = FakeEngine()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val repository = RecordingExportRepository()
    private val exportTrack = ExportTrackUseCase(engine, nowPlaying, repository)

    private val track = LibraryTrack("1", "Song", "/1.m4a", null, 0)

    @Test
    fun `when no library track is open then nothing is exported`() = runTest {
        val result = exportTrack {}

        assertEquals(ExportResult.NothingOpen, result)
        assertNull(repository.exportedName)
    }

    @Test
    fun `when the engine is closed then nothing is exported`() = runTest {
        nowPlaying.set(track)
        engine.isOpen = false

        assertEquals(ExportResult.NothingOpen, exportTrack {})
    }

    @Test
    fun `when exporting during playback then playback stops first`() = runTest {
        nowPlaying.set(track)
        engine.isPlaying = true

        exportTrack {}

        assertFalse(engine.isPlaying)
        assertEquals("Song 100%.mp3", repository.exportedName)
    }

    @Test
    fun `file name carries speed and a non-zero pitch`() {
        assertEquals("Song 57% +6st.mp3", ExportTrackUseCase.fileName("Song", 57.3f, 6f))
        assertEquals("Song 125% -2st.mp3", ExportTrackUseCase.fileName("Song", 125f, -2f))
        assertEquals("Song 75%.mp3", ExportTrackUseCase.fileName("Song", 75f, 0.2f))
    }

    @Test
    fun `file name replaces characters that are not allowed in file names`() {
        assertEquals("AC_DC_ Live 100%.mp3", ExportTrackUseCase.fileName("AC/DC: Live", 100f, 0f))
        assertEquals("audio 100%.mp3", ExportTrackUseCase.fileName("  ", 100f, 0f))
    }

    private class RecordingExportRepository : ExportRepository {
        var exportedName: String? = null
        override suspend fun exportOpenTrack(fileName: String, onProgress: (Float) -> Unit): ExportResult {
            exportedName = fileName
            return ExportResult.Success(ExportedFile("/exports/$fileName", fileName))
        }
        override suspend fun saveTo(file: ExportedFile, contentUri: String) = error("unused")
    }
}
