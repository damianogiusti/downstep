package com.damianogiusti.slowdowner.desktop.windows

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** WM_NCHITTEST's answers that the title bar gives (winuser.h). */
object HitCode {
    const val TRANSPARENT = -1
    const val CLIENT = 1
    const val CAPTION = 2
    const val MAXBUTTON = 9
    const val TOP = 12
    const val TOPLEFT = 13
    const val TOPRIGHT = 14
}

/**
 * Where the header's parts are, in the window's client pixels, as Compose last laid them out. One
 * immutable snapshot, replaced whole: the window procedure reads it from the OS's thread.
 */
data class TitleBarRegions(
    /** Drag the window where no control is. */
    val captions: List<IntRect> = emptyList(),
    /** Keep their clicks. */
    val controls: List<IntRect> = emptyList(),
    val maximizeButton: IntRect? = null,
    /** A drawer, a menu, a dialog or a sheet covers the header: the whole window is the app's. */
    val hasOverlay: Boolean = false,
) {
    companion object {
        val NONE = TitleBarRegions()
    }
}

/** The window as WM_NCHITTEST needs it. */
data class TitleBarWindowState(
    val isMaximized: Boolean = false,
    val isFullscreen: Boolean = false,
    /** Client width in pixels: the top-right resize corner is at its end. */
    val width: Int = 0,
    /** The system's resize border at the window's DPI (SM_CYFRAME + SM_CXPADDEDBORDER), in pixels. */
    val resizeBorder: Int = 0,
)

/**
 * The hit code for the point ([x], [y]) in Compose's window pixels, when Windows' own test says client (the
 * borders left, right and below the window stay Windows' own):
 * - fullscreen, or something over the header: client everywhere, the app takes every click;
 * - a strip at the top as thick as the resize border, except when maximized: the top edge, whose
 *   ends are the corners (the side borders are outside the client area, the top one inside it);
 * - the maximize button: Windows' own, so that it shows its snap layouts on hover;
 * - a control: client; the rest of the header: caption (drag, double-click, system menu);
 * - anything else: client.
 */
fun titleBarHitTest(
    x: Int,
    y: Int,
    regions: TitleBarRegions,
    window: TitleBarWindowState,
    /** The point in the window's client area, when Compose draws in a child window that may not start at its top. */
    fromTop: Int = y,
    fromLeft: Int = x,
): Int {
    if (window.isFullscreen || regions.hasOverlay) return HitCode.CLIENT
    if (!window.isMaximized && fromTop < window.resizeBorder && fromTop >= 0) {
        val corner = window.resizeBorder * CORNER_SCALE
        return when {
            fromLeft < corner -> HitCode.TOPLEFT
            fromLeft >= window.width - corner -> HitCode.TOPRIGHT
            else -> HitCode.TOP
        }
    }
    if (regions.maximizeButton?.containsPoint(x, y) == true) return HitCode.MAXBUTTON
    if (regions.controls.any { it.containsPoint(x, y) }) return HitCode.CLIENT
    if (regions.captions.any { it.containsPoint(x, y) }) return HitCode.CAPTION
    return HitCode.CLIENT
}

/** The corners of the top edge reach this many border widths along it: easier to grab, as Windows' own. */
private const val CORNER_SCALE = 2

/** Half-open, as pixels are: [right] and [bottom] are just outside. */
private fun IntRect.containsPoint(x: Int, y: Int) = x >= left && x < right && y >= top && y < bottom

/**
 * A control's bounds in whole pixels, grown to the pixels it touches, so its edges stay clickable.
 * A caption is rounded to the nearest pixels instead: it is what is left around the controls.
 */
fun Rect.toRegionPixels(isControl: Boolean): IntRect = if (isControl) {
    IntRect(floor(left).toInt(), floor(top).toInt(), ceil(right).toInt(), ceil(bottom).toInt())
} else {
    IntRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
}
