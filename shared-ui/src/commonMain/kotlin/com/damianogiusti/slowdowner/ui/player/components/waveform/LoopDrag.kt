package com.damianogiusti.slowdowner.ui.player.components.waveform

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import com.damianogiusti.slowdowner.domain.loops.BeatGrid
import com.damianogiusti.slowdowner.domain.loops.LoopBounds

/** What an A/B drag edits: the active loop as the state has it. */
internal class DragTarget(val loopId: Long, val bounds: LoopBounds, val durationSeconds: Float, val grid: BeatGrid?)

/**
 * The A/B drag of one waveform, local to it: moves are drawn from [bounds] (read in draw code only, so
 * a drag redraws and never recomposes), and the ViewModel hears once, on release.
 */
@Stable
internal class LoopDragState {
    /** The loop as dragged, until the state catches up with the commit; null otherwise. */
    var bounds: LoopBounds? by mutableStateOf(null)
        private set
    var isDragging: Boolean = false
        private set

    fun shown(committed: LoopBounds): LoopBounds = bounds ?: committed

    fun begin(from: LoopBounds) {
        isDragging = true
        bounds = from
    }

    fun move(to: LoopBounds) {
        bounds = to
    }

    /** Released: the preview stays until [settle], unless nothing moved. */
    fun end(moved: Boolean) {
        isDragging = false
        if (!moved) bounds = null
    }

    /** The state has new bounds (the commit came back, or another loop is active): drop the preview. */
    fun settle() {
        if (!isDragging) bounds = null
    }
}

/**
 * Click to seek, drag A or B within [reach] pixels of its line. [axis] and [target] are read when a
 * gesture starts, so this pointer input never restarts while playing or after a commit.
 */
internal suspend fun PointerInputScope.loopGestures(
    drag: LoopDragState,
    reach: Float,
    axis: (IntSize) -> WaveformAxis?,
    target: () -> DragTarget?,
    onSeek: (Float) -> Unit,
    onCommit: (loopId: Long, bounds: LoopBounds) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        // Secondary clicks are not seeks.
        if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
        val currentAxis = axis(size) ?: return@awaitEachGesture
        val loop = target()
        val from = loop?.let { drag.shown(it.bounds) }
        val handle = from?.let { hitHandle(down.position.x, currentAxis.x(it.start), currentAxis.x(it.end), reach) }
        if (loop == null || from == null || handle == null) {
            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
            onSeek(currentAxis.fraction(up.position.x))
            return@awaitEachGesture
        }
        down.consume()
        drag.begin(from)
        var bounds: LoopBounds = from
        var released = false
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                if (change.positionChanged()) {
                    change.consume()
                    bounds = dragLoop(handle, from, currentAxis.fraction(change.position.x), loop.durationSeconds, loop.grid)
                    drag.move(bounds)
                }
            }
            released = true
        } finally {
            // A cancelled drag (the view went away) commits nothing and keeps no preview.
            drag.end(moved = released && bounds != loop.bounds)
        }
        if (bounds != loop.bounds) onCommit(loop.loopId, bounds)
    }
}
