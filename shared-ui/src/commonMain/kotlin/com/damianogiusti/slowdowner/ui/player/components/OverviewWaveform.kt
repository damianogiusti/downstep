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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.ui.common.MovePointerIcon
import com.damianogiusti.slowdowner.ui.common.ResizeHorizontalPointerIcon
import com.damianogiusti.slowdowner.ui.common.formatClock
import com.damianogiusti.slowdowner.ui.common.formatTimecode
import com.damianogiusti.slowdowner.ui.player.LoopRegionModel
import com.damianogiusti.slowdowner.ui.player.OverviewWaveformState
import com.damianogiusti.slowdowner.ui.player.WaveformActions
import com.damianogiusti.slowdowner.ui.player.components.waveform.BarColors
import com.damianogiusti.slowdowner.ui.player.components.waveform.DRAG_THRESHOLD
import com.damianogiusti.slowdowner.ui.player.components.waveform.HitRegion
import com.damianogiusti.slowdowner.ui.player.components.waveform.LoopDragState
import com.damianogiusti.slowdowner.ui.player.components.waveform.LoopGestureActions
import com.damianogiusti.slowdowner.ui.player.components.waveform.LoopHandle
import com.damianogiusti.slowdowner.ui.player.components.waveform.LoopHit
import com.damianogiusti.slowdowner.ui.player.components.waveform.WaveformAxis
import com.damianogiusti.slowdowner.ui.player.components.waveform.barShapes
import com.damianogiusti.slowdowner.ui.player.components.waveform.barsIn
import com.damianogiusti.slowdowner.ui.player.components.waveform.drawBars
import com.damianogiusti.slowdowner.ui.player.components.waveform.drawGlow
import com.damianogiusti.slowdowner.ui.player.components.waveform.drawTextPill
import com.damianogiusti.slowdowner.ui.player.components.waveform.drawVerticalLine
import com.damianogiusti.slowdowner.ui.player.components.waveform.hitLoop
import com.damianogiusti.slowdowner.ui.player.components.waveform.loopGestures
import com.damianogiusti.slowdowner.ui.player.components.waveform.normalizeLevels
import com.damianogiusti.slowdowner.ui.player.components.waveform.playedBars
import com.damianogiusti.slowdowner.ui.player.components.waveform.resamplePeaks
import com.damianogiusti.slowdowner.ui.resources.*
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The full-width overview: bars from [OverviewWaveformState.peaks], loop regions, A/B pills
 * and drag handles, the zoom bracket, suggestions, and the playhead with its timecode pill.
 *
 * Every loop is editable here, active or not: its thick top bar drags the whole loop (keeping its
 * length), its edges resize it, and a drag that picks up a loop that is not active makes it active.
 * A click on a top bar plays that loop; a click anywhere else seeks (README §3, the pointer table).
 * [drag] is shared with the zoom strip, so both draw a drag live.
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
    drag: LoopDragState = remember { LoopDragState() },
) {
    val colors = SlowDownerTheme.colors
    val type = SlowDownerTheme.type
    val measurer = rememberTextMeasurer()
    val current by rememberUpdatedState(state)
    // The loop part under the mouse, for the cursor and the raised bar; null over the bare waveform.
    val hover = remember { mutableStateOf<LoopHit?>(null) }
    LaunchedEffect(state.regions) {
        val dragged = drag.loopId ?: return@LaunchedEffect
        drag.settle(dragged, state.regions.firstOrNull { it.id == dragged }?.bounds)
    }
    LaunchedEffect(state.isOpen, state.enabled) { if (!state.isOpen || !state.enabled) hover.value = null }
    // Only for the invisible A/B handles, which are placed in the layout phase.
    val width = remember { mutableIntStateOf(0) }

    val separator = stringResource(Res.string.decimal_separator)
    val markerA = stringResource(Res.string.marker_a, PLACEHOLDER)
    val markerB = stringResource(Res.string.marker_b, PLACEHOLDER)
    val description = stringResource(Res.string.waveform_description, formatClock(state.durationSeconds))
    val pillStyle = type.tag.copy(fontSize = 11.sp)
    val labelStyle = type.tag.copy(fontSize = 10.sp, color = colors.amber)
    val durationStyle = type.meta.copy(fontSize = 11.sp, color = colors.fg2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.bgDeep)
            .onSizeChanged { width.intValue = it.width }
            // No playhead here: semantics would then be rebuilt on every frame of playback.
            .semantics { contentDescription = description }
            .pointerHoverIcon(pointerIconFor(hover.value?.handle))
            .pointerInput(drag) {
                // Hover only: never consumes. The state is written when the part under the mouse changes.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // While pressed the cursor keeps what the press grabbed.
                        if (event.changes.any { it.pressed }) continue
                        val at = event.changes.firstOrNull()?.position
                        val axis = current.takeIf { it.isOpen && it.enabled }
                            ?.let { overviewAxis(size.width.toFloat(), DownstepDimens.gutter.toPx()) }
                        hover.value = if (event.type == PointerEventType.Exit || at == null || axis == null) {
                            null
                        } else {
                            hitLoop(at.x, at.y, current.hitRegions(drag), axis, HANDLE_WIDTH.toPx() / 2, TOP_BAR_HIT.toPx())
                        }
                    }
                }
            }
            .pointerInput(actions, drag) {
                val gutter = DownstepDimens.gutter.toPx()
                loopGestures(
                    drag = drag,
                    reach = HANDLE_WIDTH.toPx() / 2,
                    barHeight = TOP_BAR_HIT.toPx(),
                    threshold = DRAG_THRESHOLD.toPx(),
                    durationSeconds = { current.durationSeconds },
                    axis = { size -> current.takeIf { it.isOpen && it.enabled }?.let { overviewAxis(size.width.toFloat(), gutter) } },
                    regions = { current.hitRegions(null) },
                    actions = LoopGestureActions(
                        onSeek = actions.onOverviewClick,
                        onBarClick = actions.onLoopBarClick,
                        onGrab = actions.onLoopGrabbed,
                        onCommit = actions.onLoopBoundsChanged,
                    ),
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
                val bar = TOP_BAR.toPx()
                val barRaised = TOP_BAR_RAISED.toPx()
                val barRadius = 2.dp.toPx()
                val grip = 1.25.dp.toPx()
                val gripGap = 4.dp.toPx()
                val gripMinWidth = GRIP_MIN_WIDTH.toPx()
                val pillHeight = 22.dp.toPx()
                val pillPadding = 8.dp.toPx()

                onDrawBehind {
                    if (!state.isOpen) return@onDrawBehind
                    val active = state.active
                    val dragged = drag.loopId
                    val hovered = hover.value
                    // The loop the bars and A/B pills follow: one being dragged (picked up before it is
                    // active), else the active one.
                    val lead = state.regions.firstOrNull { it.id == dragged } ?: active
                    val leadBounds = lead?.let { drag.shown(it.id, it.bounds) }
                    val playhead = position().coerceIn(0f, 1f)

                    // Region fills under everything.
                    for (region in state.regions) {
                        val bounds = drag.shown(region.id, region.bounds)
                        val alpha = when {
                            region.isActive && state.isLoopOn -> 0.16f
                            region.isActive || region.id == dragged -> 0.08f
                            else -> 0.05f
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

                    if (bars != null) {
                        val loopColor = lead?.let { colors.loopColor(it.colorIndex) } ?: colors.amber
                        drawBars(
                            bars = bars,
                            played = playedBars(playhead, 0f, 1f, bars.count),
                            loop = leadBounds?.let { barsIn(it.start, it.end, 0f, 1f, bars.count) } ?: IntRange.EMPTY,
                            colors = BarColors(colors.barPlayed, colors.barMuted, loopColor, loopColor.copy(alpha = 0.5f)),
                        )
                    }

                    // Edges and top bars over the bars; the lead loop on top of the others. A bar under the
                    // mouse or in a drag is raised and opaque: it is the grab handle.
                    for (region in state.regions.sortedBy { it.id == lead?.id }) {
                        val bounds = drag.shown(region.id, region.bounds)
                        val isLead = region.id == lead?.id
                        val isHeld = region.id == dragged || (hovered?.loopId == region.id && hovered.handle == LoopHandle.Bar)
                        val loopColor = colors.loopColor(region.colorIndex)
                        val barColor = loopColor.copy(alpha = if (isLead || isHeld) 1f else INACTIVE_BAR_ALPHA)
                        val edgeColor = loopColor.copy(alpha = if (isLead) 1f else INACTIVE_EDGE_ALPHA)
                        val left = axis.x(bounds.start)
                        val right = axis.x(bounds.end)
                        val thickness = if (isHeld) barRaised else bar
                        drawRoundRect(barColor, Offset(left, 0f), Size(right - left, thickness), CornerRadius(min(barRadius, (right - left) / 2)))
                        if (right - left > gripMinWidth) {
                            // Three dots in the middle: something to grab.
                            val centre = (left + right) / 2
                            for (dot in -1..1) {
                                drawCircle(colors.bgDeep.copy(alpha = GRIP_ALPHA), grip, Offset(centre + dot * gripGap, thickness / 2))
                            }
                        }
                        val hoveredEdge = hovered?.takeIf { it.loopId == region.id }?.handle
                        drawVerticalLine(edgeColor, left, if (hoveredEdge == LoopHandle.Start) px * 2 else px)
                        drawVerticalLine(edgeColor, right, if (hoveredEdge == LoopHandle.End) px * 2 else px)
                    }

                    if (active != null) {
                        state.zoomWindow?.let { window ->
                            val bracket = colors.fg2.copy(alpha = 0.35f)
                            drawVerticalLine(bracket, axis.x(window.start), px)
                            drawVerticalLine(bracket, axis.x(window.end), px)
                        }
                    }
                    if (leadBounds != null) {
                        val top = size.height - 8.dp.toPx() - pillHeight
                        val a = measurer.measure(marker(markerA, formatTimecode(leadBounds.start * duration, separator)), pillStyle.copy(color = colors.onAmber))
                        val b = measurer.measure(marker(markerB, formatTimecode(leadBounds.end * duration, separator)), pillStyle.copy(color = colors.onTeal))
                        drawTextPill(a, axis.x(leadBounds.start), top, pillHeight, pillPadding, colors.amber)
                        drawTextPill(b, axis.x(leadBounds.end), top, pillHeight, pillPadding, colors.teal)
                    }

                    durationText?.let {
                        drawText(it, topLeft = Offset(size.width - gutter - it.size.width, size.height - 10.dp.toPx() - it.size.height))
                    }

                    val x = axis.x(playhead)
                    drawGlow(colors.fgGlow.copy(alpha = colors.fgGlow.alpha * GLOW_STRENGTH), x, 8.dp.toPx())
                    drawVerticalLine(colors.fg, x, 2.dp.toPx())
                    val clock = measurer.measure(formatClock(playhead * duration), pillStyle.copy(color = colors.bg))
                    // Below the loops' top bars, raised ones included: it never covers a grab handle.
                    drawTextPill(clock, x, CLOCK_TOP.toPx(), 20.dp.toPx(), pillPadding, colors.fg)
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
            val id = active.id
            val bounds = active.bounds
            val axis: Density.() -> WaveformAxis = { overviewAxis(width.intValue.toFloat(), DownstepDimens.gutter.toPx()) }
            // The parent's pointer input drags and sets the cursor: these only name the parts.
            TopBarTarget(
                description = stringResource(Res.string.loop_move_description),
                left = { axis().x(drag.shown(id, bounds).start) },
                right = { axis().x(drag.shown(id, bounds).end) },
            )
            HandleTarget(
                description = stringResource(Res.string.marker_a_description),
                x = { axis().x(drag.shown(id, bounds).start) },
                icon = null,
            )
            HandleTarget(
                description = stringResource(Res.string.marker_b_description),
                x = { axis().x(drag.shown(id, bounds).end) },
                icon = null,
            )
        }
    }
}

/**
 * An invisible strip over an A or B line: the resize cursor ([icon]; null when the parent sets the
 * cursor) and the accessible name. The drag itself is the parent's pointer input; [x] is read while
 * placing, so a drag moves it without recomposing.
 */
@Composable
internal fun HandleTarget(
    description: String,
    x: Density.() -> Float,
    width: Dp = HANDLE_WIDTH,
    icon: PointerIcon? = ResizeHorizontalPointerIcon,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset((x() - width.toPx() / 2).roundToInt(), 0) }
            .width(width)
            .fillMaxHeight()
            .then(if (icon != null) Modifier.pointerHoverIcon(icon) else Modifier)
            .semantics { contentDescription = description },
    )
}

/** The active loop's top bar, named for accessibility; [left] and [right] are read while measuring. */
@Composable
private fun TopBarTarget(description: String, left: Density.() -> Float, right: Density.() -> Float) {
    Box(
        modifier = Modifier
            .layout { measurable, _ ->
                val start = left()
                val barWidth = (right() - start).roundToInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(barWidth, TOP_BAR_HIT.roundToPx()))
                layout(placeable.width, placeable.height) { placeable.place(start.roundToInt(), 0) }
            }
            .semantics { contentDescription = description },
    )
}

/** Move over a top bar, resize over an edge, else the crosshair of a seek. */
internal fun pointerIconFor(handle: LoopHandle?): PointerIcon = when (handle) {
    LoopHandle.Bar -> MovePointerIcon
    LoopHandle.Start, LoopHandle.End -> ResizeHorizontalPointerIcon
    null -> PointerIcon.Crosshair
}

private fun overviewAxis(width: Float, gutter: Float) = WaveformAxis(gutter, (width - gutter * 2).coerceAtLeast(0f), 0f, 1f)

/** Every loop as the gestures hit-test it; with [drag], a dropped loop is where it is drawn until its commit lands. */
private fun OverviewWaveformState.hitRegions(drag: LoopDragState?): List<HitRegion> =
    regions.map { HitRegion(it.id, drag?.shown(it.id, it.bounds) ?: it.bounds, it.isActive) }

private val LoopRegionModel.bounds: LoopBounds get() = LoopBounds(start, end)

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
/** A loop's top bar: clearly a grab handle (README §2.4), raised while hovered or dragged. */
private val TOP_BAR = 7.dp
private val TOP_BAR_RAISED = 11.dp
/** The bar's grab zone, from the top: taller than the bar, so it is easy to catch. */
private val TOP_BAR_HIT = 16.dp
private val CLOCK_TOP = 14.dp
private val GRIP_MIN_WIDTH = 28.dp
private const val GRIP_ALPHA = 0.55f
private const val INACTIVE_BAR_ALPHA = 0.5f
private const val INACTIVE_EDGE_ALPHA = 0.35f
// A 12 px box-shadow spreads its colour thin: the gradient peaks well under the token's alpha.
private const val GLOW_STRENGTH = 0.35f
