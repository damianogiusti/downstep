package com.damianogiusti.slowdowner.desktop

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences
import kotlin.math.roundToInt

/** The window's last bounds in the user's Java preferences, next to the theme and effects settings. */
class WindowBoundsStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/window"),
) {

    /** Null before the first run, or when a value is missing or unreadable. */
    fun read(): WindowBounds? {
        val width = preferences.getInt(KEY_WIDTH, 0)
        val height = preferences.getInt(KEY_HEIGHT, 0)
        if (width <= 0 || height <= 0 || preferences.get(KEY_X, null) == null || preferences.get(KEY_Y, null) == null) return null
        return WindowBounds(
            x = preferences.getInt(KEY_X, 0),
            y = preferences.getInt(KEY_Y, 0),
            width = width,
            height = height,
            isMaximized = preferences.getBoolean(KEY_MAXIMIZED, false),
        )
    }

    fun write(bounds: WindowBounds) {
        preferences.putInt(KEY_X, bounds.x)
        preferences.putInt(KEY_Y, bounds.y)
        preferences.putInt(KEY_WIDTH, bounds.width)
        preferences.putInt(KEY_HEIGHT, bounds.height)
        preferences.putBoolean(KEY_MAXIMIZED, bounds.isMaximized)
        preferences.flush()
    }

    private companion object {
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_MAXIMIZED = "maximized"
    }
}

/**
 * Saves where the window is a moment after it stops moving or resizing, so the next run opens there.
 * Runs for the window's life. A maximized window keeps the size it had before, to restore to.
 *
 * Fullscreen is never saved, so it is never the startup state: the next run opens the window as it was
 * before going fullscreen ([WindowFullscreen]). The states that are not fullscreen are picked before
 * the pause, so a move just before going fullscreen is still saved. [isWindowFullscreen] is the real
 * window's state: on macOS it stays fullscreen for a moment after the placement says otherwise, while
 * the OS animates the way out, and the window's size meanwhile is still the screen's.
 */
@OptIn(FlowPreview::class)
suspend fun WindowState.saveBoundsTo(
    store: WindowBoundsStore,
    initial: WindowBounds,
    isWindowFullscreen: () -> Boolean = { false },
    delayMillis: Long = WINDOW_SAVE_DELAY_MS,
) {
    var floating = initial
    snapshotFlow { Triple(placement, position, size) }
        .filter { (placement) -> placement != WindowPlacement.Fullscreen && !isWindowFullscreen() }
        .debounce(delayMillis)
        .collect { (placement, position, size) ->
            if (isMinimized) return@collect
            if (placement == WindowPlacement.Floating && position is WindowPosition.Absolute && size.isSpecified) {
                floating = WindowBounds(
                    position.x.value.roundToInt(),
                    position.y.value.roundToInt(),
                    size.width.value.roundToInt(),
                    size.height.value.roundToInt(),
                )
            }
            val bounds = floating.copy(isMaximized = placement == WindowPlacement.Maximized)
            withContext(Dispatchers.IO) { runCatching { store.write(bounds) } }
        }
}

private const val WINDOW_SAVE_DELAY_MS = 500L
