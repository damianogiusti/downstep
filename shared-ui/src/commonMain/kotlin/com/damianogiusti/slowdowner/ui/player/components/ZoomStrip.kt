package com.damianogiusti.slowdowner.ui.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.damianogiusti.slowdowner.ui.player.WaveformActions
import com.damianogiusti.slowdowner.ui.player.ZoomStripState
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

// PLACEHOLDER (group W): final signature, minimal body. See ui-contract.md, README §2.5.

/**
 * The active loop ± padding ([ZoomStripState.window]) at sub-beat precision: beat lines, A/B lines
 * draggable with the same snapping, header texts. [position] is read only inside draw code.
 */
@Composable
fun ZoomStrip(
    state: ZoomStripState,
    position: () -> Float,
    actions: WaveformActions,
    modifier: Modifier = Modifier,
) {
    val colors = SlowDownerTheme.colors
    Canvas(
        modifier = modifier
            .padding(horizontal = DownstepDimens.gutter)
            .fillMaxWidth()
            .height(DownstepDimens.zoomStrip)
            .background(colors.bgDeep),
    ) {
        val window = state.window
        fun x(fraction: Float) = (fraction - window.start) / (window.end - window.start) * size.width
        drawLine(colors.amber, Offset(x(state.start), 0f), Offset(x(state.start), size.height), strokeWidth = 2f)
        drawLine(colors.teal, Offset(x(state.end), 0f), Offset(x(state.end), size.height), strokeWidth = 2f)
        val playhead = position()
        if (playhead in window.start..window.end) drawLine(colors.fg, Offset(x(playhead), 0f), Offset(x(playhead), size.height), strokeWidth = 2f)
    }
}
