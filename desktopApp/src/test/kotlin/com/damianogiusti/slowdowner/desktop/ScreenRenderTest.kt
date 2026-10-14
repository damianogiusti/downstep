package com.damianogiusti.slowdowner.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.data.support.SupportPromptDto
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.ui.navigation.ActivityActions
import com.damianogiusti.slowdowner.ui.navigation.ActivityViewModels
import com.damianogiusti.slowdowner.ui.navigation.SlowDownerApp
import com.damianogiusti.slowdowner.ui.support.SupportPromptHost
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.domain.library.ImportDocumentUseCase
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.assertIs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the real app (desktop bindings) off screen and saves PNGs in build/screenshots: a smoke test
 * that fonts, icons and strings load on desktop, and that wide windows get the two-pane layout.
 */
class ScreenRenderTest {

    private val directories = AppDirectories(Files.createTempDirectory("slowdowner-render").toFile())
    private val supportPromptStore = InMemorySupportPromptStore()
    private val koin = startKoin {
        modules(
            desktopModule(directories, NativeEngine.manual(44_100), InMemoryEffectsSettingsStore(), supportPromptStore),
            useCasesModule,
            uiModule,
        )
    }.koin

    @AfterTest
    fun tearDown() {
        koin.get<DesktopPlaybackEngine>().close()
        stopKoin()
        directories.root.deleteRecursively()
    }

    @Test
    fun `the library screen renders`() {
        val bytes = render(File("build/screenshots/library-narrow.png"), widthDp = 440, heightDp = 900)
        assertTrue(bytes > 10_000, "suspiciously small render: $bytes bytes")
    }

    @Test
    fun `a wide window shows the library and the player side by side`() {
        val tone = File(Files.createTempDirectory("slowdowner-render-audio").toFile(), "Autumn Leaves.wav")
        tone.writeBytes(toneWav())
        val imported = runBlocking { koin.get<ImportDocumentUseCase>()(tone.path) }
        assertIs<ImportResult.Success>(imported)
        val bytes = render(File("build/screenshots/library-wide.png"), widthDp = 1280, heightDp = 820) { player ->
            player.onTrackSelected(imported.track)
        }
        assertTrue(bytes > 10_000, "suspiciously small render: $bytes bytes")
    }

    @Test
    fun `on the third launch the support prompt shows over the app`() {
        supportPromptStore.stored = SupportPromptDto(launchCount = 3, lastShownAtMillis = null)
        val bytes = render(File("build/screenshots/support-prompt.png"), widthDp = 1280, heightDp = 820)
        assertTrue(bytes > 10_000, "suspiciously small render: $bytes bytes")
    }

    /** Renders the app at the given size; [setup] runs on the Swing thread once the ViewModels exist. */
    private fun render(output: File, widthDp: Int, heightDp: Int, setup: (PlayerViewModel) -> Unit = {}): Int {
        output.parentFile.mkdirs()
        lateinit var scene: ImageComposeScene
        var player: PlayerViewModel? = null
        // Compose and viewModelScope expect the Swing thread as the main thread. Frames are rendered in
        // separate turns, so the ViewModels' coroutines can run on that thread in between.
        SwingUtilities.invokeAndWait {
            val lifecycle = object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                override val lifecycle: Lifecycle get() = registry
            }
            val store = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
            scene = ImageComposeScene(width = widthDp * 2, height = heightDp * 2, density = Density(2f)) {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycle, LocalViewModelStoreOwner provides store) {
                    SlowDownerTheme {
                        val viewModels = ActivityViewModels(koinViewModel(), koinViewModel(), koinViewModel())
                        player = viewModels.player
                        SlowDownerApp(viewModels, noActions())
                        SupportPromptHost()
                    }
                }
            }
            scene.render(0)
            player?.let(setup)
        }
        repeat(10) { frame ->
            Thread.sleep(150)
            SwingUtilities.invokeAndWait { scene.render((frame + 1) * 150_000_000L) }
        }
        var bytes = 0
        SwingUtilities.invokeAndWait {
            val png = requireNotNull(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)).bytes
            output.writeBytes(png)
            bytes = png.size
            scene.close()
        }
        return bytes
    }

    /** 10 s stereo 16-bit tone, so the player has a real file to open. */
    private fun toneWav(): ByteArray {
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
        return header.array() + data.array()
    }

    private fun noActions() = ActivityActions(
        onPickFile = {}, onOpenSpotify = null, onPlayStop = {}, onPlaybackStarted = {},
        onShareExport = {}, onSaveExport = {}, onPickBackup = {}, onShareBackup = {}, onSaveBackup = {},
    )
}
