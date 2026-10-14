package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel

/**
 * Keys for practising with the hands on the instrument:
 * Space play/pause · ←/→ 5 s back/forward (with ⇧ 1 s) · A loop start · B loop end · L loop on/off.
 *
 * Handled before the focused element, so Space never re-clicks the last button and the arrows never
 * move a slider. Only a focused text field keeps its keys ([TextInputFocus]).
 */
class PracticeShortcuts(private val player: PlayerViewModel, private val textInput: TextInputFocus) {

    private var isSpaceDown = false

    fun handle(event: KeyEvent): Boolean {
        if (textInput.isTyping) return false
        // Leave ⌘, ⌃ and ⌥ combinations to the system and the menus.
        if (event.isMetaPressed || event.isCtrlPressed || event.isAltPressed) return false
        if (event.key !in KEYS) return false
        when (event.type) {
            KeyEventType.KeyDown -> handle(event.key, fine = event.isShiftPressed)
            // Space held down repeats KeyDown: play/pause only once per press.
            KeyEventType.KeyUp -> if (event.key == Key.Spacebar) isSpaceDown = false
        }
        // KeyUp too: a focused button would otherwise click on it.
        return true
    }

    /** Acts on [key]; [fine] moves 1 s instead of 5. Returns false if it is not a practice key. */
    fun handle(key: Key, fine: Boolean = false): Boolean {
        when (key) {
            Key.Spacebar -> {
                if (isSpaceDown) return true
                isSpaceDown = true
                player.onPlayStopClicked()
            }
            Key.DirectionLeft -> player.onNudge(-(if (fine) FINE_NUDGE_SECONDS else NUDGE_SECONDS))
            Key.DirectionRight -> player.onNudge(if (fine) FINE_NUDGE_SECONDS else NUDGE_SECONDS)
            Key.A -> player.onMarkLoopStartClicked()
            Key.B -> player.onMarkLoopEndClicked()
            Key.L -> player.onLoopToggled(!player.state.value.controls.isLoopOn)
            else -> return false
        }
        return true
    }

    /** Space released, for callers that drive [handle] by key (tests). */
    fun onSpaceReleased() {
        isSpaceDown = false
    }

    private companion object {
        val KEYS = setOf(Key.Spacebar, Key.DirectionLeft, Key.DirectionRight, Key.A, Key.B, Key.L)
        const val NUDGE_SECONDS = 5f
        const val FINE_NUDGE_SECONDS = 1f
    }
}
