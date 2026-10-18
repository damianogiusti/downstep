package com.damianogiusti.slowdowner.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The window's title bar, where the app's header takes its place (Windows): the header is the area
 * that drags the window, its controls stay clickable, and the platform draws the window buttons at
 * its right end. The header tells where its parts are, in window pixels, as they are laid out:
 * - [titleBarCaption]: the header itself, which drags the window, maximizes on a double-click and
 *   opens the system menu on a right-click wherever no control is;
 * - [titleBarControl]: a control inside it (a pill, a menu button), which keeps its clicks;
 * - [TitleBarOverlay]: something that covers the header (the library drawer, a menu, a dialog,
 *   a sheet): while one shows, the whole window is the app's, so a click there closes it.
 *
 * Null (see [LocalWindowTitleBar]) where the OS draws the title bar (macOS, Linux, Android, a
 * Windows window whose title bar could not be replaced): the header then lays out as before.
 *
 * Positions are reported on layout changes only, never per frame or playback tick, and reporting
 * them recomposes nothing.
 */
@Stable
interface WindowTitleBar {
    /** Room the window buttons take at the header's right end (zero when they are hidden, in fullscreen). */
    val buttonsWidth: Dp

    /** Minimize, maximize / restore and close, as tall as [modifier] makes them. Nothing in fullscreen. */
    @Composable
    fun WindowButtons(modifier: Modifier)

    /** [region]'s bounds in window pixels, or null once it left the screen. */
    fun onRegionChanged(region: Any, kind: TitleBarRegion, bounds: Rect?)

    /** [overlay] shows over the header ([active]) or went away. */
    fun onOverlayChanged(overlay: Any, active: Boolean)
}

/** What a part of the header does in the title bar. */
enum class TitleBarRegion {
    /** Drags the window, where no control is. */
    Caption,

    /** Keeps its clicks. */
    Control,

    /** The maximize button: Windows shows its snap layouts over it, and handles its clicks. */
    MaximizeButton,
}

val LocalWindowTitleBar = staticCompositionLocalOf<WindowTitleBar?> { null }

/**
 * This title bar for a header that is not at the window's top (a banner above it draws the window
 * buttons): the header still drags the window and its controls keep their clicks, but it leaves
 * the window buttons, and their room, to the bar above.
 */
fun WindowTitleBar.withoutButtons(): WindowTitleBar = this as? TitleBarWithoutButtons ?: TitleBarWithoutButtons(this)

@Stable
private data class TitleBarWithoutButtons(private val base: WindowTitleBar) : WindowTitleBar {
    override val buttonsWidth: Dp get() = 0.dp

    @Composable
    override fun WindowButtons(modifier: Modifier) = Unit

    override fun onRegionChanged(region: Any, kind: TitleBarRegion, bounds: Rect?) = base.onRegionChanged(region, kind, bounds)

    override fun onOverlayChanged(overlay: Any, active: Boolean) = base.onOverlayChanged(overlay, active)
}

/** The header, or another bar at the top of the window: drags the window where it has no control. */
fun Modifier.titleBarCaption(): Modifier = this then TitleBarRegionElement(TitleBarRegion.Caption)

/** A control inside the header: keeps its clicks. */
fun Modifier.titleBarControl(): Modifier = this then TitleBarRegionElement(TitleBarRegion.Control)

/** The window's maximize button (the platform's own buttons only). */
fun Modifier.titleBarMaximizeButton(): Modifier = this then TitleBarRegionElement(TitleBarRegion.MaximizeButton)

/**
 * Composed while something covers the header (a drawer, a menu, a dialog, a sheet): the whole window
 * is the app's meanwhile, so a click on the header closes it instead of moving the window.
 */
@Composable
fun TitleBarOverlay() {
    val titleBar = LocalWindowTitleBar.current ?: return
    val token = remember { Any() }
    DisposableEffect(titleBar, token) {
        titleBar.onOverlayChanged(token, true)
        onDispose { titleBar.onOverlayChanged(token, false) }
    }
}

private data class TitleBarRegionElement(val kind: TitleBarRegion) : ModifierNodeElement<TitleBarRegionNode>() {
    override fun create() = TitleBarRegionNode(kind)

    override fun update(node: TitleBarRegionNode) {
        node.kind = kind
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "titleBarRegion"
        properties["kind"] = kind
    }
}

/** Reports its layout's bounds in the window, when they change, to the window's title bar (if any). */
private class TitleBarRegionNode(var kind: TitleBarRegion) :
    Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var titleBar: WindowTitleBar? = null
    private var last: Rect? = null

    override fun onAttach() {
        titleBar = currentValueOf(LocalWindowTitleBar)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val bar = titleBar ?: return
        val bounds = if (coordinates.isAttached) coordinates.boundsInWindow() else null
        // Called after every layout pass that may have moved it: tell only what changed.
        if (bounds == last) return
        last = bounds
        bar.onRegionChanged(this, kind, bounds?.takeUnless { it.isEmpty })
    }

    override fun onDetach() {
        titleBar?.onRegionChanged(this, kind, null)
        titleBar = null
        last = null
    }
}
