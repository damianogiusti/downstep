package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.common.formatClock
import com.damianogiusti.slowdowner.ui.common.formatPreciseClock
import com.damianogiusti.slowdowner.ui.player.PlayerControlsState
import com.damianogiusti.slowdowner.ui.player.LoopZoomUiState
import com.damianogiusti.slowdowner.ui.player.WaveformUiState
import com.damianogiusti.slowdowner.ui.player.components.timeline.ShownTimeline
import com.damianogiusti.slowdowner.ui.player.components.timeline.TimelineCanvas
import com.damianogiusti.slowdowner.ui.player.components.timeline.TimelineCanvasActions
import com.damianogiusti.slowdowner.ui.player.components.timeline.TimelineCanvasState
import com.damianogiusti.slowdowner.ui.player.components.timeline.TimelineDrag
import com.damianogiusti.slowdowner.ui.player.components.timeline.TimelineWindow
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.tabular
import org.jetbrains.compose.resources.stringResource

/** What the timeline draws, and whether it takes input (not while an export renders). */
class LoopTimelineState(
    val controls: PlayerControlsState,
    /** Drawn as bars under the loop; null for the plain track. */
    val waveform: WaveformUiState?,
    val isEnabled: Boolean,
)

/** Seeking and moving the loop. Changes come only when the finger lifts: every intermediate seek would stutter. */
class TimelineActions(val onSeek: (Float) -> Unit, val onLoopRegionChanged: (start: Float, end: Float) -> Unit)

/** Seek bar and A/B loop editor in one, over the whole file. Drag A or B to move the loop, anywhere else to seek. */
@Composable
fun LoopTimeline(state: LoopTimelineState, actions: TimelineActions, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<TimelineDrag?>(null) }
    val canvasActions = rememberCanvasActions(onDrag = { drag = it }, actions)
    val controls = state.controls
    val levels = state.waveform?.levels
    val shown = ShownTimeline.of(controls, drag)
    Column(modifier = modifier) {
        TimelineCanvas(
            state = TimelineCanvasState(controls, TimelineWindow.WHOLE, levels, drag, state.isEnabled),
            actions = canvasActions,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (levels == null) 72.dp else 88.dp)
                .seekSemantics(controls, shown.position, state.isEnabled, actions.onSeek),
        )
        TimeRow(controls, shown)
    }
}

/** The zoom of a [LoopCard]: its window, and whether it takes input. */
internal class LoopZoomState(val controls: PlayerControlsState, val zoom: LoopZoomUiState, val isEnabled: Boolean)

/** The loop and a little around it, to place A and B precisely, with their times to the hundredth. */
@Composable
internal fun LoopZoom(state: LoopZoomState, actions: TimelineActions, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<TimelineDrag?>(null) }
    val canvasActions = rememberCanvasActions(onDrag = { drag = it }, actions)
    val controls = state.controls
    val zoom = state.zoom
    val separator = stringResource(Res.string.decimal_separator)
    val duration = controls.durationSeconds
    val description = stringResource(
        Res.string.loop_zoom_description,
        formatPreciseClock(zoom.from * duration, separator),
        formatPreciseClock(zoom.to * duration, separator),
    )
    val shown = ShownTimeline.of(controls, drag)
    Column(modifier = modifier) {
        TimelineCanvas(
            state = TimelineCanvasState(controls, TimelineWindow(zoom.from, zoom.to), zoom.levels, drag, state.isEnabled),
            actions = canvasActions,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (zoom.levels == null) 64.dp else 80.dp)
                .semantics { contentDescription = description },
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val time = MaterialTheme.typography.labelMedium.tabular()
            val edge = MaterialTheme.colorScheme.onSurfaceVariant
            Text(formatPreciseClock(zoom.from * duration, separator), style = time, color = edge)
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("A ${formatPreciseClock(shown.loopStart * duration, separator)}", style = time, color = SlowDownerTheme.colors.markerA)
                Spacer(Modifier.width(16.dp))
                Text("B ${formatPreciseClock(shown.loopEnd * duration, separator)}", style = time, color = SlowDownerTheme.colors.markerB)
            }
            Text(formatPreciseClock(zoom.to * duration, separator), style = time, color = edge)
        }
    }
}

@Composable
private fun rememberCanvasActions(onDrag: (TimelineDrag?) -> Unit, actions: TimelineActions): TimelineCanvasActions {
    val current by rememberUpdatedState(actions)
    return remember { TimelineCanvasActions(onDrag, { current.onSeek(it) }, { start, end -> current.onLoopRegionChanged(start, end) }) }
}

@Composable
private fun Modifier.seekSemantics(controls: PlayerControlsState, position: Float, enabled: Boolean, onSeek: (Float) -> Unit): Modifier {
    val description = stringResource(
        Res.string.timeline_description,
        formatClock(position * controls.durationSeconds),
        formatClock(controls.durationSeconds),
    )
    val currentOnSeek by rememberUpdatedState(onSeek)
    return semantics {
        contentDescription = description
        if (enabled) setProgress { value -> currentOnSeek(value.coerceIn(0f, 1f)); true }
    }
}

/** Position, A and B, and the length of the file. */
@Composable
private fun TimeRow(controls: PlayerControlsState, shown: ShownTimeline) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val time = MaterialTheme.typography.labelMedium.tabular()
        Text(formatClock(shown.position * controls.durationSeconds), style = time)
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            if (controls.hasLoopPoints) {
                Text("A ${formatClock(shown.loopStart * controls.durationSeconds)}", style = time, color = SlowDownerTheme.colors.markerA)
                Spacer(Modifier.width(16.dp))
                Text("B ${formatClock(shown.loopEnd * controls.durationSeconds)}", style = time, color = SlowDownerTheme.colors.markerB)
            }
        }
        Text(formatClock(controls.durationSeconds), style = time, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
