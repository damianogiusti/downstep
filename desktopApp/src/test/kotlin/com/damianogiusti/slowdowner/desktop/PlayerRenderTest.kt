package com.damianogiusti.slowdowner.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
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
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.domain.library.ImportDocumentUseCase
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.ui.common.LocalTextInputFocus
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.navigation.ActivityActions
import com.damianogiusti.slowdowner.ui.navigation.ActivityViewModels
import com.damianogiusti.slowdowner.ui.navigation.SlowDownerApp
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Renders the Downstep 2.0 player as the design shows it (README §2): a track with three loops and a
 * BPM, in both themes and with the library drawer open. The PNGs land in
 * build/screenshots for a side-by-side look with docs/design/downstep-2/screenshots; the asserts check
 * the colours that say the right layout is up (amber Play button, the drawer's panel).
 */
class PlayerRenderTest {

    private val directories = AppDirectories(Files.createTempDirectory("downstep-player-render").toFile())
    private val koin = startKoin {
        modules(
            desktopModule(directories, NativeEngine.manual(44_100), InMemoryEffectsSettingsStore(), InMemorySupportPromptStore(),
                clickSettingsStore = InMemoryClickSettingsStore()),
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
    fun `the dark player shows its loops and the amber play button`() {
        val image = renderPlayer("player-dark.png", ThemeMode.Dark)
        assertColorNear(image, PLAY_BUTTON_X, PLAY_BUTTON_Y, 0xF2A541, "Play button")
        assertPlayIconCentred(image, 0xF2A541)
    }

    @Test
    fun `the light player uses the light palette`() {
        val image = renderPlayer("player-light.png", ThemeMode.Light)
        assertColorNear(image, PLAY_BUTTON_X, PLAY_BUTTON_Y, 0x935100, "Play button")
        assertColorNear(image, 4, image.height - 4, 0xFAF6F0, "background")
        assertPlayIconCentred(image, 0x935100)
    }

    @Test
    fun `the library drawer slides over the player`() {
        val image = renderPlayer("player-drawer.png", ThemeMode.Dark) { it.onDrawerOpened() }
        // Inside the 360dp panel, below its rows: surface1.
        assertColorNear(image, 300 * SCALE, image.height - 200, 0x1A1E24, "drawer panel")
    }

    /** Opens a 30 s track, sets 92 BPM and three loops (the second active), then [extra]. */
    private fun renderPlayer(name: String, theme: ThemeMode, extra: (PlayerViewModel) -> Unit = {}): Image {
        val audio = File(Files.createTempDirectory("downstep-player-audio").toFile(), "Easy Blues.wav")
        audio.writeBytes(musicWav())
        val imported = runBlocking { koin.get<ImportDocumentUseCase>()(audio.path) }
        assertIs<ImportResult.Success>(imported)
        val steps = listOf<(PlayerViewModel) -> Unit>(
            { it.onTrackSelected(imported.track) },
            { it.onBpmChanged(92f) },
            { it.onSeek(0.05f) },
            { it.onNewLoopAtPlayhead() },
            { it.onSeek(0.35f) },
            { it.onNewLoopAtPlayhead() },
            { it.onSeek(0.7f) },
            { it.onNewLoopAtPlayhead() },
            { it.onPreviousLoopClicked() },
            { it.onSeek(0.42f) },
            extra,
        )
        val image = render(File("build/screenshots/$name"), theme, steps)
        return image
    }

    private fun render(output: File, theme: ThemeMode, steps: List<(PlayerViewModel) -> Unit>): Image {
        output.parentFile.mkdirs()
        lateinit var scene: ImageComposeScene
        var player: PlayerViewModel? = null
        SwingUtilities.invokeAndWait {
            val lifecycle = object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                override val lifecycle: Lifecycle get() = registry
            }
            val store = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
            scene = ImageComposeScene(width = WIDTH_DP * SCALE, height = HEIGHT_DP * SCALE, density = Density(SCALE.toFloat())) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides lifecycle,
                    LocalViewModelStoreOwner provides store,
                    LocalTextInputFocus provides TextInputFocus(),
                ) {
                    SlowDownerTheme(theme) {
                        val viewModels = ActivityViewModels(koinViewModel(), koinViewModel(), koinViewModel(), koinViewModel())
                        player = viewModels.player
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) { SlowDownerApp(viewModels, noActions()) }
                        }
                    }
                }
            }
            scene.render(0)
        }
        var time = 0L
        // One step per few frames: each loop edit waits for Room before the next one starts.
        steps.forEach { step ->
            SwingUtilities.invokeAndWait { player?.let(step) }
            repeat(4) {
                Thread.sleep(80)
                time += 80_000_000L
                SwingUtilities.invokeAndWait { scene.render(time) }
            }
        }
        repeat(6) {
            Thread.sleep(120)
            time += 120_000_000L
            SwingUtilities.invokeAndWait { scene.render(time) }
        }
        lateinit var image: Image
        SwingUtilities.invokeAndWait {
            image = scene.render(time + 2_000_000_000L)
            output.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            scene.close()
        }
        return image
    }

    private fun assertColorNear(image: Image, x: Int, y: Int, rgb: Int, what: String) {
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        val pixel = bitmap.getColor(x, y)
        val channels = listOf(16, 8, 0).map { shift -> abs(((pixel shr shift) and 0xFF) - ((rgb shr shift) and 0xFF)) }
        assertTrue(channels.all { it <= 12 }, "$what at ($x, $y) is #%06X, expected about #%06X".format(pixel and 0xFFFFFF, rgb))
    }

    /**
     * The play triangle's visual centre (its ink-weighted centroid) is the centre of the round button,
     * within [ICON_CENTRE_TOLERANCE_DP] on both axes: not its bounding box, which leans right. The
     * button is found by its [amber] pixels near the bottom centre of the window, where the transport sits.
     */
    private fun assertPlayIconCentred(image: Image, amber: Int) {
        val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
        fun distance(x: Int, y: Int): Int {
            val pixel = bitmap.getColor(x, y)
            return listOf(16, 8, 0).maxOf { shift -> abs(((pixel shr shift) and 0xFF) - ((amber shr shift) and 0xFF)) }
        }
        val searchX = (640 - 50) * SCALE until (640 + 50) * SCALE
        val searchY = (PLAY_BUTTON_Y_DP - 80) * SCALE until minOf(PLAY_BUTTON_Y_DP + 80, HEIGHT_DP) * SCALE
        val disc = searchY.flatMap { y -> searchX.filter { x -> distance(x, y) <= 24 }.map { x -> x to y } }
        assertTrue(disc.isNotEmpty(), "no Play button found")
        val left = disc.minOf { it.first }
        val right = disc.maxOf { it.first } + 1
        val top = disc.minOf { it.second }
        val bottom = disc.maxOf { it.second } + 1
        val centreX = (left + right) / 2.0
        val centreY = (top + bottom) / 2.0
        // Ink = how far a pixel is from the container colour, inside the disc minus its anti-aliased rim.
        val inner = (right - left) / 2.0 - 3
        var ink = 0.0
        var sumX = 0.0
        var sumY = 0.0
        for (y in top until bottom) for (x in left until right) {
            val dx = x + 0.5 - centreX
            val dy = y + 0.5 - centreY
            if (dx * dx + dy * dy > inner * inner) continue
            val weight = distance(x, y) / 255.0
            ink += weight
            sumX += weight * dx
            sumY += weight * dy
        }
        val offsetX = sumX / ink / SCALE
        val offsetY = sumY / ink / SCALE
        assertTrue(
            abs(offsetX) <= ICON_CENTRE_TOLERANCE_DP && abs(offsetY) <= ICON_CENTRE_TOLERANCE_DP,
            "the play icon's centre is %.2f, %.2f dp off the button's centre".format(offsetX, offsetY),
        )
    }

    /** 30 s of a chord whose loudness swells and fades, so the waveform has a shape. */
    private fun musicWav(): ByteArray {
        val rate = 44_100
        val frames = 30 * rate
        val data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val t = i.toDouble() / rate
            val envelope = 0.25 + 0.75 * abs(sin(2 * PI * t / 7.0)) * (0.6 + 0.4 * abs(sin(2 * PI * t * 1.5)))
            val chord = sin(2 * PI * 220 * t) + 0.6 * sin(2 * PI * 277.2 * t) + 0.4 * sin(2 * PI * 329.6 * t)
            val sample = (chord / 2.0 * envelope * 26_000).toInt().coerceIn(-32_767, 32_767).toShort()
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
        // As in the app: the header shows the fullscreen toggle.
        fullscreen = TestFullscreen(),
    )

    private companion object {
        const val WIDTH_DP = 1280
        const val HEIGHT_DP = 860
        const val SCALE = 2
        // Inside the 68dp Play button beside its icon: the transport row is centred, at the bottom, 14dp padding.
        const val PLAY_BUTTON_X = (640 + 24) * SCALE
        const val PLAY_BUTTON_Y_DP = HEIGHT_DP - 14 - 34
        const val PLAY_BUTTON_Y = PLAY_BUTTON_Y_DP * SCALE
        // The box-centred triangle was 2.5dp off; a quarter of that is rounding and anti-aliasing.
        const val ICON_CENTRE_TOLERANCE_DP = 0.6
    }
}
