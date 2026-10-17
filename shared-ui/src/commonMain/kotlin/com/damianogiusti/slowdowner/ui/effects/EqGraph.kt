package com.damianogiusti.slowdowner.ui.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.effects.EqResponse
import com.damianogiusti.slowdowner.ui.common.formatDecimal
import com.damianogiusti.slowdowner.ui.common.formatSignedDecimal
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.decimal_separator
import com.damianogiusti.slowdowner.ui.resources.eq_band_readout
import com.damianogiusti.slowdowner.ui.resources.eq_graph_description
import com.damianogiusti.slowdowner.ui.resources.eq_graph_hint
import com.damianogiusti.slowdowner.ui.resources.gain_format
import com.damianogiusti.slowdowner.ui.resources.hz_format
import com.damianogiusti.slowdowner.ui.resources.khz_format
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.tabular
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The EQ as a frequency response: each band a bell with a node to drag (sideways = frequency, up and
 * down = gain; the wheel, or a drag with Shift or Alt, = width), the combined curve drawn bold and
 * each band's own faintly, live from the state ([EqResponse], the engine's own maths). A double
 * click on a node puts its gain back to 0 dB. Above it, a read-out of the band under the pointer.
 *
 * Common code only (no desktop pointer APIs): scroll and modifiers come from the pointer events.
 * Presses on a node are consumed, so neither the sheet nor its scroll moves under a drag.
 */
@Composable
fun EqGraph(
    curve: EqCurve,
    onBandChanged: (index: Int, band: EqBand) -> Unit,
    onBandGainReset: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = GRAPH_HEIGHT,
) {
    val colors = SlowDownerTheme.colors
    val type = SlowDownerTheme.type
    // The band under the pointer, or being dragged: highlighted, and read out above.
    var active by remember { mutableStateOf<Int?>(null) }

    val separator = stringResource(Res.string.decimal_separator)
    val activeBand = active?.let { curve.bands.getOrNull(it) }
    val readout = if (activeBand == null) stringResource(Res.string.eq_graph_hint) else stringResource(
        Res.string.eq_band_readout,
        formatFrequency(activeBand.frequencyHz, separator),
        stringResource(Res.string.gain_format, formatSignedDecimal(activeBand.gainDb, separator)),
        formatDecimal(activeBand.q, separator),
    )

    Column(modifier) {
        Text(
            text = readout,
            style = type.meta.tabular(),
            color = if (activeBand == null) colors.fg2 else colors.teal,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        EqGraphPlot(
            curve = curve,
            active = active,
            onActive = { active = it },
            onBandChanged = onBandChanged,
            onBandGainReset = onBandGainReset,
            modifier = Modifier.fillMaxWidth().height(height),
        )
    }
}

/** The plot alone: grid, curves and nodes, and the pointer handling. [active] is highlighted. */
@Composable
internal fun EqGraphPlot(
    curve: EqCurve,
    active: Int?,
    onActive: (Int?) -> Unit,
    onBandChanged: (index: Int, band: EqBand) -> Unit,
    onBandGainReset: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SlowDownerTheme.colors
    val type = SlowDownerTheme.type
    val measurer = rememberTextMeasurer()
    val current by rememberUpdatedState(curve)
    val setActive by rememberUpdatedState(onActive)
    val changeBand by rememberUpdatedState(onBandChanged)
    val resetGain by rememberUpdatedState(onBandGainReset)
    val separator = stringResource(Res.string.decimal_separator)
    val description = stringResource(Res.string.eq_graph_description)
    val labelStyle = type.meta.copy(fontSize = 10.sp, color = colors.fg2)

    Box(
        modifier = modifier
            .clip(SHAPE)
            .background(colors.bgDeep)
            .semantics { contentDescription = description }
            .pointerHoverIcon(if (active != null) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(Unit) {
                eqGestures(
                    bands = { current.bands },
                    onActive = { setActive(it) },
                    onChanged = { index, band -> changeBand(index, band) },
                    onReset = { resetGain(it) },
                )
            }
            .drawWithCache {
                val width = size.width
                val plotHeight = size.height
                val samples = EqGraphMath.sampleFrequencies(SAMPLES)
                val xs = samples.map { EqGraphMath.frequencyToX(it, width) }
                fun pathOf(gainAt: (Float) -> Double) = Path().apply {
                    samples.forEachIndexed { i, hz ->
                        val y = EqGraphMath.gainToY(gainAt(hz).toFloat(), plotHeight)
                        if (i == 0) moveTo(xs[i], y) else lineTo(xs[i], y)
                    }
                }
                val bandPaths = curve.bands.map { band -> pathOf { EqResponse.bandMagnitudeDb(band, it.toDouble()) } }
                // Drawn even while off, faded: the shape is kept for when it comes back on.
                val shaped = curve.copy(enabled = true)
                val combined = pathOf { EqResponse.curveMagnitudeDb(shaped, it.toDouble()) }
                val zeroY = EqGraphMath.gainToY(0f, plotHeight)
                val fill = Path().apply {
                    addPath(combined)
                    lineTo(width, zeroY)
                    lineTo(0f, zeroY)
                    close()
                }
                val frequencyLabels = EqGraphMath.GRID_HZ.map { measurer.measure(gridLabel(it), labelStyle) }
                val gainLabels = EqGraphMath.GRID_DB.map { measurer.measure(formatSignedDecimal(it, separator).substringBefore(separator), labelStyle) }
                val accent = if (curve.enabled) colors.amber else colors.fg2
                val px = 1.dp.toPx()
                val nodeRadius = NODE_RADIUS.toPx()
                val labelPad = 4.dp.toPx()
                onDrawBehind {
                    // Grid: decades and their halves, every 6 dB, 0 dB stronger.
                    EqGraphMath.GRID_HZ.forEachIndexed { i, hz ->
                        val x = EqGraphMath.frequencyToX(hz, width)
                        drawLine(colors.surface3, Offset(x, 0f), Offset(x, plotHeight), px)
                        val label = frequencyLabels[i]
                        val labelX = (x + labelPad).coerceAtMost(width - label.size.width - labelPad)
                        drawText(label, topLeft = Offset(labelX, plotHeight - label.size.height - labelPad / 2))
                    }
                    EqGraphMath.GRID_DB.forEachIndexed { i, db ->
                        val y = EqGraphMath.gainToY(db, plotHeight)
                        drawLine(if (db == 0f) colors.surface4 else colors.surface3, Offset(0f, y), Offset(width, y), if (db == 0f) 1.5f * px else px)
                        if (db != 0f) drawText(gainLabels[i], topLeft = Offset(labelPad, y - gainLabels[i].size.height - labelPad / 2))
                    }
                    drawPath(fill, accent.copy(alpha = 0.12f))
                    bandPaths.forEachIndexed { i, path ->
                        val tint = if (i == active) colors.teal else accent
                        drawPath(path, tint.copy(alpha = if (i == active) 0.8f else 0.35f), style = Stroke(width = px))
                    }
                    drawPath(combined, accent, style = Stroke(width = 2 * px))
                    curve.bands.forEachIndexed { i, band ->
                        val centre = Offset(EqGraphMath.frequencyToX(band.frequencyHz, width), EqGraphMath.gainToY(band.gainDb, plotHeight))
                        val isActive = i == active
                        val radius = if (isActive) nodeRadius * 1.3f else nodeRadius
                        drawCircle(colors.bgDeep, radius + 2 * px, centre)
                        drawCircle(if (isActive) colors.teal else accent, radius, centre)
                    }
                }
            },
    )
}

/**
 * The graph's pointer handling. [bands] reads the latest bands: a drag runs from the band as it was
 * when it was taken, so the node stays under the pointer and nothing accumulates.
 */
private suspend fun PointerInputScope.eqGestures(
    bands: () -> List<EqBand>,
    onActive: (Int?) -> Unit,
    onChanged: (index: Int, band: EqBand) -> Unit,
    onReset: (index: Int) -> Unit,
) {
    val hitRadius = EqGraphMath.NODE_HIT_RADIUS.dp.toPx()
    val qPixels = EqGraphMath.Q_DRAG_PIXELS_PER_DOUBLING.dp.toPx()
    val doubleClickMillis = viewConfiguration.doubleTapTimeoutMillis
    var drag: BandDrag? = null
    var lastPress: Pair<Int, Long>? = null
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            val all = bands()
            val position = change.position
            val hit = EqGraphMath.hitBand(all, position.x, position.y, width, height, hitRadius)
            val adjustsQ = event.keyboardModifiers.isShiftPressed || event.keyboardModifiers.isAltPressed
            when (event.type) {
                PointerEventType.Press -> {
                    if (hit == null) continue
                    change.consume()
                    val previous = lastPress
                    if (previous != null && previous.first == hit && change.uptimeMillis - previous.second <= doubleClickMillis) {
                        lastPress = null
                        drag = null
                        onReset(hit)
                        continue
                    }
                    lastPress = hit to change.uptimeMillis
                    drag = BandDrag(hit, all[hit], position, adjustsQ)
                    onActive(hit)
                }
                // A press can come with no hover first (touch, a test): its first move is an Enter.
                PointerEventType.Move, PointerEventType.Enter -> {
                    val started = drag
                    if (started == null || !change.pressed || started.index !in all.indices) {
                        if (!change.pressed) onActive(hit)
                        continue
                    }
                    change.consume()
                    // Shift or Alt pressed or released mid-drag: carry on from here in the other mode.
                    val anchor = if (started.adjustsQ == adjustsQ) started
                    else BandDrag(started.index, all[started.index], position, adjustsQ).also { drag = it }
                    val dx = position.x - anchor.origin.x
                    val dy = position.y - anchor.origin.y
                    val band = if (anchor.adjustsQ) anchor.start.copy(q = EqGraphMath.qFromDrag(anchor.start.q, dy, qPixels))
                    else EqGraphMath.moveBand(anchor.start, dx, dy, width, height)
                    if (band != all[anchor.index]) onChanged(anchor.index, band)
                }
                PointerEventType.Release -> {
                    if (drag != null) change.consume()
                    drag = null
                    onActive(hit)
                }
                PointerEventType.Scroll -> {
                    // Only over a node: elsewhere the wheel scrolls the sheet as usual.
                    val delta = change.scrollDelta.y
                    if (hit == null || delta == 0f) continue
                    change.consume()
                    val band = all[hit]
                    onChanged(hit, band.copy(q = EqGraphMath.qFromScroll(band.q, delta)))
                    onActive(hit)
                }
                PointerEventType.Exit -> if (drag == null) onActive(null)
            }
        }
    }
}

private class BandDrag(val index: Int, val start: EqBand, val origin: Offset, val adjustsQ: Boolean)

@Composable
private fun formatFrequency(hz: Float, separator: String): String =
    if (hz >= 1_000f) stringResource(Res.string.khz_format, formatDecimal(hz / 1_000f, separator))
    else stringResource(Res.string.hz_format, hz.roundToInt())

/** "50", "500", "1k", "20k": the axis has no room for units. */
private fun gridLabel(hz: Float): String =
    if (hz >= 1_000f) "${(hz / 1_000f).roundToInt()}k" else hz.roundToInt().toString()

private const val SAMPLES = 160
private val GRAPH_HEIGHT = 200.dp
private val NODE_RADIUS = 6.dp
private val SHAPE = DownstepShapes.control
