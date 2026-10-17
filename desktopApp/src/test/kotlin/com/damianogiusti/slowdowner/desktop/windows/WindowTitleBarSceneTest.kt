package com.damianogiusti.slowdowner.desktop.windows

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.common.TopBanners
import com.damianogiusti.slowdowner.ui.common.titleBarControl
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.ui.common.LocalWindowTitleBar
import com.damianogiusti.slowdowner.ui.playlists.PlaylistContent
import com.damianogiusti.slowdowner.ui.playlists.PlaylistContentActions
import com.damianogiusti.slowdowner.ui.playlists.PlaylistUiState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import javax.swing.SwingUtilities
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.IntRect
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.desktop.AppDirectories
import com.damianogiusti.slowdowner.desktop.InMemoryClickSettingsStore
import com.damianogiusti.slowdowner.desktop.InMemoryEffectsSettingsStore
import com.damianogiusti.slowdowner.desktop.InMemorySupportPromptStore
import com.damianogiusti.slowdowner.desktop.MinimumWindowSize
import com.damianogiusti.slowdowner.desktop.PlayerScene
import com.damianogiusti.slowdowner.desktop.TestFullscreen
import com.damianogiusti.slowdowner.desktop.desktopModule
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real player with the Windows title bar's Compose side: what the header reports to the hit test
 * (caption, controls, the maximize button), the window buttons, the overlays, and that playing
 * reports nothing.
 */
@OptIn(ExperimentalComposeUiApi::class)
class WindowTitleBarSceneTest {

    private val directories = AppDirectories(Files.createTempDirectory("downstep-titlebar").toFile())
    private val koin = startKoin {
        modules(
            desktopModule(directories, NativeEngine.manual(44_100), InMemoryEffectsSettingsStore(), InMemorySupportPromptStore(),
                clickSettingsStore = InMemoryClickSettingsStore()),
            useCasesModule,
            uiModule,
        )
    }.koin

    /** The window, as the hook would be: counts what it receives. */
    private class FakeWindow : CaptionlessWindow {
        var published = 0
        override var regions: TitleBarRegions = TitleBarRegions.NONE
            set(value) {
                field = value
                published++
            }
        override var listener: CaptionlessWindow.Listener? = null
        val commands = mutableListOf<String>()
        var isMaximized = false

        override fun minimize() {
            commands += "minimize"
        }

        override fun toggleMaximized() {
            commands += "maximize"
        }

        override fun close() {
            commands += "close"
        }
    }

    private val window = FakeWindow()
    private val fullscreen = TestFullscreen()
    private val titleBar = WindowsTitleBar(window, isFullscreen = { fullscreen.isFullscreen }, isMaximized = { window.isMaximized })

    @AfterTest
    fun tearDown() {
        koin.get<DesktopPlaybackEngine>().close()
        stopKoin()
        directories.root.deleteRecursively()
    }

    private fun scene(block: (PlayerScene) -> Unit) =
        PlayerScene(koin, 1280, 820, scale = 2, fullscreen = fullscreen, titleBar = titleBar).use { scene ->
            scene.openTrackWithLoops()
            scene.capture()
            block(scene)
        }

    @Test
    fun `the header is the caption, its controls and the window buttons keep their clicks`() = scene { scene ->
        scene.capture(File("build/screenshots/titlebar-header.png"))
        val regions = window.regions
        // The header: the window's width at the top, 74 dp tall at 2x.
        val caption = regions.captions.single()
        assertEquals(IntRect(0, 0, 1280 * 2, caption.bottom), caption)
        assertTrue(caption.bottom in 140..160, "header height ${caption.bottom}")
        assertFalse(regions.hasOverlay)

        val maximize = assertNotNull(regions.maximizeButton)
        assertEquals(WINDOW_BUTTON_WIDTH.value.toInt() * 2, maximize.width)
        assertEquals(0, maximize.top)
        assertEquals(caption.bottom, maximize.bottom, "as tall as the header")

        // Minimize and close beside it, close in the window's corner.
        val close = regions.controls.single { it.right == 1280 * 2 && it.top == 0 }
        assertEquals(maximize.right, close.left)
        assertTrue(regions.controls.any { it.right == maximize.left && it.top == 0 }, "minimize")

        // The header's controls: what is under the Library pill and the BPM is a control, the title is not.
        val library = assertNotNull(scene.centreOfText("Library"))
        assertEquals(HitCode.CLIENT, hit(library))
        val title = assertNotNull(scene.centreOfText("Easy Blues"))
        assertEquals(HitCode.CAPTION, hit(title))
        assertEquals(HitCode.MAXBUTTON, hit(maximize.center.let { Offset(it.x.toFloat(), it.y.toFloat()) }))

        // The buttons are drawn, named for screen readers.
        for (name in listOf("Minimize", "Maximize", "Close")) assertNotNull(scene.centreOfDescription(name), name)
    }

    @Test
    fun `the window buttons act on the window`() = scene { scene ->
        scene.click(assertNotNull(scene.centreOfDescription("Minimize")))
        scene.click(assertNotNull(scene.centreOfDescription("Maximize")))
        scene.click(assertNotNull(scene.centreOfDescription("Close")))
        assertEquals(listOf("minimize", "maximize", "close"), window.commands)
    }

    @Test
    fun `the drawer over the header makes the whole window the app's`() = scene { scene ->
        scene.click(assertNotNull(scene.centreOfText("Library")))
        scene.capture()
        assertTrue(window.regions.hasOverlay, "drawer open")
        assertEquals(HitCode.CLIENT, hit(Offset(1500f, 20f)))

        scene.click(Offset(2000f, 1000f))
        scene.capture()
        assertFalse(window.regions.hasOverlay, "drawer closed")
    }

    @Test
    fun `a menu over the header makes the whole window the app's`() = scene { scene ->
        scene.click(assertNotNull(scene.centreOfDescription("Theme")))
        scene.capture()
        assertTrue(window.regions.hasOverlay, "theme menu open")
        scene.click(Offset(1000f, 1200f))
        scene.capture()
        assertFalse(window.regions.hasOverlay, "theme menu closed")
    }

    @Test
    fun `fullscreen hides the window buttons and gives their room back`() = scene { scene ->
        fullscreen.toggle()
        scene.capture()
        assertNull(scene.centreOfDescription("Minimize"))
        assertNull(window.regions.maximizeButton)
        assertEquals(0f, titleBar.buttonsWidth.value)
    }

    @Test
    fun `the playhead moving reports nothing`() = scene { scene ->
        val before = window.published
        scene.steps({ it.onSeek(0.1f) }, { it.onSeek(0.2f) }, { it.onSeek(0.6f) }, { it.onSeek(0.9f) })
        scene.capture()
        assertEquals(before, window.published, "regions published while only the playhead moved")
    }

    @Test
    fun `the playlist bar is the caption, with the window buttons`() {
        lateinit var scene: ImageComposeScene
        SwingUtilities.invokeAndWait {
            scene = ImageComposeScene(width = 1280, height = 400, density = Density(1f)) {
                CompositionLocalProvider(LocalWindowTitleBar provides titleBar) {
                    SlowDownerTheme(ThemeMode.Dark) {
                        PlaylistContent(
                            PlaylistUiState(),
                            PlaylistContentActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                        )
                    }
                }
            }
            scene.render(0)
            scene.render(16_000_000)
        }
        try {
            val regions = window.regions
            val caption = regions.captions.single()
            assertEquals(0, caption.top)
            assertEquals(1280, caption.width)
            val maximize = assertNotNull(regions.maximizeButton)
            assertEquals(caption.bottom, maximize.bottom)
            // Back, the menu, minimize and close.
            assertTrue(regions.controls.size >= 4, "controls ${regions.controls}")
            assertTrue(regions.controls.any { it.right == 1280 }, "close in the corner")
        } finally {
            SwingUtilities.invokeAndWait { scene.close() }
        }
    }

    @Test
    fun `at the minimum width beside the window buttons, the header keeps the title, the BPM and its icons`() =
        assertHeaderFits(MinimumWindowSize.width.value.toInt(), File("build/screenshots/titlebar-header-minimum.png"))

    @Test
    fun `on a small screen narrower than the minimum, the header still fits beside the window buttons`() =
        // 1280 × 720 at 150 %: the minimum window size gives way to the screen's 853 dp.
        assertHeaderFits(853, File("build/screenshots/titlebar-header-853.png"))

    private fun assertHeaderFits(widthDp: Int, screenshot: File) =
        PlayerScene(koin, widthDp, MinimumWindowSize.height.value.toInt(), scale = 2, fullscreen = fullscreen, titleBar = titleBar)
            .use { scene ->
                scene.openTrackWithLoops()
                scene.capture(screenshot)
                val minimize = assertNotNull(scene.boundsOfDescription("Minimize"))
                // The BPM opens the BPM dialog: laid out whole, and clickable.
                val bpm = assertNotNull(scene.boundsOfText("BPM"), "BPM in the header")
                assertTrue(bpm.width > 40f, "BPM width ${bpm.width}")
                assertEquals(HitCode.CLIENT, hit(bpm.center))
                val title = assertNotNull(scene.boundsOfText("Easy Blues"))
                assertTrue(title.width > 160f, "title width ${title.width}")
                // The icons at the header's end, whole, left of the window buttons.
                for (name in listOf("Theme", "Export")) {
                    val icon = assertNotNull(scene.boundsOfDescription(name), name)
                    assertTrue(icon.width >= 36f * 2 - 1, "$name width ${icon.width}")
                    assertTrue(icon.right <= minimize.left, "$name ${icon.right} under the window buttons at ${minimize.left}")
                }
                // Analyze and the click toggles keep their icons, and their names for screen readers.
                for (name in listOf("Analyze", "Metronome", "Count-in")) assertNotNull(scene.boundsOfDescription(name), name)
            }

    @Test
    fun `at the design width the header keeps every label`() = scene { scene ->
        for (name in listOf("Analyze", "Metronome", "Count-in")) assertNotNull(scene.boundsOfText(name), name)
    }

    @Test
    fun `a banner above the header is the window's top, with the window buttons in the corner`() {
        var showBanner by mutableStateOf(true)
        lateinit var scene: ImageComposeScene
        fun render() = SwingUtilities.invokeAndWait {
            scene.render(time)
            time += 16_000_000
            scene.render(time)
        }
        SwingUtilities.invokeAndWait {
            scene = ImageComposeScene(width = 1280, height = 400, density = Density(1f)) {
                CompositionLocalProvider(LocalWindowTitleBar provides titleBar) {
                    SlowDownerTheme(ThemeMode.Dark) {
                        TopBanners(banners = { endInset ->
                            if (showBanner) Box(Modifier.fillMaxWidth().height(40.dp).padding(end = endInset)) {
                                Box(Modifier.size(80.dp, 40.dp).align(Alignment.CenterEnd).titleBarControl())
                            }
                        }) {
                            PlaylistContent(PlaylistUiState(), PlaylistContentActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}))
                        }
                    }
                }
            }
        }
        try {
            render()
            render()
            // The banner drags the window, its control keeps its clicks, the bar below still drags it.
            val captions = window.regions.captions.sortedBy { it.top }
            assertEquals(IntRect(0, 0, 1280, 40), captions.first())
            assertTrue(captions.any { it.top == 40 }, "the playlist bar below: $captions")
            val maximize = assertNotNull(window.regions.maximizeButton)
            assertEquals(0, maximize.top)
            assertEquals(40, maximize.bottom, "as tall as the banner")
            assertTrue(window.regions.controls.any { it.right == 1280 && it.top == 0 && it.bottom == 40 }, "close in the corner")
            // The banner's control, left of the buttons.
            val bannerControl = window.regions.controls.single { it.top == 0 && it.width == 80 }
            assertEquals(1280 - WINDOW_BUTTON_WIDTH.value.toInt() * 3, bannerControl.right)
            // Once per window: the bar below draws none.
            assertEquals(1, window.regions.controls.count { it.right == 1280 })

            showBanner = false
            render()
            render()
            val bar = window.regions.captions.single()
            assertEquals(0, bar.top)
            assertEquals(bar.bottom, assertNotNull(window.regions.maximizeButton).bottom, "back in the bar, at the top")
        } finally {
            SwingUtilities.invokeAndWait { scene.close() }
        }
    }

    private var time = 0L

    /** In the header: the window's top bar, as the hook last heard of it. */
    private fun PlayerScene.headerNodes() = nodes().filter { it.boundsInRoot.bottom <= window.regions.captions.single().bottom }

    private fun PlayerScene.boundsOfText(text: String) = headerNodes()
        .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(text) } == true }
        ?.boundsInRoot

    private fun PlayerScene.boundsOfDescription(description: String) = headerNodes()
        .firstOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true }
        ?.boundsInRoot

    private fun hit(at: Offset) = titleBarHitTest(
        at.x.toInt(), at.y.toInt(), window.regions,
        TitleBarWindowState(width = 1280 * 2, resizeBorder = 16),
    )

    private fun PlayerScene.centreOfDescription(description: String): Offset? = nodes()
        .firstOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true }
        ?.boundsInRoot?.center

    private var eventMillis = 1_000L

    private fun PlayerScene.click(at: Offset) {
        send { sendPointerEvent(PointerEventType.Move, at, timeMillis = eventMillis) }
        eventMillis += 20
        send { sendPointerEvent(PointerEventType.Press, at, timeMillis = eventMillis, button = PointerButton.Primary) }
        eventMillis += 40
        send { sendPointerEvent(PointerEventType.Release, at, timeMillis = eventMillis, button = PointerButton.Primary) }
        eventMillis += 60
    }
}
