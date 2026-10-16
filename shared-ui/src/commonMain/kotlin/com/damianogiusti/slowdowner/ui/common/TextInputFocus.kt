package com.damianogiusti.slowdowner.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Whether the user is typing. Desktop keyboard shortcuts (Space, arrows) take over every other
 * focused element, but must leave a focused text field alone. Main thread only.
 */
class TextInputFocus {
    private var focusedFields = 0

    val isTyping: Boolean get() = focusedFields > 0

    internal fun onFieldFocusChanged(gained: Boolean) {
        focusedFields = (focusedFields + if (gained) 1 else -1).coerceAtLeast(0)
    }
}

/** Null on Android: there are no window-level shortcuts to protect. */
val LocalTextInputFocus = compositionLocalOf<TextInputFocus?> { null }

/**
 * Put on a screen's root: a press anywhere in it takes the focus away from a text field, as a click
 * outside a browser's textarea does, so the shortcuts come back. Only buttons and chips take the focus
 * themselves; the waveforms, sliders and the background do not. The press is not consumed, and a
 * press on a text field focuses it again in the same gesture.
 */
fun Modifier.clearsFocusOnPress(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    pointerInput(focusManager) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            focusManager.clearFocus()
        }
    }
}

/** Put on a text field: Esc leaves it, and the next Esc reaches the window's shortcuts. */
fun Modifier.leavesOnEscape(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (event.key != Key.Escape || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        focusManager.clearFocus()
        true
    }
}

/** Put on every text field, so [TextInputFocus] knows when the user is typing. */
fun Modifier.reportsTextInputFocus(): Modifier = composed {
    val tracker = LocalTextInputFocus.current ?: return@composed this
    val isFocused = remember { booleanArrayOf(false) }
    // A field can leave the screen while focused (search closed): it must not count any more.
    DisposableEffect(tracker) {
        onDispose {
            if (isFocused[0]) tracker.onFieldFocusChanged(gained = false)
            isFocused[0] = false
        }
    }
    onFocusChanged { state ->
        if (state.isFocused != isFocused[0]) {
            isFocused[0] = state.isFocused
            tracker.onFieldFocusChanged(state.isFocused)
        }
    }
}
