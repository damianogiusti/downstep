package com.damianogiusti.slowdowner.desktop

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.WindowListener

/**
 * Windows only: a fullscreen window that its own file pickers, or another app, do not minimize.
 *
 * Compose's fullscreen there is AWT's full-screen exclusive mode (`setFullScreenWindow`). With Java2D's
 * GDI pipeline (Direct3D unavailable or turned off: remote desktop, some virtual machines and GPUs) the
 * JDK adds a listener (`Win32GraphicsDevice$Win32FSWindowAdapter`) that minimizes the window whenever
 * it loses the focus to a window that is not itself full-screen. That includes the app's own pickers
 * (import, backup, batch export: a FileDialog or a JFileChooser owned by the window), which would be
 * minimized with it, and any click in another app on a second monitor. Without that listener (and the
 * topmost it may come with), the window stays fullscreen, an owned dialog shows over it, and another
 * app simply comes in front of it, as with a browser's F11.
 *
 * With the Direct3D pipeline (most machines) the JDK adds no such listener and the pickers show over
 * the fullscreen window as they should; Windows itself still minimizes a Direct3D exclusive window
 * when another app takes the focus, which this cannot change.
 *
 * Runs for the window's life: each time the window enters fullscreen, once the JDK has added its
 * listener (it does so with `invokeLater`), the listener is removed and the always-on-top undone.
 */
suspend fun Window.keepFullscreenOnFocusLoss(state: WindowState) {
    snapshotFlow { state.placement }.collectLatest { placement ->
        if (placement != WindowPlacement.Fullscreen) return@collectLatest
        // Compose applies the placement on a later frame: wait for the device to have the window.
        val entered = withTimeoutOrNull(ENTER_TIMEOUT_MS) {
            while (graphicsConfiguration?.device?.fullScreenWindow !== this@keepFullscreenOnFocusLoss) delay(POLL_MS)
        }
        // Queued after the JDK's own invokeLater, posted when the window became full-screen.
        if (entered != null) EventQueue.invokeLater { removeFullscreenFocusLossHandling() }
    }
}

/**
 * Removes the JDK's minimize-on-focus-loss listeners ([isFocusLossHandler]) and the always-on-top that
 * comes with them. True if there were any. Main thread only.
 */
fun Window.removeFullscreenFocusLossHandling(isFocusLossHandler: (WindowListener) -> Boolean = ::isWin32FullscreenAdapter): Boolean {
    val handlers = windowListeners.filter(isFocusLossHandler)
    if (handlers.isEmpty()) return false
    handlers.forEach(::removeWindowListener)
    // The JDK made the native window topmost behind Window's back (isAlwaysOnTop still says false):
    // through true, so that false reaches the native window.
    if (!isAlwaysOnTop) {
        isAlwaysOnTop = true
        isAlwaysOnTop = false
    }
    return true
}

val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

private fun isWin32FullscreenAdapter(listener: WindowListener): Boolean = listener.javaClass.name == WIN32_FS_ADAPTER

private const val WIN32_FS_ADAPTER = "sun.awt.Win32GraphicsDevice\$Win32FSWindowAdapter"
private const val POLL_MS = 50L
private const val ENTER_TIMEOUT_MS = 3_000L
