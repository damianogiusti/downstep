package com.damianogiusti.slowdowner.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.domain.library.ImportDocumentUseCase
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.domain.theme.ThemeSelection
import com.damianogiusti.slowdowner.ui.common.LocalTextInputFocus
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.navigation.ActivityActions
import com.damianogiusti.slowdowner.ui.navigation.ActivityViewModels
import com.damianogiusti.slowdowner.ui.navigation.SlowDownerApp
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.ThemeViewModel
import com.damianogiusti.slowdowner.ui.theme.Themes
import com.damianogiusti.slowdowner.ui.theme.themeById
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The player in every theme and mode, and the header's theme menu: open, previewing the theme under
 * the pointer, back on leaving, applied on a click. The window root is wired as in Main.kt (the
 * ThemeViewModel's effective theme around the app). PNGs land in build/screenshots/theme-*.png.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ThemeRenderTest {

    private val directories = AppDirectories(Files.createTempDirectory("downstep-theme-render").toFile())
    private val themeStore = InMemoryThemeStore()
    private val koin = startKoin {
        modules(
            desktopModule(directories, NativeEngine.manual(44_100), InMemoryEffectsSettingsStore(), InMemorySupportPromptStore(),
                themeStore = themeStore, clickSettingsStore = InMemoryClickSettingsStore()),
            useCasesModule,
            uiModule,
        )
    }.koin

    private lateinit var scene: ImageComposeScene
    private var player: PlayerViewModel? = null
    private var theme: ThemeViewModel? = null
    private var time = 0L

    @AfterTest
    fun tearDown() {
        if (::scene.isInitialized) SwingUtilities.invokeAndWait { scene.close() }
        koin.get<DesktopPlaybackEngine>().close()
        stopKoin()
        directories.root.deleteRecursively()
    }

    @Test
    fun `the player renders in every theme and mode`() {
        openPlayer()
        for (appTheme in Themes) for (mode in ThemeMode.entries) {
            val selection = ThemeSelection(mode, appTheme.id)
            SwingUtilities.invokeAndWait { theme!!.onApply(selection) }
            val image = frames(3, "theme-${appTheme.id}-${mode.name.lowercase()}.png")
            val colors = appTheme.colors(mode)
            assertColorNear(image, 4, image.height - 4, colors.bg.rgb(), "${appTheme.id} $mode background")
        }
        assertEquals("high-contrast", themeStore.readThemeId())
        assertEquals("Light", themeStore.readMode())
    }

    @Test
    fun `the theme menu previews the hovered theme, reverts on leaving and applies on a click`() {
        openPlayer()
        val downstep = themeById("downstep").dark
        val forest = themeById("forest").dark

        click(THEME_BUTTON)
        frames(4, "theme-menu-downstep-dark.png")

        hover(rowCentre(FOREST_ROW))
        val previewing = frames(3, "theme-menu-hover-preview.png")
        assertColorNear(previewing, 4, previewing.height - 4, forest.bg.rgb(), "background previewing Forest")
        assertEquals(null, themeStore.readThemeId(), "a preview is saved")

        // Off the menu, over the player: back to Downstep, menu still open.
        hover(Offset(200f * SCALE, 600f * SCALE))
        val left = frames(3, "theme-menu-left.png")
        assertColorNear(left, 4, left.height - 4, downstep.bg.rgb(), "background after leaving the menu")

        hover(rowCentre(FOREST_ROW))
        frames(2)
        click(rowCentre(FOREST_ROW))
        hover(Offset(200f * SCALE, 600f * SCALE))
        val applied = frames(4, "theme-forest-applied.png")
        assertColorNear(applied, 4, applied.height - 4, forest.bg.rgb(), "background after the click")
        assertEquals("forest", themeStore.readThemeId())
        assertEquals("Dark", themeStore.readMode())
    }

    @Test
    fun `the theme menu in other themes`() {
        openPlayer()
        for ((id, mode) in listOf("midnight" to ThemeMode.Light, "high-contrast" to ThemeMode.Dark, "sandstone" to ThemeMode.Dark)) {
            SwingUtilities.invokeAndWait { theme!!.onApply(ThemeSelection(mode, id)) }
            frames(2)
            click(THEME_BUTTON)
            val image = frames(4, "theme-menu-$id-${mode.name.lowercase()}.png")
            assertColorNear(image, 4, image.height - 4, themeById(id).colors(mode).bg.rgb(), "$id background")
            // Esc closes it without a change.
            key(Key.Escape)
            frames(3)
        }
    }

    @Test
    fun `Esc ends a keyboard preview and keeps the chosen theme`() {
        openPlayer()
        val downstep = themeById("downstep").dark
        click(THEME_BUTTON)
        frames(3)
        // The focus starts on the checked theme (Downstep): two down is Midnight, previewed.
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        val previewing = frames(3, "theme-menu-keyboard-preview.png")
        assertColorNear(previewing, 4, previewing.height - 4, themeById("midnight").dark.bg.rgb(), "background previewing Midnight")

        key(Key.Escape)
        val closed = frames(3)
        assertColorNear(closed, 4, closed.height - 4, downstep.bg.rgb(), "background after Esc")
        assertEquals(null, themeStore.readThemeId())
    }

    /** A 30 s track with three loops, as PlayerRenderTest, in a scene whose root follows the ThemeViewModel. */
    private fun openPlayer() {
        val audio = File(Files.createTempDirectory("downstep-theme-audio").toFile(), "Easy Blues.wav")
        audio.writeBytes(musicWav())
        val imported = runBlocking { koin.get<ImportDocumentUseCase>()(audio.path) }
        assertIs<ImportResult.Success>(imported)
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
                    val themeViewModel = koinViewModel<ThemeViewModel>()
                    theme = themeViewModel
                    val selection by themeViewModel.effective.collectAsStateWithLifecycle()
                    SlowDownerTheme(selection.mode, themeById(selection.themeId)) {
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
        )
        steps.forEach { step ->
            SwingUtilities.invokeAndWait { player?.let(step) }
            frames(4)
        }
        frames(6)
    }

    /** [count] frames 100 ms apart; the last one is returned (and saved as [name]). */
    private fun frames(count: Int, name: String? = null): Image {
        lateinit var image: Image
        repeat(count) {
            Thread.sleep(100)
            time += 100_000_000L
            SwingUtilities.invokeAndWait { image = scene.render(time) }
        }
        if (name != null) {
            val output = File("build/screenshots/$name")
            output.parentFile.mkdirs()
            output.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
        }
        return image
    }

    private fun hover(at: Offset) = SwingUtilities.invokeAndWait { scene.sendPointerEvent(PointerEventType.Move, at) }

    private fun click(at: Offset) {
        hover(at)
        SwingUtilities.invokeAndWait {
            scene.sendPointerEvent(PointerEventType.Press, at)
            scene.sendPointerEvent(PointerEventType.Release, at)
        }
    }

    private fun key(key: Key) = SwingUtilities.invokeAndWait {
        for (type in listOf(KeyEventType.KeyDown, KeyEventType.KeyUp)) {
            scene.sendKeyEvent(KeyEvent(key, type))
        }
    }

    private fun rowCentre(row: Int) = Offset(MENU_ROW_X * SCALE, (FIRST_ROW_Y + row * ROW_STEP) * SCALE)

    private fun androidx.compose.ui.graphics.Color.rgb(): Int =
        ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()

    private fun assertColorNear(image: Image, x: Int, y: Int, rgb: Int, what: String) {
        val pixel = Bitmap.makeFromImage(image).getColor(x, y)
        val channels = listOf(16, 8, 0).map { shift -> abs(((pixel shr shift) and 0xFF) - ((rgb shr shift) and 0xFF)) }
        assertTrue(channels.all { it <= 3 }, "$what at ($x, $y) is #%06X, expected about #%06X".format(pixel and 0xFFFFFF, rgb))
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
    )

    private companion object {
        const val WIDTH_DP = 1280
        const val HEIGHT_DP = 860
        const val SCALE = 2
        // The header's icons end 24dp from the right, 40dp each, 4dp apart: Focus, Theme, EQ, Presets, Export.
        val THEME_BUTTON = Offset((WIDTH_DP - 24 - 3 * 44 - 20f) * SCALE, 40f * SCALE)
        // Measured on theme-menu-downstep-dark.png: the theme rows, 40dp apart.
        const val MENU_ROW_X = 1000f
        const val FIRST_ROW_Y = 200f
        const val ROW_STEP = 40f
        const val FOREST_ROW = 3
    }
}
