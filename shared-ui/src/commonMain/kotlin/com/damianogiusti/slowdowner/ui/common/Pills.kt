package com.damianogiusti.slowdowner.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

// The design's small controls (README §1: "Pills for state, cards for settings"). Shared by every
// player component: owned by the header/chips/transport group, used by all.

/** Alpha of a disabled control: the design shows unavailable features as designed, only dimmed. */
const val DISABLED_ALPHA = 0.38f

/** A hover tooltip (desktop) around [content]; just [content] when [text] is null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tooltip(text: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (text == null) {
        Box(modifier) { content() }
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * A 999px pill: header buttons (Library, Analyze), the loop toggle, chips. [container] null = no fill;
 * [border] null = no outline. Disabled pills keep their look at [DISABLED_ALPHA] and still show
 * [tooltip], which is how unavailable features explain themselves.
 */
@Composable
fun Pill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = DownstepDimens.headerControl,
    container: Color? = null,
    contentColor: Color = SlowDownerTheme.colors.fg,
    border: Color? = SlowDownerTheme.colors.surface3,
    dashed: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 14.dp),
    tooltip: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Tooltip(tooltip, modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .height(height)
                .clip(DownstepShapes.pill)
                .then(if (container != null) Modifier.background(container, DownstepShapes.pill) else Modifier)
                .then(if (border != null) Modifier.pillBorder(border, dashed) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .padding(contentPadding),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                ProvideTextStyle(SlowDownerTheme.type.control) { content() }
            }
        }
    }
}

/** Header-style round icon button: 40dp, 1px border, tooltip = description (README §2.2). */
@Composable
fun IconPill(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = DownstepDimens.headerControl,
    iconSize: Dp = 18.dp,
    container: Color? = null,
    tint: Color = SlowDownerTheme.colors.fg,
    border: Color? = SlowDownerTheme.colors.surface3,
    tooltip: String? = contentDescription,
) {
    Tooltip(tooltip, modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(DownstepShapes.pill)
                .then(if (container != null) Modifier.background(container, DownstepShapes.pill) else Modifier)
                .then(if (border != null) Modifier.border(1.dp, border, DownstepShapes.pill) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * A toggle inside a [SegmentedPill] or on its own (Metronome, Count-in, Snap, Isolate bands): filled
 * with [onColor] when [checked], transparent otherwise.
 */
@Composable
fun PillToggle(
    checked: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
    height: Dp = DownstepDimens.chip,
    onColor: Color = SlowDownerTheme.colors.amber,
    onContentColor: Color = SlowDownerTheme.colors.onAmber,
    offContentColor: Color = SlowDownerTheme.colors.fg,
    tooltip: String? = null,
) {
    Pill(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = height,
        container = if (checked) onColor else null,
        contentColor = if (checked) onContentColor else offContentColor,
        border = null,
        contentPadding = PaddingValues(horizontal = 14.dp),
        tooltip = tooltip,
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
        Text(text, maxLines = 1)
    }
}

/** The rounded track that groups toggles: Metronome / Count-in, the loop tools, Isolate bands. */
@Composable
fun SegmentedPill(
    modifier: Modifier = Modifier,
    container: Color = SlowDownerTheme.colors.surface1,
    border: Color? = SlowDownerTheme.colors.surface3,
    padding: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(DownstepShapes.pill)
            .background(container, DownstepShapes.pill)
            .then(if (border != null) Modifier.border(1.dp, border, DownstepShapes.pill) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Pass count on a chip, track count on the Library pill: a small pill of digits. */
@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier, container: Color = SlowDownerTheme.colors.surface3, contentColor: Color = SlowDownerTheme.colors.fg2) {
    Text(
        text,
        style = SlowDownerTheme.type.tag,
        color = contentColor,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp)
            .background(container, DownstepShapes.pill)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** A key cap in the keyboard hints: `A`, `[`, `F`. */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = SlowDownerTheme.type.tag,
        color = SlowDownerTheme.colors.fg,
        modifier = modifier
            .background(SlowDownerTheme.colors.surface1, KeyCapShape)
            .border(1.dp, SlowDownerTheme.colors.surface3, KeyCapShape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** −5 / +5 / −1 / +1 in the cards: 40×36, 10dp radius, surface3 (README §2.7). */
@Composable
fun ControlButton(
    text: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 40.dp,
    height: Dp = 36.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width, height)
            .clip(DownstepShapes.control)
            .background(SlowDownerTheme.colors.surface3, DownstepShapes.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Text(text, style = SlowDownerTheme.type.control, color = SlowDownerTheme.colors.fg)
    }
}

/** SPEED, PITCH, NOTES · Solo, RECENTLY ADDED: Inter 500 11, 0.6 tracking, secondary text. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = SlowDownerTheme.colors.fg2) {
    Text(text.uppercase(), style = SlowDownerTheme.type.sectionLabel, color = color, maxLines = 1, modifier = modifier)
}

private val KeyCapShape = RoundedCornerShape(4.dp)

private fun Modifier.pillBorder(color: Color, dashed: Boolean): Modifier =
    if (!dashed) border(1.dp, color, DownstepShapes.pill) else dashedPillBorder(color)

/** The dashed outline of "+ New loop" and of Analyze suggestions. */
fun Modifier.dashedPillBorder(color: Color, width: Dp = 1.dp, dash: Dp = 4.dp): Modifier = drawWithCache {
    val stroke = width.toPx()
    val effect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx()))
    val radius = CornerRadius(size.height / 2)
    onDrawBehind {
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = radius,
            style = Stroke(width = stroke, pathEffect = effect),
        )
    }
}
