package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.common.formatClock
import com.damianogiusti.slowdowner.ui.player.PlayerControlsState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.tabular
import kotlin.math.abs

/**
 * Seek bar and A/B loop editor in one. Drag A or B to move the loop, anywhere else to seek.
 * Changes are reported only when the finger lifts: every intermediate seek would stutter.
 */
@Composable
fun LoopTimeline(
    controls: PlayerControlsState,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    onLoopRegionChanged: (start: Float, end: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<TimelineDrag?>(null) }
    val current by rememberUpdatedState(controls)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnLoop by rememberUpdatedState(onLoopRegionChanged)

    val shown = drag
    val position = (shown as? TimelineDrag.Seek)?.position ?: controls.position
    val draggedStart = (shown as? TimelineDrag.Marker)?.takeIf { it.isStart }?.position ?: controls.loopStart
    val draggedEnd = (shown as? TimelineDrag.Marker)?.takeUnless { it.isStart }?.position ?: controls.loopEnd
    // A marker dragged past the other swaps roles, as SetLoopRegionUseCase does on release.
    val loopStart = minOf(draggedStart, draggedEnd)
    val loopEnd = maxOf(draggedStart, draggedEnd)

    val colors = MaterialTheme.colorScheme
    val practiceColors = SlowDownerTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val description = stringResource(
        Res.string.timeline_description,
        formatClock(position * controls.durationSeconds),
        formatClock(controls.durationSeconds),
    )

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .semantics {
                    contentDescription = description
                    if (enabled) setProgress { value -> currentOnSeek(value.coerceIn(0f, 1f)); true }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat()
                        fun fraction(x: Float) = (x / width).coerceIn(0f, 1f)
                        val state = current
                        val hitSlop = 24.dp.toPx()
                        val startX = state.loopStart * width
                        val endX = state.loopEnd * width
                        val downX = down.position.x
                        var gesture: TimelineDrag = when {
                            !state.hasLoopPoints -> TimelineDrag.Seek(fraction(downX))
                            // On overlap, the nearest marker wins.
                            abs(downX - endX) <= hitSlop && abs(downX - endX) <= abs(downX - startX) ->
                                TimelineDrag.Marker(isStart = false, position = state.loopEnd)
                            abs(downX - startX) <= hitSlop -> TimelineDrag.Marker(isStart = true, position = state.loopStart)
                            else -> TimelineDrag.Seek(fraction(downX))
                        }
                        if (gesture is TimelineDrag.Seek) drag = gesture
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
                                gesture = when (val g = gesture) {
                                    is TimelineDrag.Seek -> g.copy(position = fraction(change.position.x))
                                    is TimelineDrag.Marker -> g.copy(position = fraction(change.position.x))
                                }
                                drag = gesture
                            }
                        }
                        drag = null
                        if (isCancelled) return@awaitEachGesture
                        when (val g = gesture) {
                            is TimelineDrag.Seek -> currentOnSeek(g.position)
                            // A tap near a marker is a seek: only a real drag moves the loop.
                            is TimelineDrag.Marker -> if (isDragging) {
                                currentOnLoop(g.position, if (g.isStart) state.loopEnd else state.loopStart)
                            } else {
                                currentOnSeek(fraction(downX))
                            }
                        }
                    }
                },
        ) {
            val trackY = size.height * 0.55f
            val trackHeight = 8.dp.toPx()
            val markerTop = 14.dp.toPx()
            val markerBottom = size.height - 6.dp.toPx()
            val playX = position * size.width

            if (controls.hasLoopPoints) {
                val alpha = if (controls.isLoopOn) 1f else 0.45f
                drawRoundRect(
                    color = practiceColors.loopRegion.copy(alpha = practiceColors.loopRegion.alpha * alpha),
                    topLeft = Offset(loopStart * size.width, markerTop),
                    size = Size((loopEnd - loopStart) * size.width, markerBottom - markerTop),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
            drawTrack(practiceColors.timelineTrack, trackY, trackHeight, size.width)
            drawTrack(colors.primary.copy(alpha = if (enabled) 1f else 0.4f), trackY, trackHeight, playX)
            if (controls.hasLoopPoints) {
                val alpha = if (controls.isLoopOn) 1f else 0.45f
                drawMarker("A", loopStart * size.width, practiceColors.markerA.copy(alpha = alpha), colors.onPrimary, markerTop, markerBottom, textMeasurer, labelStyle)
                drawMarker("B", loopEnd * size.width, practiceColors.markerB.copy(alpha = alpha), colors.background, markerTop, markerBottom, textMeasurer, labelStyle)
            }
            drawCircle(colors.onSurface, radius = 10.dp.toPx(), center = Offset(playX, trackY))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val time = MaterialTheme.typography.labelMedium.tabular()
            Text(formatClock(position * controls.durationSeconds), style = time)
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                if (controls.hasLoopPoints) {
                    Text("A ${formatClock(loopStart * controls.durationSeconds)}", style = time, color = practiceColors.markerA)
                    Spacer(Modifier.width(16.dp))
                    Text("B ${formatClock(loopEnd * controls.durationSeconds)}", style = time, color = practiceColors.markerB)
                }
            }
            Text(formatClock(controls.durationSeconds), style = time, color = colors.onSurfaceVariant)
        }
    }
}

private sealed interface TimelineDrag {
    data class Seek(val position: Float) : TimelineDrag
    data class Marker(val isStart: Boolean, val position: Float) : TimelineDrag
}

private fun DrawScope.drawTrack(color: Color, centerY: Float, height: Float, width: Float) {
    if (width <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, centerY - height / 2),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2),
    )
}

private fun DrawScope.drawMarker(
    label: String,
    x: Float,
    color: Color,
    labelColor: Color,
    top: Float,
    bottom: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
) {
    val lineWidth = 2.dp.toPx()
    drawRect(color, topLeft = Offset(x - lineWidth / 2, top), size = Size(lineWidth, bottom - top))
    val pill = Size(22.dp.toPx(), 18.dp.toPx())
    val pillTopLeft = Offset((x - pill.width / 2).coerceIn(0f, size.width - pill.width), 0f)
    drawRoundRect(color, topLeft = pillTopLeft, size = pill, cornerRadius = CornerRadius(6.dp.toPx()))
    val text = textMeasurer.measure(label, style.copy(color = labelColor))
    drawText(
        text,
        topLeft = Offset(
            pillTopLeft.x + (pill.width - text.size.width) / 2,
            pillTopLeft.y + (pill.height - text.size.height) / 2,
        ),
    )
}
