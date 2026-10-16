package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.loops.ProgressionStatus
import com.damianogiusti.slowdowner.ui.common.IconPill
import com.damianogiusti.slowdowner.ui.common.KeyCap
import com.damianogiusti.slowdowner.ui.common.Pill
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.player.PassState
import com.damianogiusti.slowdowner.ui.player.TransportActions
import com.damianogiusti.slowdowner.ui.player.TransportState
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// PLACEHOLDER (group H): final signatures, minimal bodies. See ui-contract.md, README §2.6.

/**
 * Three columns (1fr auto 1fr): left = loop toggle pill + [loopTools] (LoopToolGroup + Snap);
 * centre = ⏮ · ↺ −5 s · Play/Pause (68dp amber, glow) · ↻ +5 s · ⏭ next loop; right = [trailing]
 * (pass pill and keyboard hints, or the focus read-outs).
 */
@Composable
fun TransportRow(
    state: TransportState,
    actions: TransportActions,
    loopTools: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SlowDownerTheme.colors
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = DownstepDimens.gutter, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterStart)) {
            Pill(
                onClick = actions.onLoopToggled,
                enabled = state.enabled && state.activeLoopName != null,
                height = 38.dp,
                container = if (state.isLoopOn) colors.loopOnBg else colors.surface1,
                contentColor = if (state.isLoopOn) colors.loopOnFg else colors.fg2,
                tooltip = stringResource(Res.string.loop_toggle_tooltip),
            ) {
                Text(if (state.isLoopOn) state.activeLoopName.orEmpty() else stringResource(Res.string.loop_off))
            }
            loopTools()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.Center)) {
            IconPill(painterResource(Res.drawable.ic_skip_previous), stringResource(Res.string.to_start_description), actions.onToStart, enabled = state.enabled, size = DownstepDimens.transportButton, border = null)
            IconPill(painterResource(Res.drawable.ic_replay), stringResource(Res.string.nudge_back_description), { actions.onSeekBy(-5f) }, enabled = state.enabled, size = DownstepDimens.transportButton, border = null)
            IconPill(
                painterResource(if (state.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                stringResource(if (state.isPlaying) Res.string.pause else Res.string.play),
                actions.onPlayPause,
                enabled = state.enabled,
                size = DownstepDimens.playButton,
                iconSize = 28.dp,
                container = colors.amber,
                tint = colors.onAmber,
                border = null,
            )
            IconPill(painterResource(Res.drawable.ic_forward), stringResource(Res.string.nudge_forward_description), { actions.onSeekBy(5f) }, enabled = state.enabled, size = DownstepDimens.transportButton, border = null)
            IconPill(painterResource(Res.drawable.ic_chevron_right), stringResource(Res.string.next_loop_description), actions.onNextLoop, enabled = state.enabled && state.hasLoops, size = DownstepDimens.transportButton, border = null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterEnd)) {
            trailing()
        }
    }
}

/** "Pass 4", plus "· +3% in 2" while progression ramps. */
@Composable
fun PassPill(state: PassState, modifier: Modifier = Modifier) {
    val ramp = (state.progression as? ProgressionStatus.Ramping)
    val text = buildString {
        append(stringResource(Res.string.pass_count, state.passes))
        if (ramp != null) append(" · ").append(stringResource(Res.string.pass_ramp, formatSpeed(ramp.nextStepPercent), ramp.passesUntilNextStep))
    }
    Text(text, style = SlowDownerTheme.type.meta, color = SlowDownerTheme.colors.fg2, modifier = modifier)
}

/** `A` `B` set · `[` `]` loops · `F` focus. */
@Composable
fun KeyboardHints(modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        KeyCap("A")
        KeyCap("B")
        Text(stringResource(Res.string.hint_set), style = SlowDownerTheme.type.meta, color = SlowDownerTheme.colors.fg2)
        KeyCap("[")
        KeyCap("]")
        Text(stringResource(Res.string.hint_loops), style = SlowDownerTheme.type.meta, color = SlowDownerTheme.colors.fg2)
        KeyCap("F")
        Text(stringResource(Res.string.hint_focus), style = SlowDownerTheme.type.meta, color = SlowDownerTheme.colors.fg2)
    }
}
