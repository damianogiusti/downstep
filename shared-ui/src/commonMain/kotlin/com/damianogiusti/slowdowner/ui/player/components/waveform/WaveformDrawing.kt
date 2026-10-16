package com.damianogiusti.slowdowner.ui.player.components.waveform

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlin.math.max

/**
 * The bars of a waveform as one path, built once per size and peaks (in drawWithCache): a frame then
 * only clips and fills it in up to four colours, whatever the playhead does.
 */
internal class BarShapes(val path: Path, val count: Int, private val left: Float, private val step: Float, private val gap: Float) {
    /** x between bar [index] − 1 and bar [index]: where a colour change is clipped. */
    fun boundary(index: Int): Float = left + index * step - gap / 2
}

/** [levels] 0..1 as bars across [axis], centred on [centerY], at most [maxHeight] tall. */
internal fun barShapes(levels: FloatArray, axis: WaveformAxis, centerY: Float, maxHeight: Float, gap: Float, radius: Float, minHeight: Float): BarShapes {
    val count = levels.size
    val path = Path()
    if (count == 0) return BarShapes(path, 0, axis.left, 0f, gap)
    val step = (axis.width + gap) / count
    val barWidth = max(1f, step - gap)
    for (i in 0 until count) {
        val height = max(minHeight, levels[i] * maxHeight)
        val left = axis.left + i * step
        val corner = CornerRadius(minOf(radius, barWidth / 2, height / 2))
        path.addRoundRect(RoundRect(left, centerY - height / 2, left + barWidth, centerY + height / 2, corner))
    }
    return BarShapes(path, count, axis.left, step, gap)
}

internal class BarColors(val played: Color, val unplayed: Color, val loopPlayed: Color, val loopUnplayed: Color)

/** Fills [bars]: the first [played] in the played colours, those in [loop] in the loop's colours. */
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
        if (end <= first) continue
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

/**
 * A rounded pill around [text], centred on [centerX] but kept inside [minX]..[maxX] so it never
 * leaves the view at the edges of the file.
 */
internal fun DrawScope.drawTextPill(
    text: TextLayoutResult,
    centerX: Float,
    top: Float,
    height: Float,
    paddingHorizontal: Float,
    color: Color,
    minX: Float = 0f,
    maxX: Float = size.width,
) {
    val width = text.size.width + paddingHorizontal * 2
    val left = (centerX - width / 2).coerceAtMost(maxX - width).coerceAtLeast(minX)
    drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(height / 2))
    drawText(text, topLeft = Offset(left + paddingHorizontal, top + (height - text.size.height) / 2))
}

/** A vertical line [width] wide, centred on [x], over the full height. */
internal fun DrawScope.drawVerticalLine(color: Color, x: Float, width: Float, top: Float = 0f, bottom: Float = size.height) {
    drawRect(color, Offset(x - width / 2, top), Size(width, bottom - top))
}

/** The playhead's soft glow: a horizontal falloff [radius] each side, cheaper than a blur. */
internal fun DrawScope.drawGlow(color: Color, x: Float, radius: Float) {
    drawRect(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent), startX = x - radius, endX = x + radius),
        topLeft = Offset(x - radius, 0f),
        size = Size(radius * 2, size.height),
    )
}
