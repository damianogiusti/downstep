package com.damianogiusti.slowdowner.desktop

import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.damianogiusti.slowdowner.ui.common.FullscreenControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The window's fullscreen: the header's toggle, F and F11 turn it on and off, Esc leaves it. The
 * state is the window's own placement ([WindowState.placement] = [WindowPlacement.Fullscreen]),
 * which Compose implements per OS and keeps in step with the OS:
 * - Windows (and Linux): AWT's full-screen window, which covers the whole screen, taskbar included,
 *   without the title bar. Like any full-screen window there, it is minimized when another app comes
 *   to the front, and is fullscreen again when brought back.
 * - macOS: the native fullscreen of the green button (`toggleFullScreen:`), in its own Space, so the
 *   button, the menu item, ⌃⌘F, F and F11 all toggle the same thing (see [useTransparentTitleBar],
 *   which makes the window fullscreenable).
 *
 * Fullscreen entered or left by the OS (the green button, a gesture, a Space switch) resizes the
 * window, and Compose updates [WindowState.placement] from that resize: [isFullscreen] reads it, so
 * the header's toggle follows. [trackRestorePlacement] remembers the last placement that was not
 * fullscreen, whoever entered it, and leaving goes back to it: floating at the bounds it had (the OS
 * restores them), or maximized.
 *
 * Fullscreen is never the startup state: the saved bounds are those of the window before it went
 * fullscreen (see Main.kt's saveBoundsTo), so the next run opens as the window was. A practice app
 * that starts covering the screen, on a screen the user may not want covered (a second monitor, a
 * shared desk), surprises more than F does once.
 *
 * [awtPlacement] is the real window's placement, which may lag behind [WindowState.placement] while
 * the OS animates (macOS). Main thread only.
 */
@Stable
class WindowFullscreen(
    private val state: WindowState,
    private val scope: CoroutineScope,
    private val awtPlacement: () -> WindowPlacement? = { null },
) : FullscreenControl {

    /** Where leaving fullscreen goes: floating or maximized, as the window was before. */
    var restorePlacement: WindowPlacement =
        state.placement.takeIf { it != WindowPlacement.Fullscreen } ?: WindowPlacement.Floating
        private set

    private var leaving: Job? = null

    override val isFullscreen: Boolean get() = state.placement == WindowPlacement.Fullscreen

    override fun toggle() {
        if (isFullscreen) exit() else enter()
    }

    fun enter() {
        if (isFullscreen) return
        leaving?.cancel()
        restorePlacement = state.placement
        state.placement = WindowPlacement.Fullscreen
    }

    /** Back to [restorePlacement]. False when the window was not fullscreen: Esc then does nothing. */
    fun exit(): Boolean {
        if (!isFullscreen) return false
        val target = restorePlacement
        // Out of fullscreen first: Compose's Maximized only maximizes, a full-screen window stays so.
        state.placement = WindowPlacement.Floating
        if (target == WindowPlacement.Maximized) {
            leaving = scope.launch {
                // Once the window has really left (macOS animates it), maximize it.
                withTimeoutOrNull(LEAVE_TIMEOUT_MS) {
                    while (awtPlacement() == WindowPlacement.Fullscreen) delay(POLL_MS)
                }
                if (state.placement == WindowPlacement.Floating) state.placement = WindowPlacement.Maximized
            }
        }
        return true
    }

    /** Remembers every placement that is not fullscreen, from the toggle or the OS. Runs for the window's life. */
    suspend fun trackRestorePlacement() {
        snapshotFlow { state.placement }.collect { placement ->
            // A leave on its way to Maximized passes through Floating: that is not where to go back.
            if (placement != WindowPlacement.Fullscreen && leaving?.isActive != true) restorePlacement = placement
        }
    }

    private companion object {
        const val POLL_MS = 50L
        const val LEAVE_TIMEOUT_MS = 2_000L
    }
}
