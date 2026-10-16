package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.awt.Taskbar
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage

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

/** The Downstep icon (README §2.1), at its 1024 px design size. */
fun appIconPainter(): Painter {
    val stream = checkNotNull(ComposeWindow::class.java.getResourceAsStream(APP_ICON)) { "$APP_ICON missing" }
    return stream.use { loadSvgPainter(it, Density(1f)) }
}

/** The icon drawn at each size the OS picks from (title bar, taskbar, Alt+Tab), instead of one scaled bitmap. */
fun appIconImages(painter: Painter = appIconPainter()): List<BufferedImage> = ICON_SIZES.map { px ->
    val image = painter.toAwtImage(Density(1f), LayoutDirection.Ltr, Size(px.toFloat(), px.toFloat()))
    // A lazy multi-resolution image: render the 1x variant now, once.
    (image as MultiResolutionImage).getResolutionVariant(px.toDouble(), px.toDouble()) as BufferedImage
}

/**
 * Replaces Java's coffee cup in the title bar and taskbar. On macOS the window has no icon: the Dock
 * shows the bundle's, or, run from Gradle, the one set here.
 */
fun ComposeWindow.useAppIcon() {
    val images = runCatching { appIconImages() }.getOrElse { return }
    iconImages = images
    if (isMacOs && Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
        Taskbar.getTaskbar().iconImage = images.last()
    }
}

private const val APP_ICON = "/downstep-app-icon.svg"
private val ICON_SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)
