package com.damianogiusti.slowdowner.ui.player.components.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.player.PlayerControlsState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlin.math.abs

/** A drag in progress, drawn at once but reported only on release: every intermediate seek would stutter. */
internal sealed interface TimelineDrag {
    data class Seek(val position: Float) : TimelineDrag
    data class Marker(val marker: LoopMarker, val position: Float) : TimelineDrag
}

/** Position and A/B as drawn: the engine's, or a drag's while it lasts. */
internal class ShownTimeline(val position: Float, val loopStart: Float, val loopEnd: Float) {
    companion object {
        fun of(controls: PlayerControlsState, drag: TimelineDrag?): ShownTimeline {
            val marker = drag as? TimelineDrag.Marker
            val start = marker?.takeIf { it.marker == LoopMarker.A }?.position ?: controls.loopStart
            val end = marker?.takeIf { it.marker == LoopMarker.B }?.position ?: controls.loopEnd
            // A marker dragged past the other swaps roles, as SetLoopRegionUseCase does on release.
            return ShownTimeline((drag as? TimelineDrag.Seek)?.position ?: controls.position, minOf(start, end), maxOf(start, end))
        }
    }
}

internal class TimelineCanvasState(
    val controls: PlayerControlsState,
    val window: TimelineWindow,
    /** Bar levels 0..1 across the window; null draws the plain track of a timeline without a waveform. */
    val levels: FloatArray?,
    val drag: TimelineDrag?,
    val isEnabled: Boolean,
)

internal class TimelineCanvasActions(
    /** The drag to draw, null when it ends. Shared by the timelines of one panel, so each shows the other's. */
    val onDrag: (TimelineDrag?) -> Unit,
    val onSeek: (Float) -> Unit,
    val onLoopRegionChanged: (start: Float, end: Float) -> Unit,
)

/**
 * A timeline over [TimelineCanvasState.window]: drag A or B to move the loop, anywhere else to seek.
 * Used whole (the classic timeline and the waveform) and zoomed on the loop.
 */
@Composable
internal fun TimelineCanvas(state: TimelineCanvasState, actions: TimelineCanvasActions, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(state)
    val currentActions by rememberUpdatedState(actions)
    val colors = MaterialTheme.colorScheme
    val practiceColors = SlowDownerTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val shown = ShownTimeline.of(state.controls, state.drag)

    Canvas(
        modifier = modifier.pointerInput(state.isEnabled, state.window) {
            if (!state.isEnabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                val axis = TimelineAxis(current.window, size.width.toFloat())
                val controls = current.controls
                val marker = if (controls.hasLoopPoints) {
                    hitMarker(down.position.x, axis.x(controls.loopStart), axis.x(controls.loopEnd), MARKER_REACH.toPx())
                } else {
                    null
                }
                var gesture: TimelineDrag = when (marker) {
                    LoopMarker.A -> TimelineDrag.Marker(marker, controls.loopStart)
                    LoopMarker.B -> TimelineDrag.Marker(marker, controls.loopEnd)
                    null -> TimelineDrag.Seek(axis.fraction(down.position.x)).also(currentActions.onDrag)
                }
                val touchSlop = viewConfiguration.touchSlop
                var travel = Offset.Zero
                var isDragging = false
                var isCancelled = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        isCancelled = true
                        break
                    }
                    if (!change.pressed) break
                    if (!isDragging) {
                        travel += change.positionChange()
                        // A vertical move is the page scrolling: let it have the gesture.
                        if (abs(travel.y) > touchSlop && abs(travel.y) > abs(travel.x)) {
                            isCancelled = true
                            break
                        }
                        isDragging = abs(travel.x) > touchSlop
                    }
                    if (isDragging) {
                        change.consume()
                        val position = axis.fraction(change.position.x)
                        gesture = when (val g = gesture) {
                            is TimelineDrag.Seek -> g.copy(position = position)
                            is TimelineDrag.Marker -> g.copy(position = position)
                        }
                        currentActions.onDrag(gesture)
                    }
                }
                currentActions.onDrag(null)
                if (isCancelled) return@awaitEachGesture
                when (val g = gesture) {
                    is TimelineDrag.Seek -> currentActions.onSeek(g.position)
                    // A tap near a marker is a seek: only a real drag moves the loop.
                    is TimelineDrag.Marker -> if (isDragging) {
                        val other = if (g.marker == LoopMarker.A) controls.loopEnd else controls.loopStart
                        currentActions.onLoopRegionChanged(g.position, other)
                    } else {
                        currentActions.onSeek(axis.fraction(down.position.x))
                    }
                }
            }
        },
    ) {
        val axis = TimelineAxis(state.window, size.width)
        val markerTop = labelHeight() - 4.dp.toPx()
        val levels = state.levels
        val markerBottom = size.height - (if (levels == null) 6.dp else 2.dp).toPx()
        val hasLoop = state.controls.hasLoopPoints
        val loopAlpha = if (state.controls.isLoopOn) 1f else PARKED_LOOP_ALPHA
        val startX = axis.x(shown.loopStart)
        val endX = axis.x(shown.loopEnd)
        val playX = axis.x(shown.position)
        val played = colors.primary.copy(alpha = if (state.isEnabled) 1f else DISABLED_ALPHA)

        if (hasLoop) {
            drawRoundRect(
                color = practiceColors.loopRegion.copy(alpha = practiceColors.loopRegion.alpha * loopAlpha),
                topLeft = Offset(startX, markerTop),
                size = Size(endX - startX, markerBottom - markerTop),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
        }
        val trackY = size.height * 0.55f
        if (levels == null) {
            drawTrack(practiceColors.timelineTrack, trackY, 8.dp.toPx(), size.width)
            drawTrack(played, trackY, 8.dp.toPx(), playX)
        } else {
            val barsTop = labelHeight() + 4.dp.toPx()
            val bars = barShapes(levels, (barsTop + markerBottom) / 2, markerBottom - barsTop, BarStyle(1.dp.toPx(), 1.dp.toPx(), 2.dp.toPx()))
            val unplayed = colors.onSurfaceVariant.copy(alpha = UNPLAYED_BAR_ALPHA)
            drawBars(
                bars,
                played = playedBars(shown.position, state.window, levels.size),
                loop = if (hasLoop) barsIn(shown.loopStart, shown.loopEnd, state.window, levels.size) else IntRange.EMPTY,
                colors = BarColors(played = played, unplayed = unplayed, loopPlayed = played, loopUnplayed = colors.onSurfaceVariant),
            )
        }
        if (hasLoop) {
            drawMarker(
                textMeasurer.measure("A", labelStyle),
                startX,
                markerTop,
                markerBottom,
                MarkerStyle(practiceColors.markerA.copy(alpha = loopAlpha), colors.onPrimary, LabelSide.Left),
            )
            drawMarker(
                textMeasurer.measure("B", labelStyle),
                endX,
                markerTop,
                markerBottom,
                MarkerStyle(practiceColors.markerB.copy(alpha = loopAlpha), colors.background, LabelSide.Right),
            )
        }
        if (levels == null) {
            drawCircle(colors.onSurface, radius = 10.dp.toPx(), center = Offset(playX, trackY))
        } else {
            val width = 2.dp.toPx()
            drawRect(colors.onSurface, topLeft = Offset(playX - width / 2, markerTop), size = Size(width, markerBottom - markerTop))
        }
    }
}

/** How far from A or B a press still grabs it. */
private val MARKER_REACH = 24.dp
private const val PARKED_LOOP_ALPHA = 0.45f
private const val DISABLED_ALPHA = 0.4f
private const val UNPLAYED_BAR_ALPHA = 0.35f
