package com.damianogiusti.slowdowner.ui.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.player.OverviewWaveformState
import com.damianogiusti.slowdowner.ui.player.WaveformActions
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

// PLACEHOLDER (group W): final signature, minimal body. See ui-contract.md, README §2.4.

/**
 * The full-width overview: bars from [OverviewWaveformState.peaks], loop regions, bar lines, A/B pills
 * and drag handles, the zoom bracket, suggestions, and the playhead with its timecode pill.
 *
 * [position] is read only inside draw code: playback never recomposes this composable.
 */
@Composable
fun OverviewWaveform(
    state: OverviewWaveformState,
    position: () -> Float,
    actions: WaveformActions,
    modifier: Modifier = Modifier,
    height: Dp = DownstepDimens.waveform,
) {
    val colors = SlowDownerTheme.colors
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.bgDeep)
            .pointerInput(state.enabled) {
                if (state.enabled) detectTapGestures { actions.onSeek(it.x / size.width) }
            },
    ) {
        val peaks = state.peaks?.values
        if (peaks != null) {
            val bars = 200
            val barWidth = size.width / bars
            for (i in 0 until bars) {
                val level = peaks[i * peaks.size / bars]
                val h = level * size.height * 0.7f
                drawRect(colors.barMuted, Offset(i * barWidth + 1f, (size.height - h) / 2), androidx.compose.ui.geometry.Size(barWidth - 2f, h))
            }
        }
        state.active?.let { active ->
            drawRect(colors.loopColor(active.colorIndex).copy(alpha = 0.16f), Offset(active.start * size.width, 0f), androidx.compose.ui.geometry.Size((active.end - active.start) * size.width, size.height))
        }
        val x = position() * size.width
        drawLine(colors.fg, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}
