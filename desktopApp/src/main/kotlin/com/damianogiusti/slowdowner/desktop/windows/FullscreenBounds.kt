package com.damianogiusti.slowdowner.desktop.windows

import androidx.compose.ui.unit.IntRect

/**
 * Where a borderless fullscreen window goes when something moves or sizes it (WM_WINDOWPOSCHANGING):
 * over the whole monitor it lands on. Win+Shift+arrows move it to another monitor, a DPI change
 * resizes it to Windows' suggested rect, AWT may set its bounds: it covers the monitor all the same.
 * [current] is the window's rect now, [x], [y], [cx], [cy] and [flags] the requested position,
 * [monitorOf] the rect of the monitor a window rect is on. Null leaves the request alone: no move or
 * size in it, or the window going to its minimized place.
 */
fun fullscreenWindowPos(
    current: IntRect,
    x: Int,
    y: Int,
    cx: Int,
    cy: Int,
    flags: Int,
    monitorOf: (IntRect) -> IntRect?,
): IntRect? {
    val noMove = flags and SWP_NOMOVE != 0
    val noSize = flags and SWP_NOSIZE != 0
    if (noMove && noSize) return null
    val left = if (noMove) current.left else x
    val top = if (noMove) current.top else y
    // A minimized window is parked far off every screen.
    if (left <= MINIMIZED_COORDINATE || top <= MINIMIZED_COORDINATE) return null
    val width = if (noSize) current.width else cx
    val height = if (noSize) current.height else cy
    val monitor = monitorOf(IntRect(left, top, left + width, top + height)) ?: return null
    return monitor.takeIf { it.width > 0 && it.height > 0 }
}

/**
 * The bounds a window leaving fullscreen goes back to: [saved], as before fullscreen, when it is
 * still on the monitor the window is on now ([from] and [to], those monitors' work areas, are the
 * same). Otherwise (the window moved to another monitor while fullscreen, or [saved]'s monitor is
 * gone, [from] null) the same size and place on [to], shrunk and moved as needed to fit it.
 */
fun boundsAfterFullscreen(saved: IntRect, from: IntRect?, to: IntRect): IntRect {
    if (from == to) return saved
    val width = minOf(saved.width, to.width)
    val height = minOf(saved.height, to.height)
    val left = if (from != null) to.left + (saved.left - from.left) else to.left + (to.width - width) / 2
    val top = if (from != null) to.top + (saved.top - from.top) else to.top + (to.height - height) / 2
    val x = left.coerceIn(to.left, to.right - width)
    val y = top.coerceIn(to.top, to.bottom - height)
    return IntRect(x, y, x + width, y + height)
}

private const val SWP_NOSIZE = 0x0001
private const val SWP_NOMOVE = 0x0002

/** Where Windows parks a minimized window, on both axes. */
private const val MINIMIZED_COORDINATE = -32000
