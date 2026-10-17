package com.damianogiusti.slowdowner.ui.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.loops.Loop
import com.damianogiusti.slowdowner.ui.common.LocalTextInputFocus
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.common.clearsFocusOnPress
import com.damianogiusti.slowdowner.ui.player.LoopChipActions
import com.damianogiusti.slowdowner.ui.player.LoopChipModel
import com.damianogiusti.slowdowner.ui.player.LoopChipsState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A double click on a loop chip edits its name in place; the shortcuts are off meanwhile. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class LoopChipRenameTest {

    private val typing = TextInputFocus()
    private val selects = mutableListOf<Long>()
    private val renames = mutableListOf<Pair<Long, String>>()
    private val actions = LoopChipActions(
        onSelect = { selects += it },
        onNewLoop = {},
        onRename = { id, name -> renames += id to name },
        onDelete = {},
        onSuggestionAccepted = {},
        onSuggestionDismissed = {},
    )
    private val chips = LoopChipsState(
        chips = listOf(
            LoopChipModel(id = 1, name = "Solo", colorIndex = 0, lengthSeconds = 8f, passes = 0, isActive = true),
            LoopChipModel(id = 2, name = "Bridge", colorIndex = 1, lengthSeconds = 12f, passes = 0, isActive = false),
        ),
        enabled = true,
    )

    @Test
    fun `when a chip is clicked once then it is selected and not edited`() = scene {
        click(ON_SOLO)

        assertEquals(listOf(1L), selects)
        assertFalse(typing.isTyping)
    }

    @Test
    fun `when a chip is double clicked then its name is edited and the shortcuts are off`() = scene {
        doubleClick(ON_SOLO)

        assertTrue(typing.isTyping)
        assertEquals(listOf(1L), selects, "only the first click selects")
        assertTrue(renames.isEmpty())
    }

    @Test
    fun `when a new name is typed and Enter is pressed then it is saved once`() = scene {
        doubleClick(ON_SOLO)
        type("Riff")
        key(Key.Enter)

        assertEquals(listOf(1L to "Riff"), renames)
        assertFalse(typing.isTyping)
        click(OUTSIDE)
        assertEquals(1, renames.size, "the blur after Enter saves nothing more")
    }

    @Test
    fun `when Esc is pressed then the edit is cancelled`() = scene {
        doubleClick(ON_SOLO)
        type("Riff")
        key(Key.Escape)

        assertTrue(renames.isEmpty())
        assertFalse(typing.isTyping)
    }

    @Test
    fun `when the field is left then the new name is saved`() = scene {
        doubleClick(ON_SOLO)
        type("Riff")
        click(OUTSIDE)

        assertEquals(listOf(1L to "Riff"), renames)
        assertFalse(typing.isTyping)
    }

    @Test
    fun `when the name is emptied and the field left then the old name stays`() = scene {
        doubleClick(ON_SOLO)
        type("Riff")
        repeat(4) { key(Key.Backspace) }
        click(OUTSIDE)

        assertTrue(renames.isEmpty())
        assertFalse(typing.isTyping)
    }

    @Test
    fun `when the field is clicked while editing then editing goes on`() = scene {
        doubleClick(ON_SOLO)
        click(IN_FIELD)

        assertTrue(typing.isTyping, "the press cleared the focus, the field took it back")
        assertTrue(renames.isEmpty())
        type("Riff")
        key(Key.Enter)
        assertEquals(1, renames.size)
    }

    @Test
    fun `when a name is saved then it is trimmed, capped, and an empty or unchanged one is kept`() {
        assertEquals("Riff", loopNameEdit("Solo", "  Riff ", save = true))
        assertEquals(Loop.MAX_NAME_LENGTH, loopNameEdit("Solo", "x".repeat(60), save = true)?.length)
        assertNull(loopNameEdit("Solo", "   ", save = true))
        assertNull(loopNameEdit("Solo", "Solo ", save = true))
        assertNull(loopNameEdit("Solo", "Riff", save = false))
    }

    private fun scene(block: ImageComposeScene.() -> Unit) {
        val scene = ImageComposeScene(width = 600, height = 300, density = Density(1f)) {
            CompositionLocalProvider(LocalTextInputFocus provides typing) {
                SlowDownerTheme {
                    // As on the player screen: a press anywhere first takes the focus away.
                    Box(modifier = Modifier.fillMaxSize().clearsFocusOnPress()) {
                        Column {
                            LoopChipsStrip(chips, emptyList(), actions)
                            Spacer(Modifier.height(200.dp))
                        }
                    }
                }
            }
        }
        scene.render(0)
        try {
            scene.block()
        } finally {
            scene.close()
        }
    }

    private var frame = 0L

    private fun ImageComposeScene.next() {
        frame += 16_000_000L
        render(frame)
    }

    private fun ImageComposeScene.click(at: Offset) {
        sendPointerEvent(PointerEventType.Press, at, button = PointerButton.Primary)
        next()
        sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
        next()
        next()
    }

    private fun ImageComposeScene.doubleClick(at: Offset) {
        click(at)
        click(at)
        next()
    }

    private fun ImageComposeScene.key(key: Key) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        next()
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        next()
        next()
    }

    /** Characters reach a text field as AWT KEY_TYPED events, as from the keyboard. */
    private fun ImageComposeScene.type(text: String) {
        for (char in text) {
            val typed = java.awt.event.KeyEvent(source, java.awt.event.KeyEvent.KEY_TYPED, 0L, 0, java.awt.event.KeyEvent.VK_UNDEFINED, char)
            sendKeyEvent(KeyEvent(Key.Unknown, KeyEventType.KeyDown, codePoint = char.code, nativeEvent = typed))
            next()
        }
        next()
    }

    private val source = java.awt.Canvas()

    private companion object {
        // The strip's padding is 24 px left and 12 px top; chips are 34 px high.
        val ON_SOLO = Offset(50f, 29f)
        val IN_FIELD = Offset(60f, 29f)
        val OUTSIDE = Offset(300f, 250f)
    }
}
