package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import kotlin.math.roundToInt

/**
 * The smallest window the player is laid out for: its card rows need this width (README §6), and at
 * this height the most compact layout still shows every control (only a window narrower than 1240dp,
 * where the progression's sentence wraps, scrolls its cards a little). It fits a 1280×720 screen with
 * its taskbar.
 */
val MinimumWindowSize = DpSize(1000.dp, 600.dp)

/** The size the Downstep 2.0 design is drawn at. */
val DesignWindowSize = DpSize(1280.dp, 820.dp)

/** A new window takes at most this share of the screen's usable area, so it opens with a margin. */
private const val SCREEN_SHARE = 0.94f

/**
 * The first window's size on a screen whose usable area (taskbar, dock and menu bar left out) is
 * [usable]: the design size, smaller on a laptop screen, never below [minimum] unless the screen is.
 * Sizes are in dp, which on desktop are AWT's user-space units: the OS scale is already applied.
 */
fun defaultWindowSize(usable: DpSize, minimum: DpSize = MinimumWindowSize): DpSize {
    val floor = DpSize(min(minimum.width, usable.width), min(minimum.height, usable.height))
    return DpSize(
        width = min(DesignWindowSize.width, usable.width * SCREEN_SHARE).coerceAtLeast(floor.width).rounded(),
        height = min(DesignWindowSize.height, usable.height * SCREEN_SHARE).coerceAtLeast(floor.height).rounded(),
    )
}

private fun androidx.compose.ui.unit.Dp.rounded() = value.roundToInt().dp

/** A window's place in dp (AWT user space), as saved between runs. [isMaximized] keeps the size it restores to. */
data class WindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val isMaximized: Boolean = false,
) {
    val rectangle: Rectangle get() = Rectangle(x, y, width, height)
}

/**
 * Where the window opens, given the [saved] bounds of the last run and the [screens]' usable areas
 * (the primary one first). The saved window goes back on the screen that shows most of it, shrunk to
 * fit that screen and moved fully onto it. When no screen shows it any more (a monitor unplugged), or
 * nothing was saved, the window opens at [defaultWindowSize], centred on the primary screen.
 */
fun restoreWindowBounds(saved: WindowBounds?, screens: List<Rectangle>, minimum: DpSize = MinimumWindowSize): WindowBounds {
    val primary = screens.firstOrNull() ?: FALLBACK_SCREEN
    val screen = saved?.let { window -> screens.maxByOrNull { visibleArea(window.rectangle, it) }?.takeIf { visibleArea(window.rectangle, it) > 0 } }
    if (saved == null || screen == null) {
        val size = defaultWindowSize(DpSize(primary.width.dp, primary.height.dp), minimum)
        return centred(primary, size.width.value.roundToInt(), size.height.value.roundToInt(), saved?.isMaximized ?: false)
    }
    val width = saved.width.coerceAtLeast(minimum.width.value.roundToInt()).coerceAtMost(screen.width)
    val height = saved.height.coerceAtLeast(minimum.height.value.roundToInt()).coerceAtMost(screen.height)
    return WindowBounds(
        x = saved.x.coerceIn(screen.x, screen.x + screen.width - width),
        y = saved.y.coerceIn(screen.y, screen.y + screen.height - height),
        width = width,
        height = height,
        isMaximized = saved.isMaximized,
    )
}

private fun centred(screen: Rectangle, width: Int, height: Int, isMaximized: Boolean) = WindowBounds(
    x = screen.x + (screen.width - width) / 2,
    y = screen.y + (screen.height - height) / 2,
    width = width,
    height = height,
    isMaximized = isMaximized,
)

private fun visibleArea(window: Rectangle, screen: Rectangle): Long {
    val visible = window.intersection(screen)
    return if (visible.isEmpty) 0 else visible.width.toLong() * visible.height
}

/** Used only without any screen (headless): the smallest common laptop screen. */
private val FALLBACK_SCREEN = Rectangle(0, 0, 1280, 720)

/**
 * The usable area of every screen, the primary one first: its bounds without the taskbar, dock or menu
 * bar. AWT gives them in user space, the units Compose sizes windows in. Empty when headless.
 */
fun usableScreenAreas(): List<Rectangle> {
    if (GraphicsEnvironment.isHeadless()) return emptyList()
    val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val primary = environment.defaultScreenDevice
    val toolkit = Toolkit.getDefaultToolkit()
    return (listOf(primary) + environment.screenDevices.filter { it != primary }).map { device ->
        val configuration = device.defaultConfiguration
        val bounds = configuration.bounds
        val insets = toolkit.getScreenInsets(configuration)
        Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom,
        )
    }
}
