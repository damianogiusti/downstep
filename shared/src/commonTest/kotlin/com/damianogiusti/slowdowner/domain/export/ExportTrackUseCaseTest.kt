package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeLoopRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ExportTrackUseCaseTest {

    private val engine = FakeEngine()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val loops = FakeLoopRepository(existingTrackIds = setOf("1"))
    private val repository = RecordingExportRepository()
    private val exportTrack = ExportTrackUseCase(engine, nowPlaying, loops, repository)

    private val track = LibraryTrack("1", "Song", "/1.m4a", null, 0)

    @Test
    fun `when no library track is open then nothing is exported`() = runTest {
        val result = exportTrack {}

        assertEquals(ExportResult.NothingOpen, result)
        assertNull(repository.items)
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
        assertEquals(listOf(ExportItem("Song 100%.mp3")), repository.items)
    }

    @Test
    fun `when the scope is the full track then the loops are left out`() = runTest {
        nowPlaying.set(track)
        loops.create("1", "Intro", LoopBounds(0.1f, 0.2f))

        exportTrack(ExportScope.FULL_TRACK) {}

        assertEquals(listOf(ExportItem("Song 100%.mp3")), repository.items)
    }

    @Test
    fun `when the scope is the loops then each loop is a slice in the track's order`() = runTest {
        nowPlaying.set(track)
        engine.setSpeedPercent(75f)
        loops.create("1", "Solo", LoopBounds(0.6f, 0.8f))
        loops.create("1", "Intro", LoopBounds(0.0f, 0.1f))

        exportTrack(ExportScope.LOOPS) {}

        assertEquals(
            listOf(ExportItem("Song - 01 Intro.mp3", 0.0f, 0.1f), ExportItem("Song - 02 Solo.mp3", 0.6f, 0.8f)),
            repository.items,
        )
    }

    @Test
    fun `when the scope is both then the full track comes first and the loops follow`() = runTest {
        nowPlaying.set(track)
        loops.create("1", "Verse", LoopBounds(0.2f, 0.4f))

        exportTrack(ExportScope.BOTH) {}

        assertEquals(listOf(ExportItem("Song 100%.mp3"), ExportItem("Song - 01 Verse.mp3", 0.2f, 0.4f)), repository.items)
    }

    @Test
    fun `when only the loops are asked for and there are none then nothing is exported`() = runTest {
        nowPlaying.set(track)
        engine.isPlaying = true

        assertEquals(ExportResult.NoLoops, exportTrack(ExportScope.LOOPS) {})
        assertNull(repository.items)
        // Nothing to render: playback goes on.
        assertEquals(true, engine.isPlaying)
    }

    @Test
    fun `when both are asked for and there are no loops then the full track is exported alone`() = runTest {
        nowPlaying.set(track)

        exportTrack(ExportScope.BOTH) {}

        assertEquals(listOf(ExportItem("Song 100%.mp3")), repository.items)
    }

    @Test
    fun `when two loops share a name then their numbers keep the files apart`() = runTest {
        nowPlaying.set(track)
        loops.create("1", "Riff", LoopBounds(0.1f, 0.2f))
        loops.create("1", "Riff", LoopBounds(0.3f, 0.4f))

        exportTrack(ExportScope.LOOPS) {}

        assertEquals(listOf("Song - 01 Riff.mp3", "Song - 02 Riff.mp3"), repository.items?.map { it.fileName })
    }

    @Test
    fun `the scope counts the files a track gets`() {
        assertEquals(1, ExportScope.FULL_TRACK.fileCount(3))
        assertEquals(3, ExportScope.LOOPS.fileCount(3))
        assertEquals(4, ExportScope.BOTH.fileCount(3))
        assertEquals(0, ExportScope.LOOPS.fileCount(0))
        assertEquals(1, ExportScope.BOTH.fileCount(0))
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
        var items: List<ExportItem>? = null
        override suspend fun exportOpenTrack(items: List<ExportItem>, onProgress: (Float) -> Unit): ExportResult {
            this.items = items
            return ExportResult.Success(items.map { ExportedFile("/exports/${it.fileName}", it.fileName) })
        }
        override suspend fun saveTo(file: ExportedFile, contentUri: String) = error("unused")
        override suspend fun saveAllTo(files: List<ExportedFile>, folder: String) = error("unused")
    }
}
