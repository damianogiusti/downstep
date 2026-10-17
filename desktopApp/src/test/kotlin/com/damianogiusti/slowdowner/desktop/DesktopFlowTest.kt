package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.domain.backup.BackupExportResult
import com.damianogiusti.slowdowner.domain.backup.ExportLibraryBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.PlaylistConflictChoice
import com.damianogiusti.slowdowner.domain.loops.LoopRepository
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
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.damianogiusti.slowdowner.ui.common.LocalTextInputFocus
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.player.LoopChipActions
import com.damianogiusti.slowdowner.ui.player.LoopChipModel
import com.damianogiusti.slowdowner.ui.player.LoopChipsState
import com.damianogiusti.slowdowner.ui.player.components.LoopChipsStrip
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
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
        // L adds a second loop near the start, and makes it the active one.
        SwingUtilities.invokeAndWait {
            player.onSeek(0.05f)
            // ⇧L is still held otherwise.
            shortcuts.handle(keyUp(Key.L))
            shortcuts.handle(Key.L)
        }
        waitUntil { abs(engine.loopStart - 0.05f) < 0.02f }
        // [ typed with AltGr on another key (Italian layout): previous loop, round to the last. Released,
        // then again: the first one, so the release was matched to the key that typed it.
        SwingUtilities.invokeAndWait { assertTrue(shortcuts.handle(altGr(Key.Grave, '['))) }
        waitUntil { abs(engine.loopStart - 0.2f) < 0.02f }
        SwingUtilities.invokeAndWait { shortcuts.handle(keyUp(Key.Grave)) }
        SwingUtilities.invokeAndWait { shortcuts.handle(altGr(Key.Grave, '[')) }
        waitUntil { abs(engine.loopStart - 0.05f) < 0.02f }
        source.delete()
    }

    @Test
    fun `F toggles focus once per press, Esc closes the drawer, then focus`() {
        val player = koin.get<PlayerViewModel>()
        val shortcuts = PracticeShortcuts(player, TextInputFocus())
        SwingUtilities.invokeAndWait {
            assertTrue(shortcuts.handle(keyDown(Key.F)))
            // Auto-repeat while held must not toggle it back.
            shortcuts.handle(keyDown(Key.F))
            assertTrue(player.layout.value.isFocusMode)
            shortcuts.handle(keyUp(Key.F))

            player.onDrawerOpened()
            assertTrue(shortcuts.handle(keyDown(Key.Escape)))
            assertFalse(player.layout.value.isDrawerOpen, "Esc closes the drawer first")
            assertTrue(player.layout.value.isFocusMode)
            assertTrue(shortcuts.handle(keyDown(Key.Escape)))
            assertFalse(player.layout.value.isFocusMode)
            assertFalse(shortcuts.handle(keyDown(Key.Escape)), "nothing to close: Esc goes on to dialogs")

            // ⌃F belongs to the system and the menus.
            assertFalse(shortcuts.handle(keyDown(Key.F, ctrl = true)))
            assertFalse(player.layout.value.isFocusMode)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `while a loop chip is renamed the practice keys are left to its field`() {
        val player = koin.get<PlayerViewModel>()
        val textInput = TextInputFocus()
        val shortcuts = PracticeShortcuts(player, textInput)
        val chips = LoopChipsState(listOf(LoopChipModel(1, "Solo", 0, 8f, 0, isActive = true)), enabled = true)
        val actions = LoopChipActions({}, {}, { _, _ -> }, {}, {}, {})
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width = 400, height = 80, density = Density(1f)) {
                CompositionLocalProvider(LocalTextInputFocus provides textInput) {
                    SlowDownerTheme { LoopChipsStrip(chips, emptyList(), actions) }
                }
            }
            try {
                scene.render(0)
                // A double click on the chip: its name becomes a text field.
                repeat(2) {
                    scene.sendPointerEvent(PointerEventType.Press, Offset(50f, 29f), button = PointerButton.Primary)
                    scene.render(0)
                    scene.sendPointerEvent(PointerEventType.Release, Offset(50f, 29f), button = PointerButton.Primary)
                    scene.render(0)
                }
                scene.render(16_000_000)
                assertTrue(textInput.isTyping)

                assertFalse(shortcuts.handle(keyDown(Key.F)), "F is typed, not focus mode")
                assertFalse(shortcuts.handle(keyDown(Key.L)))
                assertFalse(shortcuts.handle(keyDown(Key.Spacebar)))
                assertFalse(player.layout.value.isFocusMode)
                assertFalse(shortcuts.handle(keyDown(Key.Escape)), "Esc cancels the rename, it is not the window's")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `while a playlist covers the player its loop and layout keys do nothing`() = runBlocking<Unit> {
        val source = toneFile()
        val imported = koin.get<ImportDocumentUseCase>()(source.path)
        assertIs<ImportResult.Success>(imported)
        val player = koin.get<PlayerViewModel>()
        val engine = koin.get<PlaybackEngine>()
        val loops = koin.get<LoopRepository>()
        SwingUtilities.invokeAndWait { player.onTrackSelected(imported.track) }
        waitUntil { engine.isOpen }
        val shortcuts = PracticeShortcuts(player, TextInputFocus())

        SwingUtilities.invokeAndWait {
            player.onShownChanged(false)
            assertFalse(shortcuts.handle(Key.L), "L would add a loop out of sight")
            assertFalse(shortcuts.handle(Key.A))
            assertFalse(shortcuts.handle(Key.B))
            assertFalse(shortcuts.handle(Key.RightBracket))
            assertFalse(shortcuts.handle(Key.F))
            assertFalse(player.layout.value.isFocusMode)
            // Left to the playlist screen, which goes back.
            assertFalse(shortcuts.handle(Key.Escape))
        }
        // Give a loop edit, had one started, the time to land.
        Thread.sleep(300)
        assertTrue(loops.trackLoops(imported.track.id).loops.isEmpty())
        assertFalse(engine.isLoopOn)

        // Back on the player: the same keys work again.
        SwingUtilities.invokeAndWait {
            player.onShownChanged(true)
            assertTrue(shortcuts.handle(Key.L))
        }
        waitUntil { player.loopChips.value.chips.size == 1 }
        source.delete()
    }

    @Test
    fun `a key released with a modifier held still works on the next press`() {
        val player = koin.get<PlayerViewModel>()
        val shortcuts = PracticeShortcuts(player, TextInputFocus())
        SwingUtilities.invokeAndWait {
            shortcuts.handle(keyDown(Key.F))
            shortcuts.handle(keyUp(Key.F, ctrl = true))
            shortcuts.handle(keyDown(Key.F))
            assertFalse(player.layout.value.isFocusMode, "F was stuck as held")
            // Held while the window lost focus: the release went to another app.
            shortcuts.onFocusLost()
            shortcuts.handle(keyDown(Key.F))
            assertTrue(player.layout.value.isFocusMode)
        }
    }

    private fun keyDown(key: Key, ctrl: Boolean = false) = keyEvent(key, KeyEventType.KeyDown, ctrl = ctrl)

    private fun keyUp(key: Key, ctrl: Boolean = false) = keyEvent(key, KeyEventType.KeyUp, ctrl = ctrl)

    /** ⌃⌥ + [key] typing [char]: AltGr on Windows. */
    private fun altGr(key: Key, char: Char) = keyEvent(key, KeyEventType.KeyDown, char, ctrl = true, alt = true)

    // The test-only constructor: what the window passes is built from AWT events internally.
    @OptIn(InternalComposeUiApi::class)
    private fun keyEvent(key: Key, type: KeyEventType, char: Char? = null, ctrl: Boolean = false, alt: Boolean = false) =
        KeyEvent(key, type, codePoint = char?.code ?: 0, isCtrlPressed = ctrl, isAltPressed = alt)

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
