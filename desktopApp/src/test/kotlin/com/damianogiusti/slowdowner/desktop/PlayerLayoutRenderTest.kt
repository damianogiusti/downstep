package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.ui.player.ExportUiState
import com.damianogiusti.slowdowner.ui.player.PlayerTags
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real player at the window sizes of common screens, with an active loop so Progression and Notes
 * show: the controls sit at the bottom, fully visible, and nothing scrolls. PNGs land in
 * build/screenshots/layout-<w>x<h>[-export].png, sized as the player's area: the window less its frame
 * (title bar, and for a window that is not maximized Windows' invisible resize borders). Fullscreen is
 * the same layout on the whole screen, with no frame at all: build/screenshots/fullscreen-<w>x<h>.png.
 */
class PlayerLayoutRenderTest {

    private val directories = AppDirectories(Files.createTempDirectory("downstep-layout-render").toFile())
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
    fun `a maximized window on a 1280x720 screen fits`() = assertFits(maximizedOn(1280, 720))

    @Test
    fun `a maximized window on a 1366x768 screen fits`() = assertFits(maximizedOn(1366, 768))

    @Test
    fun `a maximized window on a 1920x1080 screen fits`() = assertFits(maximizedOn(1920, 1080))

    @Test
    fun `the default window on a 1280x720 screen fits`() = assertFits(defaultOn(DpSize(1280.dp, 720.dp - TASKBAR)))

    @Test
    fun `the default window on a 1366x768 screen fits`() = assertFits(defaultOn(DpSize(1366.dp, 768.dp - TASKBAR)))

    @Test
    fun `an export running on the design-size window leaves the cards unscrolled`() =
        render(defaultOn(DpSize(1920.dp, 1080.dp - TASKBAR)), cardsMayScroll = false, export = true)

    @Test
    fun `the default window on a 1280x800 Mac screen fits`() = assertFits(defaultOn(DpSize(1280.dp, MAC_USABLE_HEIGHT)))

    @Test
    fun `the default window on a large screen is the design size and fits`() {
        assertEquals(DesignWindowSize, defaultWindowSize(DpSize(1920.dp, 1080.dp - TASKBAR)))
        assertFits(defaultOn(DpSize(1920.dp, 1080.dp - TASKBAR)))
    }

    @Test
    fun `the minimum window keeps every control on screen`() {
        val area = Area(
            MinimumWindowSize.width.value.roundToInt() - SIDE_BORDERS,
            MinimumWindowSize.height.value.roundToInt() - TITLE_BAR - BOTTOM_BORDER,
            "minimum",
        )
        // Narrower than 1240dp with Progression and Notes up, the cards may scroll a little; the controls never.
        render(area, cardsMayScroll = true)
    }

    @Test
    fun `a tall window keeps the controls at the bottom`() = render(Area(1280, 1200, "tall"), cardsMayScroll = false)

    @Test
    fun `fullscreen on a 1920x1080 screen gives the waveforms the room`() =
        assertFullscreen(1920, 1080, minOverview = 380f, minZoom = 140f)

    @Test
    fun `fullscreen on a 2560x1440 screen gives the waveforms the room`() =
        assertFullscreen(2560, 1440, minOverview = 600f, minZoom = 190f)

    @Test
    fun `fullscreen on a 1366x768 screen fits`() = assertFullscreen(1366, 768, minOverview = 160f, minZoom = 90f)

    private fun assertFits(area: Area) = render(area, cardsMayScroll = false)

    /** The whole screen, no taskbar and no title bar: the same layout, its waveforms grown into the room. */
    private fun assertFullscreen(width: Int, height: Int, minOverview: Float, minZoom: Float) {
        render(Area(width, height, "fullscreen"), cardsMayScroll = false, fullscreen = true) { scene, what ->
            val overview = assertNotNull(scene.boundsOf(PlayerTags.OVERVIEW), "$what: no overview")
            assertTrue(overview.height >= minOverview, "$what: the overview is ${overview.height}dp")
            val controls = assertNotNull(scene.boundsOf(PlayerTags.CONTROLS))
            val transport = assertNotNull(scene.boundsOf(PlayerTags.TRANSPORT))
            // Below the overview: the zoom strip, down to the transport.
            assertTrue(transport.top - overview.bottom >= minZoom, "$what: the zoom strip is ${transport.top - overview.bottom}dp")
            assertTrue(controls.top >= 0f)
        }
    }

    private fun render(
        area: Area,
        cardsMayScroll: Boolean,
        export: Boolean = false,
        fullscreen: Boolean = false,
        extra: (PlayerScene, String) -> Unit = { _, _ -> },
    ) {
        val variant = if (export) "export" else null
        val prefix = if (fullscreen) "fullscreen" else "layout"
        val name = "$prefix-${area.width}x${area.height}${variant?.let { "-$it" } ?: ""}.png"
        val what = "${area.label} ${area.width}x${area.height}${variant?.let { " $it" } ?: ""}"
        PlayerScene(koin, area.width, area.height, fullscreen = TestFullscreen(isFullscreen = fullscreen)).use { scene ->
            scene.openTrackWithLoops()
            if (export) scene.steps({ it.exportState().value = ExportUiState.Running(0.4f, fileCount = 4) })
            scene.capture(File("build/screenshots/$name"))
            if (export) assertIs<ExportUiState.Running>(scene.player.export.value, "$what: the export is over")

            val transport = assertNotNull(scene.boundsOf(PlayerTags.TRANSPORT), "$what: no transport")
            val controls = assertNotNull(scene.boundsOf(PlayerTags.CONTROLS), "$what: no controls")
            assertTrue(transport.top >= 0f && transport.height >= MIN_TRANSPORT_HEIGHT, "$what: the transport is cut, $transport")
            assertNear(area.height.toFloat(), transport.bottom, "$what: the transport is not at the bottom")
            assertNear(area.height.toFloat(), controls.bottom, "$what: the controls are not at the bottom")
            val cards = assertNotNull(scene.boundsOf(PlayerTags.CARDS), "$what: no cards")
            assertTrue(cards.bottom <= controls.top + 0.5f, "$what: the cards overlap the controls")
            val range = assertNotNull(scene.scrollRangeOf(PlayerTags.CARDS), "$what: the cards tell no scroll range")
            if (!cardsMayScroll) assertEquals(0f, range, "$what: the cards scroll by ${range / scene.scale}dp")
            // The loop chips, in the block at the bottom.
            val chip = scene.player.loopChips.value.chips.first().name
            val chipCentre = assertNotNull(scene.centreOfText(chip), "$what: no loop chip")
            assertTrue(chipCentre.y / scene.scale in controls.top..controls.bottom, "$what: the chips are not with the controls")
            extra(scene, what)
        }
    }

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) <= 0.5f, "$message: $actual, expected $expected")

    /** The player's area in dp: what a window leaves under its title bar. */
    private data class Area(val width: Int, val height: Int, val label: String)

    private fun maximizedOn(screenWidth: Int, screenHeight: Int) =
        Area(screenWidth, screenHeight - TASKBAR.value.roundToInt() - TITLE_BAR, "maximized on ${screenWidth}x$screenHeight")

    private fun defaultOn(usable: DpSize): Area {
        val window = defaultWindowSize(usable)
        return Area(
            window.width.value.roundToInt() - SIDE_BORDERS,
            window.height.value.roundToInt() - TITLE_BAR - BOTTOM_BORDER,
            "default on ${usable.width.value.roundToInt()}x${usable.height.value.roundToInt()}",
        )
    }

    // A real export of the 30 s track is over before the frame is taken: hold it at 40%.
    @Suppress("UNCHECKED_CAST")
    private fun PlayerViewModel.exportState(): MutableStateFlow<ExportUiState> =
        PlayerViewModel::class.java.getDeclaredField("exportState").apply { isAccessible = true }.get(this) as MutableStateFlow<ExportUiState>

    private companion object {
        /** Windows 11's taskbar at 100%. */
        val TASKBAR = 48.dp
        /** A 1280×800 MacBook without its menu bar and Dock. */
        val MAC_USABLE_HEIGHT = 710.dp
        /** Windows' title bar. macOS draws its own over the content, and the app leaves 28dp free there. */
        const val TITLE_BAR = 32
        /**
         * Windows' invisible resize borders, which AWT counts in a window's size: left, right and
         * bottom, 8dp each. A maximized window has none.
         */
        const val BOTTOM_BORDER = 8
        const val SIDE_BORDERS = 16
        const val MIN_TRANSPORT_HEIGHT = 68f
    }
}
