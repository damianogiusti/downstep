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

/**
 * Whether the app may put its menus in the screen's menu bar. macOS 14.0 and 14.1 kill an app linked
 * against the macOS 14 SDK (our launcher and runtime are) as soon as Java adds a menu there:
 * `_NSCarbonMenuCrashIfNeeded`, before the window shows (JDK-8319420). Apple fixed it in 14.2. There
 * the app goes without its menus: donate and contact are in the library menu too, and the app menu
 * with Quit stays, as it is not a Java menu.
 */
internal val hasScreenMenuBar: Boolean = isMacOs && !isCarbonMenuCrashVersion(System.getProperty("os.version").orEmpty())

internal fun isCarbonMenuCrashVersion(osVersion: String): Boolean {
    val parts = osVersion.split('.').map { it.toIntOrNull() }
    return parts.getOrNull(0) == 14 && (parts.getOrNull(1) ?: 0) < 2
}
