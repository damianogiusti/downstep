package com.damianogiusti.slowdowner.desktop.windows

import androidx.compose.ui.unit.IntRect

/** An edge of the screen, where an auto-hide taskbar may wait. */
enum class ScreenEdge { Left, Top, Right, Bottom }

/**
 * The client area WM_NCCALCSIZE answers for a window whose header is its title bar, from [window]
 * (the window's proposed bounds) and [standard] (the client area Windows computed for them, with
 * its caption and frame):
 * - floating: Windows' client area with its top raised to the window's top. The caption goes, the
 *   side and bottom borders stay Windows' (invisible resize borders, shadow, rounded corners); the
 *   top resize border is inside the client area now, and the hit test gives it back.
 * - maximized: a maximized window overhangs its screen by the frame on every side; the top keeps
 *   [maximizedTopInset] (that overhang) so the header starts on the screen, not above it. Where an
 *   auto-hide taskbar waits ([autoHideEdges]), the client area stops [AUTO_HIDE_GAP] pixels short of
 *   that edge, or Windows would take the window for a fullscreen one and never show the taskbar.
 * - fullscreen: the whole window, which covers its monitor.
 */
fun captionlessClientArea(
    window: IntRect,
    standard: IntRect,
    isMaximized: Boolean,
    isFullscreen: Boolean,
    maximizedTopInset: Int,
    autoHideEdges: Set<ScreenEdge> = emptySet(),
): IntRect {
    if (isFullscreen) return window
    if (!isMaximized) return standard.copy(top = window.top)
    var area = standard.copy(top = window.top + maximizedTopInset)
    for (edge in autoHideEdges) {
        area = when (edge) {
            ScreenEdge.Left -> area.copy(left = area.left + AUTO_HIDE_GAP)
            ScreenEdge.Top -> area.copy(top = area.top + AUTO_HIDE_GAP)
            ScreenEdge.Right -> area.copy(right = area.right - AUTO_HIDE_GAP)
            ScreenEdge.Bottom -> area.copy(bottom = area.bottom - AUTO_HIDE_GAP)
        }
    }
    return area
}

/** Left free on an auto-hide taskbar's edge, for the pointer to reach it: as Windows' own frame leaves. */
const val AUTO_HIDE_GAP = 2
