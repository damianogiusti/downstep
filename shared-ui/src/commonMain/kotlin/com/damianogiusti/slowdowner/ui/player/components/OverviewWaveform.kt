package com.damianogiusti.slowdowner.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.ui.common.formatClock
import com.damianogiusti.slowdowner.ui.common.formatTimecode
import com.damianogiusti.slowdowner.ui.player.OverviewWaveformState
import com.damianogiusti.slowdowner.ui.player.WaveformActions
import com.damianogiusti.slowdowner.ui.player.components.waveform.BarColors
import com.damianogiusti.slowdowner.ui.player.components.waveform.DragTarget
import com.damianogiusti.slowdowner.ui.player.components.waveform.LoopDragState
import com.damianogiusti.slowdowner.ui.player.components.waveform.WaveformAxis
import com.damianogiusti.slowdowner.ui.player.components.waveform.barShapes
import com.damianogiusti.slowdowner.ui.player.components.waveform.barsIn
import com.damianogiusti.slowdowner.ui.player.components.waveform.drawBars
import com.damianogiusti.slowdowner.ui.player.components.waveform.drawGlow
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
import kotlin.math.roundToInt

/**
 * The full-width overview: bars from [OverviewWaveformState.peaks], loop regions, bar lines, A/B pills
 * and drag handles, the zoom bracket, suggestions, and the playhead with its timecode pill.
 *
 * [position] is read only inside draw code: playback never recomposes this composable. The bars are
 * one cached path per size and peaks; a frame only clips it into its colours and draws the overlays.
 */
@Composable
fun OverviewWaveform(
    state: OverviewWaveformState,
    position: () -> Float,
    actions: WaveformActions,
    modifier: Modifier = Modifier,
    height: Dp = DownstepDimens.waveform,
) {
    val colors = SlowDownerTheme.colors
    val type = SlowDownerTheme.type
    val measurer = rememberTextMeasurer()
    val drag = remember { LoopDragState() }
    val current by rememberUpdatedState(state)
    LaunchedEffect(state.active) { drag.settle() }
    // Only for the invisible A/B handles, which are placed in the layout phase.
    val width = remember { mutableIntStateOf(0) }

    val separator = stringResource(Res.string.decimal_separator)
    val markerA = stringResource(Res.string.marker_a, PLACEHOLDER)
    val markerB = stringResource(Res.string.marker_b, PLACEHOLDER)
    val description = stringResource(Res.string.waveform_description, PLACEHOLDER, formatClock(state.durationSeconds))
    val pillStyle = type.tag.copy(fontSize = 11.sp)
    val labelStyle = type.tag.copy(fontSize = 10.sp, color = colors.amber)
    val durationStyle = type.meta.copy(fontSize = 11.sp, color = colors.fg2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.bgDeep)
            .onSizeChanged { width.intValue = it.width }
            .semantics { contentDescription = description.replace(PLACEHOLDER, formatClock(position() * current.durationSeconds)) }
            .pointerHoverIcon(PointerIcon.Crosshair)
            .pointerInput(actions) {
                val gutter = DownstepDimens.gutter.toPx()
                loopGestures(
                    drag = drag,
                    reach = HANDLE_WIDTH.toPx() / 2,
                    axis = { size -> current.takeIf { it.isOpen && it.enabled }?.let { overviewAxis(size.width.toFloat(), gutter) } },
                    target = { current.dragTarget() },
                    onSeek = actions.onSeek,
                    onCommit = actions.onLoopBoundsChanged,
                )
            }
            .drawWithCache {
                val gutter = DownstepDimens.gutter.toPx()
                val axis = overviewAxis(size.width, gutter)
                val barsTop = BARS_INSET.toPx()
                val barsHeight = (size.height - barsTop * 2).coerceAtLeast(0f)
                val levels = state.peaks?.let { normalizeLevels(resamplePeaks(it, 0f, 1f, BAR_COUNT)) }
                val bars = levels?.let {
                    barShapes(it, axis, barsTop + barsHeight / 2, barsHeight, BAR_GAP.toPx(), BAR_RADIUS.toPx(), MIN_BAR.toPx())
                }
                val duration = state.durationSeconds
                val durationText = state.isOpen.takeIf { it }?.let { measurer.measure(formatClock(duration), durationStyle) }
                val suggestionLabels = state.suggestions.map { measurer.measure(it.name, labelStyle) }
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                val px = 1.dp.toPx()
                val rule = 3.dp.toPx()
                val pillHeight = 22.dp.toPx()
                val pillPadding = 8.dp.toPx()

                onDrawBehind {
                    if (!state.isOpen) return@onDrawBehind
                    val active = state.active
                    val activeBounds = active?.let { drag.shown(LoopBounds(it.start, it.end)) }
                    val playhead = position().coerceIn(0f, 1f)

                    // Region fills under everything.
                    for (region in state.regions) {
                        val bounds = if (region.isActive && activeBounds != null) activeBounds else LoopBounds(region.start, region.end)
                        val alpha = when {
                            !region.isActive -> 0.05f
                            state.isLoopOn -> 0.16f
                            else -> 0.08f
                        }
                        val left = axis.x(bounds.start)
                        drawRect(colors.loopColor(region.colorIndex).copy(alpha = alpha), Offset(left, 0f), Size(axis.x(bounds.end) - left, size.height))
                    }

                    state.suggestions.forEachIndexed { index, suggestion ->
                        val left = axis.x(suggestion.start)
                        val inset = 4.dp.toPx()
                        drawRoundRect(
                            color = colors.amber.copy(alpha = 0.7f),
                            topLeft = Offset(left, inset),
                            size = Size(axis.x(suggestion.end) - left, size.height - inset * 2),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(width = px, pathEffect = dash),
                        )
                        drawText(suggestionLabels[index], topLeft = Offset(left + 6.dp.toPx(), 8.dp.toPx()))
                    }

                    val barLine = colors.fg.copy(alpha = 0.08f)
                    for (line in state.barLines) drawVerticalLine(barLine, axis.x(line), px)

                    if (bars != null) {
                        val loopColor = active?.let { colors.loopColor(it.colorIndex) } ?: colors.amber
                        drawBars(
                            bars = bars,
                            played = playedBars(playhead, 0f, 1f, bars.count),
                            loop = activeBounds?.let { barsIn(it.start, it.end, 0f, 1f, bars.count) } ?: IntRange.EMPTY,
                            colors = BarColors(colors.barPlayed, colors.barMuted, loopColor, loopColor.copy(alpha = 0.5f)),
                        )
                    }

                    // Edges and top rules over the bars; the active loop on top of the others.
                    for (region in state.regions.sortedBy { it.isActive }) {
                        val bounds = if (region.isActive && activeBounds != null) activeBounds else LoopBounds(region.start, region.end)
                        val color = colors.loopColor(region.colorIndex).copy(alpha = if (region.isActive) 1f else 0.35f)
                        val left = axis.x(bounds.start)
                        val right = axis.x(bounds.end)
                        drawRect(color, Offset(left, 0f), Size(right - left, rule))
                        drawVerticalLine(color, left, px)
                        drawVerticalLine(color, right, px)
                    }

                    if (active != null && activeBounds != null) {
                        state.zoomWindow?.let { window ->
                            val bracket = colors.fg2.copy(alpha = 0.35f)
                            drawVerticalLine(bracket, axis.x(window.start), px)
                            drawVerticalLine(bracket, axis.x(window.end), px)
                        }
                        val top = size.height - 8.dp.toPx() - pillHeight
                        val a = measurer.measure(marker(markerA, formatTimecode(activeBounds.start * duration, separator)), pillStyle.copy(color = colors.onAmber))
                        val b = measurer.measure(marker(markerB, formatTimecode(activeBounds.end * duration, separator)), pillStyle.copy(color = colors.onTeal))
                        drawTextPill(a, axis.x(activeBounds.start), top, pillHeight, pillPadding, colors.amber)
                        drawTextPill(b, axis.x(activeBounds.end), top, pillHeight, pillPadding, colors.teal)
                    }

                    durationText?.let {
                        drawText(it, topLeft = Offset(size.width - gutter - it.size.width, size.height - 10.dp.toPx() - it.size.height))
                    }

                    val x = axis.x(playhead)
                    drawGlow(colors.fgGlow.copy(alpha = colors.fgGlow.alpha * GLOW_STRENGTH), x, 8.dp.toPx())
                    drawVerticalLine(colors.fg, x, 2.dp.toPx())
                    val clock = measurer.measure(formatClock(playhead * duration), pillStyle.copy(color = colors.bg))
                    drawTextPill(clock, x, 8.dp.toPx(), 20.dp.toPx(), pillPadding, colors.fg)
                }
            },
    ) {
        if (!state.isOpen) {
            Text(
                text = stringResource(Res.string.waveform_empty),
                style = type.meta,
                color = colors.fg2,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = DownstepDimens.gutter),
            )
        }
        state.active?.let { active ->
            val start = active.start
            val end = active.end
            HandleTarget(
                description = stringResource(Res.string.marker_a_description),
                x = { overviewAxis(width.intValue.toFloat(), DownstepDimens.gutter.toPx()).x(drag.shown(LoopBounds(start, end)).start) },
            )
            HandleTarget(
                description = stringResource(Res.string.marker_b_description),
                x = { overviewAxis(width.intValue.toFloat(), DownstepDimens.gutter.toPx()).x(drag.shown(LoopBounds(start, end)).end) },
            )
        }
    }
}

/**
 * An invisible strip over an A or B line: the hand cursor and the accessible name. The drag itself is
 * the parent's pointer input; [x] is read while placing, so a drag moves it without recomposing.
 */
@Composable
internal fun HandleTarget(description: String, x: Density.() -> Float, width: Dp = HANDLE_WIDTH) {
    Box(
        modifier = Modifier
            .offset { IntOffset((x() - width.toPx() / 2).roundToInt(), 0) }
            .width(width)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = description },
    )
}

private fun overviewAxis(width: Float, gutter: Float) = WaveformAxis(gutter, (width - gutter * 2).coerceAtLeast(0f), 0f, 1f)

private fun OverviewWaveformState.dragTarget(): DragTarget? =
    active?.let { DragTarget(it.id, LoopBounds(it.start, it.end), durationSeconds, snapGrid) }

/** "A 1:00.0" with the letter bold and the time lighter, as in the design. */
private fun marker(template: String, time: String): AnnotatedString = buildAnnotatedString {
    val at = template.indexOf(PLACEHOLDER)
    if (at < 0) {
        append(template)
        return@buildAnnotatedString
    }
    append(template.substring(0, at))
    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(time) }
    append(template.substring(at + PLACEHOLDER.length))
}

/** Stands in for a value only known while drawing (the playhead, a dragged edge). */
internal const val PLACEHOLDER = "\u0001"

private const val BAR_COUNT = 220
private val BAR_GAP = 2.dp
private val BAR_RADIUS = 3.dp
private val MIN_BAR = 2.dp
private val BARS_INSET = 30.dp
private val HANDLE_WIDTH = 16.dp
// A 12 px box-shadow spreads its colour thin: the gradient peaks well under the token's alpha.
private const val GLOW_STRENGTH = 0.35f
