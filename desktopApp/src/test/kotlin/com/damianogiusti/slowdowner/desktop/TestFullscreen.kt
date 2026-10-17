package com.damianogiusti.slowdowner.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.damianogiusti.slowdowner.ui.common.FullscreenControl

/** A window's fullscreen without a window, so the rendered header shows its toggle as in the app. */
class TestFullscreen(isFullscreen: Boolean = false) : FullscreenControl {
    override var isFullscreen by mutableStateOf(isFullscreen)
        private set

    override fun toggle() {
        isFullscreen = !isFullscreen
    }
}
