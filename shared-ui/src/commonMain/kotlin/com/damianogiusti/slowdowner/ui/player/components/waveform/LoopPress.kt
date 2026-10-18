package com.damianogiusti.slowdowner.ui.player.components.waveform

import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import kotlin.math.abs

// What a press on a waveform means, as pure maths: which loop and which part of it is under the
// pointer, and whether the press is a click or a drag. The pointer code only feeds it positions.

/** A loop as the hit test sees it: its bounds as drawn (a drag preview included). */
internal class HitRegion(val loopId: Long, val bounds: LoopBounds, val isActive: Boolean)

/** A press landed on [handle] of loop [loopId]. */
internal data class LoopHit(val loopId: Long, val handle: LoopHandle)

/**
 * The loop part under ([x], [y]), or null for the waveform itself (a click there seeks).
 *
 * - An edge within [edgeReach] pixels, at any height, resizes the loop (A or B).
 * - Above [barHeight] (the top bar's grab zone) and between the edges, the bar moves the whole loop.
 *   Where an edge and the bar meet, the edge wins for a drag; on a loop too narrow for both, the middle
 *   third is the bar and the outer thirds are the edges. A click there is still a bar click (see
 *   [isOnBar] and [LoopPress.release]).
 * - Loops may overlap. For the edges the active loop is tried first, then the others, shortest first.
 *   For the bar the shortest loop under the pointer wins, active or not, so a loop inside another (the
 *   active one included) stays reachable; [topBarOrder] draws the bars in the same order.
 */
internal fun hitLoop(
    x: Float,
    y: Float,
    regions: List<HitRegion>,
    axis: WaveformAxis,
    edgeReach: Float,
    barHeight: Float,
): LoopHit? {
    val ordered = regions.sortedWith(compareByDescending<HitRegion> { it.isActive }.thenBy { it.bounds.length })
    for (region in ordered) {
        val startX = axis.x(region.bounds.start)
        val endX = axis.x(region.bounds.end)
        val onBar = y < barHeight && x >= startX && x <= endX
        if (onBar && endX - startX < edgeReach * NARROW_LOOP_REACHES) {
            val third = (endX - startX) / 3
            if (x >= startX + third && x <= endX - third) return LoopHit(region.loopId, LoopHandle.Bar)
        }
        hitHandle(x, startX, endX, edgeReach)?.let { return LoopHit(region.loopId, it) }
    }
    if (y >= barHeight) return null
    return regions.sortedWith(compareBy<HitRegion> { it.bounds.length }.thenByDescending { it.isActive })
        .firstOrNull { x >= axis.x(it.bounds.start) && x <= axis.x(it.bounds.end) }
        ?.let { LoopHit(it.loopId, LoopHandle.Bar) }
}

/**
 * The order to draw the loops' top bars in so that the bar on top is the one [hitLoop] grabs: longest
 * first, the shortest last. Between two loops of the same length the active one goes on top.
 */
internal fun <T> topBarOrder(items: List<T>, length: (T) -> Float, isActive: (T) -> Boolean): List<T> =
    items.sortedWith(compareByDescending<T> { length(it) }.thenBy { isActive(it) })

/**
 * Whether ([x], [y]) is on [region]'s top bar as drawn: above [barHeight] and between its A and B. A
 * press there that grabbed an edge resizes when dragged but is a bar click when not.
 */
internal fun isOnBar(x: Float, y: Float, region: HitRegion, axis: WaveformAxis, barHeight: Float): Boolean =
    y < barHeight && x >= axis.x(region.bounds.start) && x <= axis.x(region.bounds.end)

/** Narrower than this many edge reaches, a loop's bar and edges share its width by thirds. */
private const val NARROW_LOOP_REACHES = 3f

/** What a press turned out to be, once released. */
internal sealed interface PressResult {
    /** A click on the waveform, or on a loop's edge: seek there (0..1 of the file). */
    data class Seek(val position: Float) : PressResult

    /** A click on a loop's top bar: "play this loop" (the ViewModel decides, see onLoopBarClicked). */
    data class BarClick(val loopId: Long, val position: Float) : PressResult

    /** A drag of a loop's bar or edge ended with new bounds. */
    data class Commit(val loopId: Long, val bounds: LoopBounds) : PressResult

    /** A drag that left the loop where it was. */
    data object None : PressResult
}

/**
 * One press, from down to up. It is a click until the pointer travels more than [threshold] pixels
 * sideways; from then on it is a drag of what it [hit] (or, on the bare waveform, a click where it is
 * released, as before). A drag never turns back into a click.
 *
 * [region] is the loop that was hit, as it was when pressed; the drag starts from its bounds. [onBar]
 * says the press landed on its top bar as drawn (see [isOnBar]), even where an edge was grabbed: a
 * click there plays the loop like any other click on the bar.
 */
internal class LoopPress(
    val hit: LoopHit?,
    private val region: HitRegion?,
    private val downX: Float,
    private val threshold: Float,
    private val axis: WaveformAxis,
    private val durationSeconds: Float,
    private val onBar: Boolean = false,
) {
    init {
        require((hit == null) == (region == null) && (region == null || region.loopId == hit?.loopId))
    }

    var isDrag: Boolean = false
        private set

    /** The hit loop's bounds at the press, where a drag starts from. */
    val from: LoopBounds? get() = region?.bounds

    /** The hit loop as dragged so far; its bounds at the press until the drag moves it. */
    var bounds: LoopBounds? = region?.bounds
        private set

    /** The pointer moved to [x]. True on the move that turns the press into a drag. */
    fun moveTo(x: Float): Boolean {
        val starts = !isDrag && exceedsDragThreshold(downX, x, threshold)
        if (starts) isDrag = true
        if (isDrag && hit != null && region != null) {
            bounds = dragLoop(hit.handle, region.bounds, axis.fraction(x), durationSeconds, grabbedAt = axis.fraction(downX))
        }
        return starts
    }

    /** The loop that a drag just picked up when it is not the active one (it becomes active). */
    val grabbedInactiveLoop: Long?
        get() = region?.takeIf { isDrag && !it.isActive }?.loopId

    fun release(x: Float): PressResult {
        if (!isDrag) moveTo(x)
        val position = axis.fraction(x)
        return when {
            hit == null || region == null -> PressResult.Seek(position)
            isDrag -> bounds?.takeIf { it != region.bounds }?.let { PressResult.Commit(hit.loopId, it) } ?: PressResult.None
            hit.handle == LoopHandle.Bar || onBar -> PressResult.BarClick(hit.loopId, position)
            // A click on the active loop's own A or B stays in it (a click just outside B would turn
            // the loop off); on another loop's edge it is a click like any other.
            region.isActive -> PressResult.Seek(position.coerceIn(region.bounds.start, region.bounds.end))
            else -> PressResult.Seek(position)
        }
    }
}

/** Click or drag: a drag once the pointer is more than [threshold] pixels sideways from the press. */
internal fun exceedsDragThreshold(downX: Float, x: Float, threshold: Float): Boolean = abs(x - downX) > threshold
