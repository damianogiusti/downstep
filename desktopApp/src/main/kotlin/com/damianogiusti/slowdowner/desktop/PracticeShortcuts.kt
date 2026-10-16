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
import androidx.compose.ui.input.key.utf16CodePoint
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel

/**
 * Keys for practising with the hands on the instrument (README §3, "Keyboard"):
 * Space play/pause · L new loop at the playhead · ⇧L loop on/off · A / B active loop start / end at
 * the playhead · [ / ] previous / next loop · ←/→ 5 s back/forward (with ⇧ 1 s) · F focus mode ·
 * Esc close the drawer, then leave focus.
 *
 * Handled before the focused element, so Space never re-clicks the last button and the arrows never
 * move a slider. Only a focused text field (notes, search, BPM) keeps its keys ([TextInputFocus]).
 */
class PracticeShortcuts(private val player: PlayerViewModel, private val textInput: TextInputFocus) {

    /** Keys held down: auto-repeat must not add a loop per repeat, or toggle focus back and forth. */
    private val held = mutableSetOf<Key>()

    /** Physical keys that typed [ or ] on a layout without bracket keys, to match their release. */
    private val bracketsByChar = mutableMapOf<Key, Key>()

    fun handle(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyUp) return onKeyUp(event)
        if (event.type != KeyEventType.KeyDown || !isPracticeChord(event)) return false
        val key = event.practiceKey() ?: return false
        return handle(key, shift = event.isShiftPressed)
    }

    /** Acts on [key]; [shift] = ⇧ held. Returns false if it is not a practice key or did nothing. */
    fun handle(key: Key, shift: Boolean = false): Boolean {
        if (key in REPEATING) {
            when (key) {
                Key.DirectionLeft -> player.onSeekBy(-(if (shift) FINE_SEEK_SECONDS else SEEK_SECONDS))
                Key.DirectionRight -> player.onSeekBy(if (shift) FINE_SEEK_SECONDS else SEEK_SECONDS)
            }
            return true
        }
        if (key !in ONCE) return false
        if (!held.add(key)) return true
        when (key) {
            Key.Spacebar -> player.onPlayStopClicked()
            Key.L -> if (shift) player.onLoopToggled() else player.onNewLoopAtPlayhead()
            Key.A -> player.onSetLoopStartAtPlayhead()
            Key.B -> player.onSetLoopEndAtPlayhead()
            Key.LeftBracket -> player.onPreviousLoopClicked()
            Key.RightBracket -> player.onNextLoopClicked()
            Key.F -> player.onFocusToggled()
            Key.Escape -> {
                // Released at once: Esc does nothing on repeat anyway, and dialogs get the next one.
                held -= key
                return player.onEscape()
            }
        }
        return true
    }

    /** Space released, for callers that drive [handle] by key (tests). */
    fun onSpaceReleased() {
        held -= Key.Spacebar
    }

    /** The window lost focus: the releases of the keys held now go to another app. */
    fun onFocusLost() {
        held.clear()
        bracketsByChar.clear()
    }

    private fun onKeyUp(event: KeyEvent): Boolean {
        // Always released, typing or not, ⌃ or not: a key left "held" would stop working for good.
        val key = bracketsByChar.remove(event.key) ?: event.key
        held -= key
        // A focused button would click on the release of Space. Esc's is left to dialogs.
        return !textInput.isTyping && isPracticeChord(event) && key != Key.Escape && (key in ONCE || key in REPEATING)
    }

    // Leave ⌘ and ⌃ / ⌥ combinations to the system and the menus. Both ⌃ and ⌥ is AltGr on Windows,
    // which some layouts (Italian) need for [ and ].
    private fun isPracticeChord(event: KeyEvent): Boolean =
        !textInput.isTyping && !event.isMetaPressed && event.isCtrlPressed == event.isAltPressed

    // Brackets by character as well: on many layouts they are not on the US bracket keys.
    private fun KeyEvent.practiceKey(): Key? {
        if (key in REPEATING || key in ONCE) return key
        val bracket = when (utf16CodePoint) {
            '['.code -> Key.LeftBracket
            ']'.code -> Key.RightBracket
            else -> return null
        }
        bracketsByChar[key] = bracket
        return bracket
    }

    private companion object {
        val REPEATING = setOf(Key.DirectionLeft, Key.DirectionRight)
        val ONCE = setOf(Key.Spacebar, Key.L, Key.A, Key.B, Key.LeftBracket, Key.RightBracket, Key.F, Key.Escape)
        const val SEEK_SECONDS = 5f
        const val FINE_SEEK_SECONDS = 1f
    }
}
