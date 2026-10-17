package com.damianogiusti.slowdowner.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.domain.loops.BeatGrid
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.ui.common.formatClock
import com.damianogiusti.slowdowner.ui.common.formatDecimal
import com.damianogiusti.slowdowner.ui.common.formatTimecode
import com.damianogiusti.slowdowner.ui.player.WaveformActions
import com.damianogiusti.slowdowner.ui.player.ZoomStripState
import com.damianogiusti.slowdowner.ui.player.components.waveform.BarColors
import com.damianogiusti.slowdowner.ui.player.components.waveform.DRAG_THRESHOLD
import com.damianogiusti.slowdowner.ui.player.components.waveform.ExpireDroppedPreview
import com.damianogiusti.slowdowner.ui.player.components.waveform.HitRegion
import com.damianogiusti.slowdowner.ui.player.components.waveform.LoopDragState
import com.damianogiusti.slowdowner.ui.player.components.waveform.LoopGestureActions
import com.damianogiusti.slowdowner.ui.player.components.waveform.WaveformAxis
import com.damianogiusti.slowdowner.ui.player.components.waveform.barShapes
import com.damianogiusti.slowdowner.ui.player.components.waveform.barsIn
import com.damianogiusti.slowdowner.ui.player.components.waveform.drawBars
import com.damianogiusti.slowdowner.ui.player.components.waveform.drawTextPill
import com.damianogiusti.slowdowner.ui.player.components.waveform.drawVerticalLine
import com.damianogiusti.slowdowner.ui.player.components.waveform.loopGestures
import com.damianogiusti.slowdowner.ui.player.components.waveform.normalizeLevels
import com.damianogiusti.slowdowner.ui.player.components.waveform.playedBars
import com.damianogiusti.slowdowner.ui.player.components.waveform.resamplePeaks
import com.damianogiusti.slowdowner.ui.resources.*
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The active loop ± padding ([ZoomStripState.window]) at sub-beat precision: A/B lines draggable
 * freely, as on the overview, and header texts. [position] is read only inside draw code.
 *
 * [drag] is the overview's: a drag there (a resize, or the whole loop moved by its top bar) is drawn
 * here live too, against the window of the committed loop, which follows once the drag is committed.
 */
@Composable
fun ZoomStrip(
    state: ZoomStripState,
    position: () -> Float,
    actions: WaveformActions,
    modifier: Modifier = Modifier,
    height: Dp = DownstepDimens.zoomStrip,
    drag: LoopDragState = remember { LoopDragState() },
) {
    val colors = SlowDownerTheme.colors
    val type = SlowDownerTheme.type
    val measurer = rememberTextMeasurer()
    val current by rememberUpdatedState(state)
    LaunchedEffect(state.loopId, state.start, state.end) { drag.settle(state.loopId, LoopBounds(state.start, state.end)) }
    ExpireDroppedPreview(drag)
    val width = remember { mutableIntStateOf(0) }

    val separator = stringResource(Res.string.decimal_separator)
    val duration = state.durationSeconds
    val title = stringResource(
        Res.string.zoom_title,
        state.loopName,
        formatTimecode(state.window.start * duration, separator),
        formatTimecode(state.window.end * duration, separator),
    )
    val length = state.beats?.let {
        stringResource(Res.string.zoom_beats, formatDecimal(it, separator), formatDecimal(it / BeatGrid.BEATS_PER_BAR, separator))
    } ?: stringResource(Res.string.zoom_seconds, formatDecimal((state.end - state.start) * duration, separator))
    val description = stringResource(Res.string.waveform_description, formatClock(duration))
    val markerStyle = type.tag.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val headerStyle = type.tag.copy(fontSize = 10.sp, letterSpacing = 0.5.sp)

    Box(
        modifier = modifier
            .padding(horizontal = DownstepDimens.gutter)
            .fillMaxWidth()
            .height(height)
            .clip(Shape)
            .background(colors.surface1)
            .onSizeChanged { width.intValue = it.width }
            // No playhead here: semantics would then be rebuilt on every frame of playback.
            .semantics { contentDescription = description }
            .pointerHoverIcon(PointerIcon.Crosshair)
            .pointerInput(actions, drag) {
                val padding = INNER_PADDING.toPx()
                loopGestures(
                    drag = drag,
                    reach = HANDLE_WIDTH.toPx() / 2,
                    // No top bar here: the strip is all about A and B.
                    barHeight = 0f,
                    threshold = DRAG_THRESHOLD.toPx(),
                    durationSeconds = { current.durationSeconds },
                    axis = { size -> current.takeIf { it.enabled }?.let { zoomAxis(it.window, size.width.toFloat(), padding) } },
                    regions = { current.let { listOf(HitRegion(it.loopId, LoopBounds(it.start, it.end), isActive = true)) } },
                    actions = LoopGestureActions(
                        onSeek = actions.onSeek,
                        onBarClick = { _, position -> actions.onSeek(position) },
                        onGrab = {},
                        onCommit = actions.onLoopBoundsChanged,
                    ),
                )
            }
            .drawWithCache {
                val axis = zoomAxis(state.window, size.width, INNER_PADDING.toPx())
                val barsTop = BARS_TOP.toPx()
                val barsHeight = (size.height - barsTop - BARS_BOTTOM.toPx()).coerceAtLeast(0f)
                val bars = state.peaks?.let {
                    val levels = normalizeLevels(resamplePeaks(it, state.window.start, state.window.end, BAR_COUNT))
                    barShapes(levels, axis, barsTop + barsHeight / 2, barsHeight, 1.dp.toPx(), 2.dp.toPx(), MIN_BAR.toPx())
                }
                val loopColor = colors.loopColor(state.colorIndex)
                val barColors = BarColors(colors.barPlayed, colors.barMuted, loopColor, loopColor.copy(alpha = 0.5f))
                val a = measurer.measure("A", markerStyle.copy(color = colors.onAmber))
                val b = measurer.measure("B", markerStyle.copy(color = colors.onTeal))
                val px = 1.dp.toPx()
                val border = bottomBorder(size.width, size.height, CORNER.toPx(), px)

                onDrawBehind {
                    val bounds = drag.shown(state.loopId, LoopBounds(state.start, state.end))
                    val startX = axis.x(bounds.start)
                    val endX = axis.x(bounds.end)
                    drawRect(loopColor.copy(alpha = 0.14f), Offset(startX, 0f), Size(endX - startX, size.height))

                    val playhead = position()
                    if (bars != null) {
                        drawBars(
                            bars = bars,
                            played = playedBars(playhead, state.window.start, state.window.end, bars.count),
                            loop = barsIn(bounds.start, bounds.end, state.window.start, state.window.end, bars.count),
                            colors = barColors,
                        )
                    }

                    drawVerticalLine(colors.amber, startX, 2.dp.toPx())
                    drawVerticalLine(colors.teal, endX, 2.dp.toPx())
                    val pillTop = 4.dp.toPx()
                    val pillHeight = 16.dp.toPx()
                    drawTextPill(a, startX, pillTop, pillHeight, 6.dp.toPx(), colors.amber)
                    drawTextPill(b, endX, pillTop, pillHeight, 6.dp.toPx(), colors.teal)

                    if (axis.contains(playhead)) drawVerticalLine(colors.fg, axis.x(playhead), 2.dp.toPx())
                    drawPath(border, colors.surface3, style = Stroke(width = px))
                }
            },
    ) {
        Text(
            text = title,
            style = headerStyle,
            color = colors.fg2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(start = INNER_PADDING, top = 5.dp, end = 160.dp),
        )
        Text(
            text = length,
            style = type.meta.copy(fontSize = 10.sp),
            color = colors.fg2,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = INNER_PADDING, top = 5.dp),
        )
        val loopId = state.loopId
        val committed = LoopBounds(state.start, state.end)
        val window = state.window
        HandleTarget(
            description = stringResource(Res.string.marker_a_description),
            x = { zoomAxis(window, width.intValue.toFloat(), INNER_PADDING.toPx()).x(drag.shown(loopId, committed).start) },
            width = HANDLE_WIDTH,
        )
        HandleTarget(
            description = stringResource(Res.string.marker_b_description),
            x = { zoomAxis(window, width.intValue.toFloat(), INNER_PADDING.toPx()).x(drag.shown(loopId, committed).end) },
            width = HANDLE_WIDTH,
        )
    }
}

private fun zoomAxis(window: LoopBounds, width: Float, padding: Float) =
    WaveformAxis(padding, (width - padding * 2).coerceAtLeast(0f), window.start, window.end)

/** Left, bottom and right edges, rounded at the bottom: the strip hangs from the overview. */
private fun bottomBorder(width: Float, height: Float, radius: Float, stroke: Float): Path {
    val inset = stroke / 2
    val right = width - inset
    val bottom = height - inset
    return Path().apply {
        moveTo(inset, 0f)
        lineTo(inset, bottom - radius)
        arcTo(Rect(inset, bottom - radius * 2, inset + radius * 2, bottom), 180f, -90f, false)
        lineTo(right - radius, bottom)
        arcTo(Rect(right - radius * 2, bottom - radius * 2, right, bottom), 90f, -90f, false)
        lineTo(right, 0f)
    }
}

private const val BAR_COUNT = 180
private val CORNER = 16.dp
private val Shape = RoundedCornerShape(bottomStart = CORNER, bottomEnd = CORNER)
private val INNER_PADDING = 12.dp
private val BARS_TOP = 22.dp
private val BARS_BOTTOM = 14.dp
private val MIN_BAR = 2.dp
private val HANDLE_WIDTH = 18.dp
