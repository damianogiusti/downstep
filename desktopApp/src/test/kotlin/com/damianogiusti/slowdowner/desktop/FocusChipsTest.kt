package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Focus mode shows the loop chips too, and they work as in the full player: select, rename, new loop. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class FocusChipsTest {

    private val directories = AppDirectories(Files.createTempDirectory("downstep-focus-chips").toFile())
    private val koin = startKoin {
        modules(
            desktopModule(directories, NativeEngine.manual(44_100), InMemoryEffectsSettingsStore(), InMemorySupportPromptStore(),
                clickSettingsStore = InMemoryClickSettingsStore()),
            useCasesModule,
            uiModule,
        )
    }.koin
    private val typing = TextInputFocus()

    @AfterTest
    fun tearDown() {
        koin.get<DesktopPlaybackEngine>().close()
        stopKoin()
        directories.root.deleteRecursively()
    }

    @Test
    fun `in focus mode a click on a chip makes its loop active`() = focusScene { scene ->
        val first = scene.player.loopChips.value.chips.first()
        assertTrue(!first.isActive)

        scene.click(scene.chip(first.name))

        assertEquals(first.id, scene.player.loopChips.value.chips.single { it.isActive }.id)
    }

    @Test
    fun `in focus mode a double click on a chip renames it in place`() = focusScene { scene ->
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
    }

    @Test
    fun `in focus mode the new loop chip adds a loop`() = focusScene { scene ->
        val before = scene.player.loopChips.value.chips.size
        scene.steps({ it.onSeek(0.9f) })

        scene.click(scene.chip("New loop"))
        scene.frames(6, 80)

        assertEquals(before + 1, scene.player.loopChips.value.chips.size)
    }

    private fun focusScene(block: (PlayerScene) -> Unit) {
        PlayerScene(koin, 1280, 720, scale = 1, textInput = typing).use { scene ->
            scene.openTrackWithLoops()
            scene.steps({ it.onFocusToggled() })
            scene.capture()
            assertTrue(scene.player.layout.value.isFocusMode)
            block(scene)
        }
    }

    private fun PlayerScene.chip(text: String): Offset = assertNotNull(centreOfText(text), "no chip \"$text\" in focus mode")

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
}
