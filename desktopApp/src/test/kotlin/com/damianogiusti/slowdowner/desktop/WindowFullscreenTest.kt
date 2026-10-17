package com.damianogiusti.slowdowner.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The fullscreen toggle on the window's placement, and the bounds saved around it. */
class WindowFullscreenTest {

    private val node = Preferences.userRoot().node("com/damianogiusti/slowdowner/test/fullscreen-${UUID.randomUUID()}")

    @AfterTest
    fun tearDown() {
        node.removeNode()
    }

    private fun windowState(placement: WindowPlacement = WindowPlacement.Floating) = WindowState(
        placement = placement,
        position = WindowPosition(120.dp, 80.dp),
        size = DpSize(1100.dp, 720.dp),
    )

    @Test
    fun `entering from a floating window and leaving goes back to it`() {
        val state = windowState()
        val fullscreen = WindowFullscreen(state, CoroutineScope(Dispatchers.Unconfined))

        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen)
        assertEquals(WindowPlacement.Fullscreen, state.placement)
        assertEquals(WindowPlacement.Floating, fullscreen.restorePlacement)

        fullscreen.toggle()
        assertFalse(fullscreen.isFullscreen)
        assertEquals(WindowPlacement.Floating, state.placement)
    }

    @Test
    fun `leaving does nothing when the window is not fullscreen`() {
        val state = windowState(WindowPlacement.Maximized)
        val fullscreen = WindowFullscreen(state, CoroutineScope(Dispatchers.Unconfined))

        assertFalse(fullscreen.exit(), "Esc goes on")
        assertEquals(WindowPlacement.Maximized, state.placement)
    }

    @Test
    fun `a maximized window is maximized again once the window has really left fullscreen`() = runBlocking {
        val state = windowState(WindowPlacement.Maximized)
        // The real window: still fullscreen while macOS animates the way out.
        var awt: WindowPlacement = WindowPlacement.Maximized
        val fullscreen = WindowFullscreen(state, this) { awt }

        fullscreen.enter()
        awt = WindowPlacement.Fullscreen
        assertEquals(WindowPlacement.Maximized, fullscreen.restorePlacement)

        assertTrue(fullscreen.exit())
        // Out of fullscreen first: Maximized alone keeps a full-screen window as it is.
        assertEquals(WindowPlacement.Floating, state.placement)
        assertFalse(fullscreen.isFullscreen, "the toggle shows the window leaving")
        delay(200)
        assertEquals(WindowPlacement.Floating, state.placement, "maximized while still animating")

        awt = WindowPlacement.Floating
        delay(200)
        assertEquals(WindowPlacement.Maximized, state.placement)
    }

    @Test
    fun `entering again while leaving to a maximized window keeps maximized as the way back`() = runBlocking {
        val state = windowState(WindowPlacement.Maximized)
        var awt: WindowPlacement = WindowPlacement.Maximized
        val fullscreen = WindowFullscreen(state, this) { awt }
        val tracking = launch { fullscreen.trackRestorePlacement() }
        settle()

        fullscreen.enter()
        awt = WindowPlacement.Fullscreen
        settle()
        assertTrue(fullscreen.exit())
        settle()
        // Out of the animation, but not maximized yet: F again.
        awt = WindowPlacement.Floating
        fullscreen.enter()
        awt = WindowPlacement.Fullscreen
        settle()
        assertTrue(fullscreen.isFullscreen)
        assertEquals(WindowPlacement.Maximized, fullscreen.restorePlacement, "the floating step is not where to go back")

        assertTrue(fullscreen.exit())
        awt = WindowPlacement.Floating
        delay(200)
        assertEquals(WindowPlacement.Maximized, state.placement)
        tracking.cancelAndJoin()
    }

    @Test
    fun `a press while the window is still animating out of fullscreen is dropped`() = runBlocking {
        val state = windowState(WindowPlacement.Floating)
        var awt: WindowPlacement = WindowPlacement.Floating
        val fullscreen = WindowFullscreen(state, this) { awt }

        fullscreen.enter()
        awt = WindowPlacement.Fullscreen
        assertTrue(fullscreen.exit())
        // macOS still animates: AppKit would ignore the toggle, so the header must not show fullscreen.
        fullscreen.toggle()
        assertFalse(fullscreen.isFullscreen)
        assertEquals(WindowPlacement.Floating, state.placement)

        awt = WindowPlacement.Floating
        delay(200)
        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen, "once out, the next press enters")
        assertEquals(WindowPlacement.Floating, fullscreen.restorePlacement)
    }

    @Test
    fun `fullscreen entered and left by the OS keeps the toggle in step and leaves to where it was`() = runBlocking {
        val state = windowState(WindowPlacement.Floating)
        val fullscreen = WindowFullscreen(state, this)
        val tracking = launch { fullscreen.trackRestorePlacement() }
        settle()

        // The user maximizes the window, then the green button (or a gesture) makes it fullscreen:
        // Compose sets the placement from the window's resize.
        state.placement = WindowPlacement.Maximized
        settle()
        state.placement = WindowPlacement.Fullscreen
        settle()
        assertTrue(fullscreen.isFullscreen, "the header's toggle shows fullscreen")

        // The OS leaves it too: the toggle follows, and enters again on the next press.
        state.placement = WindowPlacement.Maximized
        settle()
        assertFalse(fullscreen.isFullscreen)
        state.placement = WindowPlacement.Fullscreen
        settle()

        // Esc, the toggle or F: back to the maximized window it came from.
        assertTrue(fullscreen.exit())
        repeat(10) { settle() }
        assertEquals(WindowPlacement.Maximized, state.placement)
        tracking.cancelAndJoin()
    }

    @Test
    fun `fullscreen is never saved as the startup state`() = runBlocking {
        val store = WindowBoundsStore(node)
        val initial = WindowBounds(120, 80, 1100, 720)
        val state = windowState()
        val saving = launch { state.saveBoundsTo(store, initial, delayMillis = 20) }
        settle()

        state.position = WindowPosition(200.dp, 100.dp)
        settle()
        // Fullscreen at once, before the pause: the move is still saved.
        state.placement = WindowPlacement.Fullscreen
        state.position = WindowPosition(0.dp, 0.dp)
        state.size = DpSize(1920.dp, 1080.dp)
        settle()
        delay(150)
        assertEquals(WindowBounds(200, 100, 1100, 720), store.read(), "the window before fullscreen")

        // Left to a maximized window: saved as maximized, over the floating bounds it restores to.
        state.placement = WindowPlacement.Maximized
        settle()
        delay(150)
        assertEquals(WindowBounds(200, 100, 1100, 720, isMaximized = true), store.read())

        // Fullscreen from there is not saved either: the next run opens maximized.
        state.placement = WindowPlacement.Fullscreen
        settle()
        delay(150)
        assertEquals(WindowBounds(200, 100, 1100, 720, isMaximized = true), store.read())
        saving.cancelAndJoin()
    }

    @Test
    fun `the way out of a macOS fullscreen is not saved while the window still has the screen's size`() = runBlocking {
        val store = WindowBoundsStore(node)
        var awtFullscreen = true
        val state = windowState(WindowPlacement.Fullscreen)
        state.position = WindowPosition(0.dp, 0.dp)
        state.size = DpSize(1440.dp, 900.dp)
        val saving = launch { state.saveBoundsTo(store, WindowBounds(120, 80, 1100, 720), { awtFullscreen }, delayMillis = 20) }
        settle()

        // Left: the placement says so at once, the window keeps the screen's bounds while it animates.
        state.placement = WindowPlacement.Floating
        settle()
        delay(150)
        assertEquals(null, store.read())

        // Done: the OS put the window back, and Compose reports where.
        awtFullscreen = false
        state.position = WindowPosition(120.dp, 80.dp)
        state.size = DpSize(1100.dp, 720.dp)
        settle()
        delay(150)
        assertEquals(WindowBounds(120, 80, 1100, 720), store.read())
        saving.cancelAndJoin()
    }

    /** Windows' borderless fullscreen, as the hook does it: the window's bounds change, its placement stays. */
    private class FakeBorderless(private val state: WindowState) : BorderlessFullscreen {
        var entered = 0
        var exited = 0
        private var before: Pair<WindowPosition, DpSize>? = null
        private var wasMaximized = false

        override fun enter() {
            entered++
            wasMaximized = state.placement == WindowPlacement.Maximized
            state.placement = WindowPlacement.Floating
            before = state.position to state.size
            state.position = WindowPosition(0.dp, 0.dp)
            state.size = DpSize(1920.dp, 1080.dp)
        }

        override fun exit() {
            exited++
            before?.let { (position, size) ->
                state.position = position
                state.size = size
            }
            if (wasMaximized) state.placement = WindowPlacement.Maximized
        }
    }

    @Test
    fun `the borderless fullscreen toggles without the placement`() {
        val state = windowState()
        val borderless = FakeBorderless(state)
        val fullscreen = WindowFullscreen(state, CoroutineScope(Dispatchers.Unconfined), borderless = borderless)

        fullscreen.toggle()
        assertTrue(fullscreen.isFullscreen)
        assertEquals(WindowPlacement.Floating, state.placement, "never AWT's exclusive full screen")
        assertEquals(1, borderless.entered)

        fullscreen.enter()
        assertEquals(1, borderless.entered, "already in")

        assertTrue(fullscreen.exit())
        assertFalse(fullscreen.isFullscreen)
        assertEquals(1, borderless.exited)
        assertEquals(WindowPosition(120.dp, 80.dp), state.position)
        assertFalse(fullscreen.exit(), "Esc goes on")
        assertEquals(1, borderless.exited)
    }

    @Test
    fun `a maximized window is maximized again after the borderless fullscreen`() {
        val state = windowState(WindowPlacement.Maximized)
        val fullscreen = WindowFullscreen(state, CoroutineScope(Dispatchers.Unconfined), borderless = FakeBorderless(state))

        fullscreen.enter()
        assertTrue(fullscreen.isFullscreen)
        fullscreen.exit()
        assertFalse(fullscreen.isFullscreen)
        assertEquals(WindowPlacement.Maximized, state.placement)
    }

    @Test
    fun `the borderless fullscreen is never saved as the startup state`() = runBlocking {
        val store = WindowBoundsStore(node)
        val state = windowState()
        val fullscreen = WindowFullscreen(state, this, borderless = FakeBorderless(state))
        val saving = launch { state.saveBoundsTo(store, WindowBounds(120, 80, 1100, 720), { fullscreen.isFullscreen }, delayMillis = 20) }
        settle()

        fullscreen.enter()
        settle()
        delay(150)
        assertEquals(WindowBounds(120, 80, 1100, 720), store.read(), "the window before fullscreen, not the monitor")

        fullscreen.exit()
        state.position = WindowPosition(130.dp, 90.dp)
        settle()
        delay(150)
        assertEquals(WindowBounds(130, 90, 1100, 720), store.read())
        saving.cancelAndJoin()
    }

    /** What Compose's frame clock does in the app: tells snapshotFlow, then lets its collector run. */
    private suspend fun settle() {
        Snapshot.sendApplyNotifications()
        delay(10)
    }
}
