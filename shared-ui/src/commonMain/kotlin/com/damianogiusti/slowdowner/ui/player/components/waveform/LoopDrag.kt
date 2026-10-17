package com.damianogiusti.slowdowner.ui.player.components.waveform

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * The loop being dragged on the waveforms, shared by the overview and the zoom strip so both draw the
 * same live bounds: moves are drawn from [bounds] (read in draw code only, so a drag redraws and never
 * recomposes), and the ViewModel hears once, on release.
 */
@Stable
class LoopDragState internal constructor() {
    /** The loop shown dragged, until the state catches up with the commit; null otherwise. */
    internal var loopId: Long? by mutableStateOf(null)
        private set

    /** Its bounds as dragged. */
    internal var bounds: LoopBounds? by mutableStateOf(null)
        private set

    internal var isDragging: Boolean by mutableStateOf(false)
        private set

    /** Its bounds when the drag began: once the state shows anything else, the commit has landed. */
    private var from: LoopBounds? = null

    /** What to draw for loop [id]: the dragged bounds while it is the one dragged, else [committed]. */
    internal fun shown(id: Long, committed: LoopBounds): LoopBounds = bounds?.takeIf { loopId == id } ?: committed

    internal fun begin(id: Long, from: LoopBounds) {
        isDragging = true
        this.from = from
        loopId = id
        bounds = from
    }

    internal fun move(to: LoopBounds) {
        bounds = to
    }

    /** Released: the preview stays until [settle], unless nothing moved. */
    internal fun end(moved: Boolean) {
        isDragging = false
        if (!moved) clear()
    }

    /**
     * The state now shows loop [id] at [committed] (null: the loop is gone). When that is the dragged
     * loop and it differs from where the drag began, the commit came back: the preview goes. Other
     * changes (the dragged loop becoming active, another loop) keep it.
     */
    internal fun settle(id: Long, committed: LoopBounds?) {
        if (isDragging || loopId != id) return
        if (committed == null || committed != from) clear()
    }

    /** The commit never came back (refused, or the loop went away): show the loop where it is. */
    internal fun abandon() {
        if (!isDragging) clear()
    }

    private fun clear() {
        loopId = null
        bounds = null
        from = null
    }
}

/**
 * A dropped preview whose commit does not come back within [DROPPED_PREVIEW_TIMEOUT_MS] (the
 * ViewModel refused it: controls frozen by an export or a decode, the loop deleted meanwhile) goes,
 * so the waveform never shows a loop where it is not. Recomposes only when a drag begins and ends.
 */
@Composable
internal fun ExpireDroppedPreview(drag: LoopDragState) {
    val dropped = drag.loopId?.takeIf { !drag.isDragging }
    LaunchedEffect(drag, dropped) {
        if (dropped == null) return@LaunchedEffect
        delay(DROPPED_PREVIEW_TIMEOUT_MS)
        drag.abandon()
    }
}

/** Longer than the ViewModel waits for a loop edit to show (2 s): a slow commit still lands first. */
internal const val DROPPED_PREVIEW_TIMEOUT_MS = 3_000L

/** A press that travels further than this sideways is a drag; less is a click. */
internal val DRAG_THRESHOLD = 4.dp

/** Callbacks of [loopGestures]: what a press on a waveform can end in. */
internal class LoopGestureActions(
    val onSeek: (position: Float) -> Unit,
    val onBarClick: (loopId: Long, position: Float) -> Unit,
    /** A drag picked up a loop that is not the active one: it becomes active at once. */
    val onGrab: (loopId: Long) -> Unit,
    val onCommit: (loopId: Long, bounds: LoopBounds) -> Unit,
)

/**
 * Click to seek, drag a loop's edge (A or B, within [reach] pixels of it) or, above [barHeight], its
 * top bar to move it; [threshold] pixels tell a click from a drag (see [LoopPress]). [axis] and
 * [regions] are read when a gesture starts, so this pointer input never restarts while playing or
 * after a commit.
 */
internal suspend fun PointerInputScope.loopGestures(
    drag: LoopDragState,
    reach: Float,
    barHeight: Float,
    threshold: Float,
    durationSeconds: () -> Float,
    axis: (IntSize) -> WaveformAxis?,
    regions: () -> List<HitRegion>,
    actions: LoopGestureActions,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        // Secondary clicks are not seeks.
        if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        val currentAxis = axis(size) ?: return@awaitEachGesture
        // Hit-tested as drawn: a loop whose last drag has not come back yet is where it was dropped.
        val shown = regions().map { HitRegion(it.loopId, drag.shown(it.loopId, it.bounds), it.isActive) }
        val hit = hitLoop(down.position.x, down.position.y, shown, currentAxis, reach, barHeight)
        val region = hit?.let { h -> shown.first { it.loopId == h.loopId } }
        // A finger wobbles more than a mouse: touch keeps the platform's own slop.
        val slop = if (down.type == PointerType.Touch) max(viewConfiguration.touchSlop, threshold) else threshold
        val onBar = region != null && isOnBar(down.position.x, down.position.y, region, currentAxis, barHeight)
        val press = LoopPress(hit, region, down.position.x, slop, currentAxis, durationSeconds(), onBar)
        if (hit != null) down.consume()

        var result: PressResult? = null
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (hit != null) change.consume()
                if (!change.pressed || change.positionChanged()) {
                    if (press.moveTo(change.position.x)) onDragStarted(press, drag, actions)
                    if (press.isDrag && hit != null) press.bounds?.let(drag::move)
                }
                if (!change.pressed) {
                    result = press.release(change.position.x)
                    break
                }
            }
        } finally {
            // A cancelled drag (the view went away) commits nothing and keeps no preview.
            if (press.isDrag && region != null) drag.end(moved = result is PressResult.Commit)
        }
        when (val ended = result) {
            is PressResult.Seek -> actions.onSeek(ended.position)
            is PressResult.BarClick -> actions.onBarClick(ended.loopId, ended.position)
            is PressResult.Commit -> actions.onCommit(ended.loopId, ended.bounds)
            PressResult.None, null -> Unit
        }
    }
}

private fun onDragStarted(press: LoopPress, drag: LoopDragState, actions: LoopGestureActions) {
    val hit = press.hit ?: return
    press.from?.let { drag.begin(hit.loopId, it) }
    press.grabbedInactiveLoop?.let(actions.onGrab)
}
