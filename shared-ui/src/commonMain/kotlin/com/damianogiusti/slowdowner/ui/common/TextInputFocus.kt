package com.damianogiusti.slowdowner.ui.common

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

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
