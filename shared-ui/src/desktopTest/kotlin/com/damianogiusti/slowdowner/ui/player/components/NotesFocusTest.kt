package com.damianogiusti.slowdowner.ui.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import com.damianogiusti.slowdowner.ui.common.LocalTextInputFocus
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.common.clearsFocusOnPress
import com.damianogiusti.slowdowner.ui.player.NotesActions
import com.damianogiusti.slowdowner.ui.player.NotesCardState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The shortcuts are off while Notes has the focus: a click elsewhere or Esc must give them back. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class NotesFocusTest {

    private val typing = TextInputFocus()

    @Test
    fun `a click outside the notes ends typing`() = scene {
        click(IN_NOTES)
        assertTrue(typing.isTyping)

        click(OUTSIDE)
        assertFalse(typing.isTyping)

        click(IN_NOTES)
        assertTrue(typing.isTyping, "a click on the field focuses it again")
    }

    @Test
    fun `Esc leaves the notes`() = scene {
        click(IN_NOTES)
        assertTrue(typing.isTyping)

        sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
        render(0)

        assertFalse(typing.isTyping)
    }

    private fun scene(block: ImageComposeScene.() -> Unit) {
        val scene = ImageComposeScene(width = 400, height = 500, density = Density(1f)) {
            CompositionLocalProvider(LocalTextInputFocus provides typing) {
                SlowDownerTheme {
                    Box(modifier = Modifier.fillMaxSize().clearsFocusOnPress()) {
                        Column {
                            NotesCard(NotesCardState(1, "Solo", ""), NotesActions { _, _ -> }, Modifier.size(400.dp, 300.dp))
                            // Like the waveforms or a card's background: takes no focus of its own.
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

    private fun ImageComposeScene.click(at: Offset) {
        sendPointerEvent(PointerEventType.Press, at, button = PointerButton.Primary)
        render(0)
        sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
        render(0)
    }

    private companion object {
        val IN_NOTES = Offset(200f, 180f)
        val OUTSIDE = Offset(200f, 420f)
    }
}
