package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.ui.player.PlayerTags
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real player at the window sizes of common screens, with an active loop so Progression and Notes
 * show: the controls sit at the bottom, fully visible, and nothing scrolls. PNGs land in
 * build/screenshots/layout-<w>x<h>[-focus].png, sized as the player's area (window minus title bar).
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
    fun `the default window on a 1366x768 screen fits`() = assertFits(defaultOn(DpSize(1366.dp, 768.dp - TASKBAR)))

    @Test
    fun `the default window on a 1280x800 Mac screen fits`() = assertFits(defaultOn(DpSize(1280.dp, MAC_USABLE_HEIGHT)))

    @Test
    fun `the default window on a large screen is the design size and fits`() {
        assertEquals(DesignWindowSize, defaultWindowSize(DpSize(1920.dp, 1080.dp - TASKBAR)))
        assertFits(defaultOn(DpSize(1920.dp, 1080.dp - TASKBAR)))
    }

    @Test
    fun `the minimum window keeps every control on screen`() {
        val area = Area(MinimumWindowSize.width.value.roundToInt(), MinimumWindowSize.height.value.roundToInt() - TITLE_BAR, "minimum")
        // Narrower than 1240dp with Progression and Notes up, the cards may scroll a little; the controls never.
        render(area, focus = false, cardsMayScroll = true)
        render(area, focus = true, cardsMayScroll = false)
    }

    @Test
    fun `a tall window keeps the controls at the bottom`() = render(Area(1280, 1200, "tall"), focus = false, cardsMayScroll = false)

    private fun assertFits(area: Area) {
        render(area, focus = false, cardsMayScroll = false)
        render(area, focus = true, cardsMayScroll = false)
    }

    private fun render(area: Area, focus: Boolean, cardsMayScroll: Boolean) {
        val name = "layout-${area.width}x${area.height}${if (focus) "-focus" else ""}.png"
        val what = "${area.label} ${area.width}x${area.height}${if (focus) " focus" else ""}"
        PlayerScene(koin, area.width, area.height).use { scene ->
            scene.openTrackWithLoops()
            if (focus) scene.steps({ it.onFocusToggled() })
            scene.capture(File("build/screenshots/$name"))

            val transport = assertNotNull(scene.boundsOf(PlayerTags.TRANSPORT), "$what: no transport")
            val controls = assertNotNull(scene.boundsOf(PlayerTags.CONTROLS), "$what: no controls")
            assertTrue(transport.top >= 0f && transport.height >= MIN_TRANSPORT_HEIGHT, "$what: the transport is cut, $transport")
            assertNear(area.height.toFloat(), transport.bottom, "$what: the transport is not at the bottom")
            assertNear(area.height.toFloat(), controls.bottom, "$what: the controls are not at the bottom")
            if (focus) {
                assertEquals(0f, scene.scrollRangeOf(PlayerTags.FOCUS), "$what: focus mode scrolls")
                // Focus mode keeps the loop chips, in the block at the bottom.
                val chip = scene.player.loopChips.value.chips.first().name
                val chipCentre = assertNotNull(scene.centreOfText(chip), "$what: no loop chip")
                assertTrue(chipCentre.y / scene.scale in controls.top..controls.bottom, "$what: the chips are not with the controls")
            } else {
                val cards = assertNotNull(scene.boundsOf(PlayerTags.CARDS), "$what: no cards")
                assertTrue(cards.bottom <= controls.top + 0.5f, "$what: the cards overlap the controls")
                val range = assertNotNull(scene.scrollRangeOf(PlayerTags.CARDS), "$what: the cards tell no scroll range")
                if (!cardsMayScroll) assertEquals(0f, range, "$what: the cards scroll by ${range / scene.scale}dp")
            }
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
        return Area(window.width.value.roundToInt(), window.height.value.roundToInt() - TITLE_BAR, "default")
    }

    private companion object {
        /** Windows 11's taskbar at 100%. */
        val TASKBAR = 48.dp
        /** A 1280×800 MacBook without its menu bar and Dock. */
        val MAC_USABLE_HEIGHT = 710.dp
        /** Windows' title bar. macOS draws its own over the content, and the app leaves 28dp free there. */
        const val TITLE_BAR = 32
        const val MIN_TRANSPORT_HEIGHT = 68f
    }
}
