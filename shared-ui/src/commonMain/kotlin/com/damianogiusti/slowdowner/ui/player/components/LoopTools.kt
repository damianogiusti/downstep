package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.common.Pill
import com.damianogiusti.slowdowner.ui.common.PillToggle
import com.damianogiusti.slowdowner.ui.common.SegmentedPill
import com.damianogiusti.slowdowner.ui.player.LoopToolActions
import com.damianogiusti.slowdowner.ui.player.LoopToolsState
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.stringResource

// PLACEHOLDER (group H): final signature, minimal body. See ui-contract.md, README §2.6.

/**
 * The active-loop tool group (◀ nudge · ½ · ×2 · nudge ▶ · ✕ delete, 30dp buttons in a segmented
 * pill; only with an active loop) and the Snap to beat toggle (disabled without a BPM, tooltip why).
 */
@Composable
fun LoopToolGroup(
    state: LoopToolsState,
    actions: LoopToolActions,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (state.hasActiveLoop) {
            SegmentedPill(padding = 3.dp) {
                ToolButton("‹", state.enabled && state.canNudgeBack) { actions.onNudge(-1) }
                ToolButton(stringResource(Res.string.loop_halve), state.enabled && state.canHalve, actions.onHalve)
                ToolButton(stringResource(Res.string.loop_double), state.enabled && state.canDouble, actions.onDouble)
                ToolButton("›", state.enabled && state.canNudgeForward) { actions.onNudge(1) }
                ToolButton("✕", state.enabled, actions.onDelete)
            }
        }
        PillToggle(
            checked = state.snapToBeat && state.canSnap,
            onClick = actions.onSnapToggled,
            text = stringResource(Res.string.snap_to_beat),
            enabled = state.enabled && state.canSnap,
            onColor = SlowDownerTheme.colors.surface3,
            onContentColor = SlowDownerTheme.colors.fg,
            offContentColor = SlowDownerTheme.colors.fg2,
            tooltip = stringResource(if (state.canSnap) Res.string.snap_tooltip else Res.string.snap_needs_bpm),
        )
    }
}

@Composable
private fun ToolButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Pill(onClick = onClick, enabled = enabled, height = DownstepDimens.loopTool, border = null) { Text(text) }
}
