package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.window.WindowState
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real player in a fullscreen window: the header's toggle, Esc as the window routes it (a menu,
 * a sheet or the drawer first, then fullscreen), and the loop chips, which work as in a window:
 * select, rename, new loop.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class FullscreenPlayerTest {

    private val directories = AppDirectories(Files.createTempDirectory("downstep-fullscreen").toFile())
    private val koin = startKoin {
        modules(
            desktopModule(directories, NativeEngine.manual(44_100), InMemoryEffectsSettingsStore(), InMemorySupportPromptStore(),
                clickSettingsStore = InMemoryClickSettingsStore()),
            useCasesModule,
            uiModule,
        )
    }.koin
    private val typing = TextInputFocus()
    private val fullscreen = WindowFullscreen(WindowState(), CoroutineScope(Dispatchers.Unconfined))

    @AfterTest
    fun tearDown() {
        koin.get<DesktopPlaybackEngine>().close()
        stopKoin()
        directories.root.deleteRecursively()
    }

    @Test
    fun `the header's toggle enters fullscreen and then offers the way out`() = fullscreenScene(entered = false) { scene ->
        scene.click(assertNotNull(scene.centreOfDescription(ENTER), "no fullscreen toggle"))

        assertTrue(fullscreen.isFullscreen)
        assertNull(scene.centreOfDescription(ENTER))
        scene.click(assertNotNull(scene.centreOfDescription(EXIT), "no way out of fullscreen"))
        assertFalse(fullscreen.isFullscreen)
    }

    @Test
    fun `Esc closes the theme menu first, then leaves fullscreen`() = fullscreenScene { scene ->
        val shortcuts = PracticeShortcuts(scene.player, typing, fullscreen)
        scene.click(assertNotNull(scene.centreOfDescription("Theme"), "no theme button"))
        scene.frames(6, 80)
        assertNotNull(scene.centreOfText("Themes"), "the theme menu is not open")

        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyDown), shortcuts::handle)
        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyUp), shortcuts::handle)
        scene.frames(6, 80)
        assertNull(scene.centreOfText("Themes"), "Esc did not close the menu")
        assertTrue(fullscreen.isFullscreen, "Esc closed the menu and left fullscreen at once")

        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyDown), shortcuts::handle)
        assertFalse(fullscreen.isFullscreen)
    }

    @Test
    fun `Esc closes a dropdown menu first, then leaves fullscreen`() = fullscreenScene { scene ->
        val shortcuts = PracticeShortcuts(scene.player, typing, fullscreen)
        scene.click(assertNotNull(scene.centreOfDescription("Metronome and count-in settings"), "no click settings button"))
        scene.frames(6, 80)
        assertNotNull(scene.centreOfText("Click volume"), "the click settings menu is not open")

        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyDown), shortcuts::handle)
        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyUp), shortcuts::handle)
        scene.frames(6, 80)
        assertNull(scene.centreOfText("Click volume"), "Esc did not close the menu")
        assertTrue(fullscreen.isFullscreen, "Esc left fullscreen with the menu open")

        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyDown), shortcuts::handle)
        assertFalse(fullscreen.isFullscreen)
    }

    @Test
    fun `Esc closes the EQ sheet first, then leaves fullscreen`() = fullscreenScene { scene ->
        val shortcuts = PracticeShortcuts(scene.player, typing, fullscreen)
        scene.click(assertNotNull(scene.centreOfDescription("Equalizer"), "no EQ button"))
        scene.frames(10, 80)
        assertNotNull(scene.centreOfText("Effects"), "the EQ sheet is not open")

        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyDown), shortcuts::handle)
        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyUp), shortcuts::handle)
        scene.frames(10, 80)
        assertNull(scene.centreOfText("Effects"), "Esc did not close the sheet")
        assertTrue(fullscreen.isFullscreen, "Esc left fullscreen with the sheet open")

        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyDown), shortcuts::handle)
        assertFalse(fullscreen.isFullscreen)
    }

    @Test
    fun `Esc closes the library drawer first, then leaves fullscreen`() = fullscreenScene { scene ->
        val shortcuts = PracticeShortcuts(scene.player, typing, fullscreen)
        scene.steps({ it.onDrawerOpened() })

        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyDown), shortcuts::handle)
        assertFalse(scene.player.layout.value.isDrawerOpen)
        assertTrue(fullscreen.isFullscreen)

        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyDown), shortcuts::handle)
        assertFalse(fullscreen.isFullscreen)
    }

    @Test
    fun `in fullscreen a click on a chip makes its loop active`() = fullscreenScene { scene ->
        val first = scene.player.loopChips.value.chips.first()
        assertTrue(!first.isActive)

        scene.click(scene.chip(first.name))

        assertEquals(first.id, scene.player.loopChips.value.chips.single { it.isActive }.id)
    }

    @Test
    fun `in fullscreen a double click on a chip renames it in place, and Esc there stays fullscreen`() = fullscreenScene { scene ->
        val shortcuts = PracticeShortcuts(scene.player, typing, fullscreen)
        val active = scene.player.loopChips.value.chips.single { it.isActive }
        val at = scene.chip(active.name)

        scene.click(at)
        scene.click(at)
        assertTrue(typing.isTyping, "the name is edited, the shortcuts are off")
        scene.type("Riff")
        scene.key(Key.Enter)
        scene.frames(6, 80)
        assertEquals("Riff", scene.player.loopChips.value.chips.single { it.id == active.id }.name)
        assertTrue(!typing.isTyping)

        // Esc in the field cancels the rename and is taken there: fullscreen stays.
        scene.click(scene.chip("Riff"))
        scene.click(scene.chip("Riff"))
        assertTrue(typing.isTyping)
        scene.windowKey(KeyEvent(Key.Escape, KeyEventType.KeyDown), shortcuts::handle)
        scene.frames(6, 80)
        assertTrue(fullscreen.isFullscreen)
        assertTrue(!typing.isTyping)
    }

    @Test
    fun `in fullscreen the new loop chip adds a loop`() = fullscreenScene { scene ->
        val before = scene.player.loopChips.value.chips.size
        scene.steps({ it.onSeek(0.9f) })

        scene.click(scene.chip("New loop"))
        scene.frames(6, 80)

        assertEquals(before + 1, scene.player.loopChips.value.chips.size)
    }

    /** A 1920×1080 screen, whole: what a fullscreen window shows. */
    private fun fullscreenScene(entered: Boolean = true, block: (PlayerScene) -> Unit) {
        if (entered) fullscreen.enter()
        PlayerScene(koin, 1920, 1080, scale = 1, textInput = typing, fullscreen = fullscreen, root = { LeaveFullscreenOnBack(fullscreen, typing) }).use { scene ->
            scene.openTrackWithLoops()
            scene.capture()
            assertEquals(entered, fullscreen.isFullscreen)
            block(scene)
        }
    }

    private fun PlayerScene.chip(text: String): Offset = assertNotNull(centreOfText(text), "no chip \"$text\"")

    private fun PlayerScene.centreOfDescription(description: String): Offset? = nodes()
        .firstOrNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true }
        ?.boundsInRoot?.center

    // Event times of our own: on a slow machine the wall clock between two clicks can pass the
    // double-click timeout, and the second click would start a new gesture.
    private var eventMillis = 1_000L

    private fun PlayerScene.click(at: Offset) {
        send { sendPointerEvent(PointerEventType.Press, at, timeMillis = eventMillis, button = PointerButton.Primary) }
        eventMillis += 40
        send { sendPointerEvent(PointerEventType.Release, at, timeMillis = eventMillis, button = PointerButton.Primary) }
        eventMillis += 60
    }

    private fun PlayerScene.key(key: Key) {
        send { sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)) }
        send { sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp)) }
    }

    /** Characters reach a text field as AWT KEY_TYPED events, as from the keyboard. */
    private fun PlayerScene.type(text: String) {
        val source = java.awt.Canvas()
        for (char in text) {
            val typed = java.awt.event.KeyEvent(source, java.awt.event.KeyEvent.KEY_TYPED, 0L, 0, java.awt.event.KeyEvent.VK_UNDEFINED, char)
            send { sendKeyEvent(KeyEvent(Key.Unknown, KeyEventType.KeyDown, codePoint = char.code, nativeEvent = typed)) }
        }
    }

    private companion object {
        const val ENTER = "Fullscreen (F)"
        const val EXIT = "Exit fullscreen (Esc)"
    }
}
