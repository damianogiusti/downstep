package com.damianogiusti.slowdowner.ui.player.components.timeline

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** The bars of a waveform as one path, so a frame only clips and fills it, whatever the playhead does. */
internal class BarShapes(val path: Path, val count: Int, private val step: Float, private val gap: Float) {
    /** x between bar [index] − 1 and bar [index]: where a colour changes. */
    fun boundary(index: Int): Float = index * step - gap / 2
}

internal class BarStyle(val gap: Float, val radius: Float, val minHeight: Float)

/** [levels] 0..1 as bars across the width, centred on [centerY], at most [maxHeight] tall. */
internal fun DrawScope.barShapes(levels: FloatArray, centerY: Float, maxHeight: Float, style: BarStyle): BarShapes {
    val count = levels.size
    val path = Path()
    if (count == 0) return BarShapes(path, 0, 0f, style.gap)
    val step = (size.width + style.gap) / count
    val barWidth = max(1f, step - style.gap)
    for (i in 0 until count) {
        val height = max(style.minHeight, levels[i] * maxHeight)
        val left = i * step
        val corner = CornerRadius(minOf(style.radius, barWidth / 2, height / 2))
        path.addRoundRect(RoundRect(left, centerY - height / 2, left + barWidth, centerY + height / 2, corner))
    }
    return BarShapes(path, count, step, style.gap)
}

internal class BarColors(val played: Color, val unplayed: Color, val loopPlayed: Color, val loopUnplayed: Color)

/** Fills [bars]: the first [played] in the played colours, those in [loop] in the loop's. */
internal fun DrawScope.drawBars(bars: BarShapes, played: Int, loop: IntRange, colors: BarColors) {
    if (bars.count == 0) return
    val cuts = mutableListOf(0, bars.count, played.coerceIn(0, bars.count))
    if (!loop.isEmpty()) {
        cuts += loop.first.coerceIn(0, bars.count)
        cuts += (loop.last + 1).coerceIn(0, bars.count)
    }
    val points = cuts.distinct().sorted()
    for (k in 0 until points.size - 1) {
        val first = points[k]
        val end = points[k + 1]
        val inLoop = first in loop
        val isPlayed = first < played
        val color = when {
            inLoop && isPlayed -> colors.loopPlayed
            inLoop -> colors.loopUnplayed
            isPlayed -> colors.played
            else -> colors.unplayed
        }
        val left = if (first == 0) 0f else bars.boundary(first)
        val right = if (end == bars.count) size.width else bars.boundary(end)
        clipRect(left = left, right = right) { drawPath(bars.path, color) }
    }
}

/** A plain rounded track, for a timeline without a waveform. */
internal fun DrawScope.drawTrack(color: Color, centerY: Float, height: Float, width: Float) {
    if (width <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, centerY - height / 2),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2),
    )
}

/** Where a marker's label sits: A's left of its line, B's right, so a short loop shows both. */
internal enum class LabelSide { Left, Right }

internal class MarkerStyle(val color: Color, val labelColor: Color, val side: LabelSide)

/**
 * A marker line from [top] to [bottom] at [x], with its [label] in a pill above. The pill hangs on the
 * [MarkerStyle.side] of the line, and stays inside the view at the ends of the file.
 */
internal fun DrawScope.drawMarker(label: TextLayoutResult, x: Float, top: Float, bottom: Float, style: MarkerStyle) {
    val lineWidth = 2.dp.toPx()
    drawRect(style.color, topLeft = Offset(x - lineWidth / 2, top), size = Size(lineWidth, bottom - top))
    val pill = Size(22.dp.toPx(), 18.dp.toPx())
    val preferred = if (style.side == LabelSide.Left) x + lineWidth / 2 - pill.width else x - lineWidth / 2
    val left = preferred.coerceIn(0f, (size.width - pill.width).coerceAtLeast(0f))
    drawRoundRect(style.color, topLeft = Offset(left, 0f), size = pill, cornerRadius = CornerRadius(6.dp.toPx()))
    drawText(label, color = style.labelColor, topLeft = Offset(left + (pill.width - label.size.width) / 2, (pill.height - label.size.height) / 2))
}

/** The top of the marker pills: what sits below a marker's label. */
internal fun DrawScope.labelHeight(): Float = 18.dp.toPx()
