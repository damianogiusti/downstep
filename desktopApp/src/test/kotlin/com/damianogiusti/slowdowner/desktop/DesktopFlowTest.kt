package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.domain.backup.BackupExportResult
import com.damianogiusti.slowdowner.domain.backup.ExportLibraryBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.PlaylistConflictChoice
import com.damianogiusti.slowdowner.domain.library.ImportDocumentUseCase
import com.damianogiusti.slowdowner.domain.playlists.AddTrackToPlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.CreatePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.ObservePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.ObservePlaylistsUseCase
import com.damianogiusti.slowdowner.domain.playlists.PlaylistResult
import com.damianogiusti.slowdowner.domain.playlists.RemoveTrackFromPlaylistUseCase
import com.damianogiusti.slowdowner.ui.backup.BackupUiState
import com.damianogiusti.slowdowner.ui.backup.BackupViewModel
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.library.ImportTrackFromUrlUseCase
import com.damianogiusti.slowdowner.domain.library.ObserveLibraryUseCase
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.practice.OpenTrackForPracticeUseCase
import androidx.compose.ui.input.key.Key
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.data.effects.EffectsSettingsDto
import com.damianogiusti.slowdowner.domain.effects.EffectsEngine
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import com.damianogiusti.slowdowner.domain.practice.ObservePracticeStatesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import javax.swing.SwingUtilities
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The desktop app's real chain, without the UI: import → library (Room) → open → play (C engine). */
class DesktopFlowTest {

    private val directories = AppDirectories(Files.createTempDirectory("slowdowner-flow").toFile())
    private val native = NativeEngine.manual(44_100)
    private val effectsStore = InMemoryEffectsSettingsStore()
    private val supportPromptStore = InMemorySupportPromptStore()
    private val koin = startKoin {
        modules(desktopModule(directories, native, effectsStore, supportPromptStore), useCasesModule, uiModule)
    }.koin

    @AfterTest
    fun tearDown() {
        koin.get<DesktopPlaybackEngine>().close()
        stopKoin()
        directories.root.deleteRecursively()
    }

    @Test
    fun `an imported file plays from the library`() = runBlocking<Unit> {
        val source = toneFile()
        val imported = koin.get<ImportDocumentUseCase>()(source.path)
        assertIs<ImportResult.Success>(imported)
        assertEquals("tone", imported.track.title)
        assertTrue(File(imported.track.filePath).exists(), "the audio is copied into the library")

        val library = koin.get<ObserveLibraryUseCase>()().first { it.isNotEmpty() }
        assertEquals(listOf(imported.track.id), library.map { it.id })

        assertTrue(koin.get<OpenTrackForPracticeUseCase>()(library.single()))
        val engine = koin.get<PlaybackEngine>()
        assertEquals(10f, engine.durationSeconds, 0.01f)
        engine.setSpeedPercent(50f)
        engine.togglePlayStop()
        native.render(44_100 * 4)
        assertTrue(abs(engine.position - 0.2f) < 0.01f, "position ${engine.position}")
        source.delete()
    }

    @Test
    fun `at start the saved effects reach the engine`() = runBlocking<Unit> {
        effectsStore.write(EffectsSettingsDto(eqEnabled = true, preampDb = -3f, bandGainsDb = List(7) { 2f }, qualityName = "LOW"))
        val scope = CoroutineScope(Dispatchers.Default)
        startAppWork(koin, scope)
        waitUntil { koin.get<EffectsEngine>().quality == ProcessingQuality.LOW }
        scope.cancel()
    }

    @Test
    fun `at start the launch is counted`() = runBlocking<Unit> {
        val scope = CoroutineScope(Dispatchers.Default)
        startAppWork(koin, scope)
        waitUntil { supportPromptStore.stored?.launchCount == 1 }
        scope.cancel()
    }

    @Test
    fun `a track's speed is saved as it changes`() = runBlocking<Unit> {
        val scope = CoroutineScope(Dispatchers.Default)
        startAppWork(koin, scope)
        val imported = koin.get<ImportDocumentUseCase>()(toneFile().path)
        assertIs<ImportResult.Success>(imported)
        assertTrue(koin.get<OpenTrackForPracticeUseCase>()(imported.track))
        // Lets the auto-save subscribe before the change, as the app does at start.
        delay(200)
        koin.get<PlaybackEngine>().setSpeedPercent(75f)
        val saved = withTimeout(5_000) {
            koin.get<ObservePracticeStatesUseCase>()().first { it[imported.track.id] != null }
        }
        assertEquals(75f, saved.getValue(imported.track.id).speedPercent)
        scope.cancel()
    }

    @Test
    fun `practice keys drive the player`() = runBlocking<Unit> {
        val source = toneFile()
        val imported = koin.get<ImportDocumentUseCase>()(source.path)
        assertIs<ImportResult.Success>(imported)
        val player = koin.get<PlayerViewModel>()
        val engine = koin.get<PlaybackEngine>()
        // The ViewModel works on the Swing thread, as in the app.
        SwingUtilities.invokeAndWait { player.onTrackSelected(imported.track) }
        waitUntil { engine.isOpen }
        val textInput = TextInputFocus()
        val shortcuts = PracticeShortcuts(player, textInput)

        SwingUtilities.invokeAndWait { assertTrue(shortcuts.handle(Key.Spacebar)) }
        waitUntil { engine.isPlaying }
        // Held down: the repeats must not pause it again.
        SwingUtilities.invokeAndWait { shortcuts.handle(Key.Spacebar) }
        assertTrue(engine.isPlaying, "auto-repeat paused playback")
        SwingUtilities.invokeAndWait { shortcuts.onSpaceReleased() }
        native.render(44_100 * 2)
        // No loop yet: A starts a new one at the playhead, and it loops.
        SwingUtilities.invokeAndWait { shortcuts.handle(Key.A) }
        waitUntil { engine.isLoopOn }
        assertEquals(0.2f, engine.loopStart, 0.02f)
        native.render(44_100 * 2)
        SwingUtilities.invokeAndWait { shortcuts.handle(Key.B) }
        waitUntil { abs(engine.loopEnd - engine.loopStart - 0.2f) < 0.02f }
        assertTrue(engine.isLoopOn, "B keeps looping")
        // Shift+L turns looping off, the loop stays.
        SwingUtilities.invokeAndWait { shortcuts.handle(Key.L, shift = true) }
        waitUntil { !engine.isLoopOn }
        SwingUtilities.invokeAndWait { assertFalse(shortcuts.handle(Key.Q), "not a practice key") }
        source.delete()
    }

    @Test
    fun `restoring a playlist that changed since the backup asks, then overwrites it`() = runBlocking<Unit> {
        val (playlistId, trackId, backup) = backupOfOnePlaylist()
        // Changed after the backup: now it is a different playlist with the same name.
        koin.get<RemoveTrackFromPlaylistUseCase>()(playlistId, trackId)
        val viewModel = koin.get<BackupViewModel>()

        SwingUtilities.invokeAndWait { viewModel.onRestorePicked(backup.path) }
        waitUntil { viewModel.state.value is BackupUiState.PlaylistConflict }
        assertEquals(listOf("Band"), (viewModel.state.value as BackupUiState.PlaylistConflict).names)
        SwingUtilities.invokeAndWait { viewModel.onPlaylistConflictResolved(PlaylistConflictChoice.Overwrite) }
        waitUntil { viewModel.state.value == BackupUiState.Idle }

        assertEquals(listOf("Band"), koin.get<ObservePlaylistsUseCase>()().first().map { it.name })
        val restored = koin.get<ObservePlaylistUseCase>()(playlistId).first()
        assertEquals(listOf(trackId), restored?.tracks?.map { it.id })
    }

    @Test
    fun `cancelling the playlist question restores nothing`() = runBlocking<Unit> {
        val (playlistId, trackId, backup) = backupOfOnePlaylist()
        koin.get<RemoveTrackFromPlaylistUseCase>()(playlistId, trackId)
        val viewModel = koin.get<BackupViewModel>()

        SwingUtilities.invokeAndWait { viewModel.onRestorePicked(backup.path) }
        waitUntil { viewModel.state.value is BackupUiState.PlaylistConflict }
        SwingUtilities.invokeAndWait { viewModel.onPlaylistConflictDismissed() }

        assertEquals(BackupUiState.Idle, viewModel.state.value)
        assertEquals(listOf("Band"), koin.get<ObservePlaylistsUseCase>()().first().map { it.name })
        assertEquals(emptyList(), koin.get<ObservePlaylistUseCase>()(playlistId).first()?.tracks)
    }

    /** A library with one track in the playlist "Band", and its backup. */
    private suspend fun backupOfOnePlaylist(): Triple<Long, String, File> {
        val imported = koin.get<ImportDocumentUseCase>()(toneFile().path)
        assertIs<ImportResult.Success>(imported)
        val created = koin.get<CreatePlaylistUseCase>()("Band")
        assertIs<PlaylistResult.Done>(created)
        koin.get<AddTrackToPlaylistUseCase>()(created.playlistId, imported.track.id)
        val export = koin.get<ExportLibraryBackupUseCase>()({})
        assertIs<BackupExportResult.Success>(export)
        return Triple(created.playlistId, imported.track.id, File(export.file.path))
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            delay(20)
        }
        error("condition not met")
    }

    /**
     * Network, so off by default: -Dslowdowner.networkTests=true. YouTube serves fragmented MP4 and the
     * desktop keeps it as downloaded (no remux): this checks the engine's decoder reads it.
     */
    @Test
    fun `a YouTube import plays`() = runBlocking<Unit> {
        if (System.getProperty("slowdowner.networkTests") != "true") return@runBlocking
        val result = koin.get<ImportTrackFromUrlUseCase>()("https://www.youtube.com/watch?v=jNQXAC9IVRw") {}
        assertIs<ImportResult.Success>(result, "import: $result")
        assertTrue(koin.get<OpenTrackForPracticeUseCase>()(result.track))
        val engine = koin.get<PlaybackEngine>()
        assertTrue(engine.durationSeconds in 15f..25f, "duration ${engine.durationSeconds}")
        engine.togglePlayStop()
        val audio = native.render(44_100 * 3)
        assertTrue(audio.any { abs(it) > 0.05f }, "silent playback")
    }

    private fun toneFile(): File {
        val rate = 44_100
        val frames = 10 * rate
        val data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val sample = (sin(2 * PI * 441 * i / rate) * 16_000).toInt().toShort()
            data.putShort(sample).putShort(sample)
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + frames * 4); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(2); putInt(rate); putInt(rate * 4); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(frames * 4)
        }
        val dir = Files.createTempDirectory("slowdowner-source").toFile()
        return File(dir, "tone.wav").apply { writeBytes(header.array() + data.array()) }
    }
}
