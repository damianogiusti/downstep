package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val isMacOs: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

/**
 * Space kept free at the top of the content for the title bar. On macOS the bar is transparent
 * and drawn over the content, so only the window buttons show, on the app's background. Its
 * area still drags the window: controls under it would not get the clicks.
 */
val titleBarHeight: Dp = if (isMacOs) 28.dp else 0.dp

/** macOS "unified" look: content under a transparent title bar, no title text. No-op elsewhere. */
fun ComposeWindow.useTransparentTitleBar() {
    if (!isMacOs) return
    rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}
