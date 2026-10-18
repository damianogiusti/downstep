package com.damianogiusti.slowdowner.desktop.windows

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.common.TitleBarRegion
import com.damianogiusti.slowdowner.ui.common.WindowTitleBar
import com.damianogiusti.slowdowner.ui.common.titleBarControl
import com.damianogiusti.slowdowner.ui.common.titleBarMaximizeButton
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.window_close
import com.damianogiusti.slowdowner.ui.resources.window_maximize
import com.damianogiusti.slowdowner.ui.resources.window_minimize
import com.damianogiusti.slowdowner.ui.resources.window_restore
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The Compose side of [WindowsTitleBarHook]: collects the header's parts as Compose lays them out
 * and hands the hook one snapshot per change (on the event thread, which is Compose's), and draws
 * the window buttons. [isFullscreen] and [isMaximized] are snapshot state: the buttons follow them.
 */
@Stable
class WindowsTitleBar(
    private val hook: CaptionlessWindow,
    private val isFullscreen: () -> Boolean,
    private val isMaximized: () -> Boolean,
) : WindowTitleBar, CaptionlessWindow.Listener {

    private val regions = LinkedHashMap<Any, Pair<TitleBarRegion, Rect>>()
    private val overlays = HashSet<Any>()

    /** The maximize button's hover and press, which Windows owns: from the hook. */
    internal var nativeMaximizeHovered by mutableStateOf(false)
        private set
    internal var nativeMaximizePressed by mutableStateOf(false)
        private set

    /** Where leaving fullscreen goes when the OS asks for it (Win+Up, the system menu). */
    var onLeaveFullscreenRequested: () -> Unit = {}

    init {
        hook.listener = this
    }

    override val buttonsWidth: Dp get() = if (isFullscreen()) 0.dp else WINDOW_BUTTON_WIDTH * 3

    @Composable
    override fun WindowButtons(modifier: Modifier) {
        if (isFullscreen()) return
        WindowButtonsRow(this, isMaximized(), modifier)
    }

    override fun onRegionChanged(region: Any, kind: TitleBarRegion, bounds: Rect?) {
        if (bounds == null) {
            if (regions.remove(region) == null) return
        } else {
            regions[region] = kind to bounds
        }
        publish()
    }

    override fun onOverlayChanged(overlay: Any, active: Boolean) {
        val changed = if (active) overlays.add(overlay) else overlays.remove(overlay)
        if (changed) publish()
    }

    private fun publish() {
        hook.regions = snapshotOf(regions.values, overlays.isNotEmpty())
    }

    override fun onMaximizeButtonHover(hovered: Boolean) {
        nativeMaximizeHovered = hovered
    }

    override fun onMaximizeButtonPressed(pressed: Boolean) {
        nativeMaximizePressed = pressed
    }

    override fun onLeaveFullscreenRequested() = onLeaveFullscreenRequested.invoke()

    internal fun minimize() = hook.minimize()

    internal fun toggleMaximized() = hook.toggleMaximized()

    internal fun close() = hook.close()
}

/** The hook's snapshot of the header's parts, in whole pixels. */
fun snapshotOf(regions: Collection<Pair<TitleBarRegion, Rect>>, hasOverlay: Boolean) = TitleBarRegions(
    captions = regions.filter { it.first == TitleBarRegion.Caption }.map { it.second.toRegionPixels(isControl = false) },
    controls = regions.filter { it.first == TitleBarRegion.Control }.map { it.second.toRegionPixels(isControl = true) },
    maximizeButton = regions.firstOrNull { it.first == TitleBarRegion.MaximizeButton }?.second?.toRegionPixels(isControl = true),
    hasOverlay = hasOverlay,
)

/** Windows 11's caption buttons are this wide. */
val WINDOW_BUTTON_WIDTH = 46.dp

@Composable
private fun WindowButtonsRow(titleBar: WindowsTitleBar, isMaximized: Boolean, modifier: Modifier) {
    Row(modifier = modifier) {
        WindowButton(
            description = stringResource(Res.string.window_minimize),
            onClick = titleBar::minimize,
            modifier = Modifier.titleBarControl(),
        ) { color -> drawMinimize(color) }
        WindowButton(
            description = stringResource(if (isMaximized) Res.string.window_restore else Res.string.window_maximize),
            onClick = titleBar::toggleMaximized,
            // Windows owns its hover and clicks (snap layouts): the hook tells them.
            nativeHovered = titleBar.nativeMaximizeHovered,
            nativePressed = titleBar.nativeMaximizePressed,
            modifier = Modifier.titleBarMaximizeButton(),
        ) { color -> if (isMaximized) drawRestore(color) else drawMaximize(color) }
        WindowButton(
            description = stringResource(Res.string.window_close),
            onClick = titleBar::close,
            isClose = true,
            modifier = Modifier.titleBarControl(),
        ) { color -> drawClose(color) }
    }
}

/**
 * One caption button, as Windows 11 draws them: a 10 px glyph, a light wash on hover (red for close,
 * with a white glyph), a stronger one while pressed, a dimmer glyph while the window is inactive.
 */
@Composable
private fun WindowButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    isClose: Boolean = false,
    nativeHovered: Boolean = false,
    nativePressed: Boolean = false,
    glyph: DrawScope.(Color) -> Unit,
) {
    val colors = SlowDownerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.collectIsHoveredAsState().value || nativeHovered
    val pressed = interaction.collectIsPressedAsState().value || nativePressed
    val isActive = LocalWindowInfo.current.isWindowFocused
    val background = when {
        isClose && pressed -> CLOSE_RED.copy(alpha = CLOSE_PRESSED_ALPHA)
        isClose && hovered -> CLOSE_RED
        pressed -> colors.fg.copy(alpha = PRESSED_ALPHA)
        hovered -> colors.fg.copy(alpha = HOVER_ALPHA)
        else -> Color.Transparent
    }
    val glyphColor = when {
        isClose && (hovered || pressed) -> Color.White
        isActive || hovered -> colors.fg
        else -> colors.fg2
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(WINDOW_BUTTON_WIDTH)
            .fillMaxHeight()
            .background(background)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(GLYPH_SIZE)) { glyph(glyphColor) }
    }
}

private fun DrawScope.stroke() = Stroke(width = 1.dp.toPx())

private fun DrawScope.drawMinimize(color: Color) {
    val y = size.height / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

private fun DrawScope.drawMaximize(color: Color) {
    val inset = 0.5.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
        style = stroke(),
    )
}

/** Two squares: the front one, and the corner of the one behind. */
private fun DrawScope.drawRestore(color: Color) {
    val inset = 0.5.dp.toPx()
    val offset = 2.dp.toPx()
    val front = size.width - offset - 2 * inset
    drawRoundRect(
        color,
        topLeft = Offset(inset, offset + inset),
        size = Size(front, front),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
        style = stroke(),
    )
    val right = size.width - inset
    val top = inset
    drawLine(color, Offset(offset + inset, top), Offset(right, top), strokeWidth = 1.dp.toPx())
    drawLine(color, Offset(right, top), Offset(right, size.height - offset - inset), strokeWidth = 1.dp.toPx())
}

private fun DrawScope.drawClose(color: Color) {
    drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
}

private val GLYPH_SIZE = 10.dp

/** Windows 11's close button red, hovered and pressed. */
private val CLOSE_RED = Color(0xFFC42B1C)
private const val CLOSE_PRESSED_ALPHA = 0.9f
private const val HOVER_ALPHA = 0.08f
private const val PRESSED_ALPHA = 0.14f
