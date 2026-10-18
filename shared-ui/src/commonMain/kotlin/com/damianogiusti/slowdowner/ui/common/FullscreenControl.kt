package com.damianogiusti.slowdowner.ui.common

import androidx.compose.runtime.Stable

/**
 * The window's fullscreen, where the platform has windows (desktop): the header's toggle shows
 * [isFullscreen] and calls [toggle]. The window owns the state, and it follows the OS too (the macOS
 * green button, a minimize): [isFullscreen] is snapshot state, so only what reads it recomposes.
 * Android has no such window: there the control is null and the header shows no toggle.
 */
@Stable
interface FullscreenControl {
    val isFullscreen: Boolean

    fun toggle()
}
