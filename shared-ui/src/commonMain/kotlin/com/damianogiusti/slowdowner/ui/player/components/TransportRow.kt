package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.domain.loops.ProgressionStatus
import com.damianogiusti.slowdowner.ui.common.IconPill
import com.damianogiusti.slowdowner.ui.common.KeyCap
import com.damianogiusti.slowdowner.ui.common.Pill
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.player.PassState
import com.damianogiusti.slowdowner.ui.player.TransportActions
import com.damianogiusti.slowdowner.ui.player.TransportState
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Three columns (1fr auto 1fr): left = loop toggle pill + [loopTools] (LoopToolGroup + Snap, wrapping
 * to a second line when narrow); centre = ⏮ · ↺ −5 s · Play/Pause (68dp amber, glow) · ↻ +5 s ·
 * ⏭ next loop; right = [trailing] (pass pill and keyboard hints, or the focus read-outs).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransportRow(
    state: TransportState,
    actions: TransportActions,
    loopTools: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = DownstepDimens.gutter, vertical = 14.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            LoopPill(state, actions.onLoopToggled)
            loopTools()
        }
        TransportCentre(state, actions)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            trailing()
        }
    }
}

/** Looping: the active loop's name on the dark amber of "loop on". Off: "Loop off", dimmer. */
@Composable
private fun LoopPill(state: TransportState, onToggle: () -> Unit) {
    val colors = SlowDownerTheme.colors
    val looping = state.isLoopOn && state.activeLoopName != null
    Pill(
        onClick = onToggle,
        enabled = state.enabled && state.activeLoopName != null,
        height = 38.dp,
        container = if (looping) colors.loopOnBg else colors.surface1,
        contentColor = if (looping) colors.loopOnFg else colors.fg2,
        border = null,
        contentPadding = PaddingValues(start = 12.dp, end = 14.dp),
        tooltip = stringResource(Res.string.loop_toggle_tooltip),
    ) {
        Icon(painterResource(Res.drawable.ic_h_loop), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            if (looping) state.activeLoopName.orEmpty() else stringResource(Res.string.loop_off),
            style = SlowDownerTheme.type.controlStrong,
            maxLines = 1,
        )
    }
}

@Composable
private fun TransportCentre(state: TransportState, actions: TransportActions) {
    val colors = SlowDownerTheme.colors
    // Remembered: the −5 / +5 buttons get the same lambda on every recomposition and can skip.
    val seekBack = remember(actions) { { actions.onSeekBy(-SEEK_SECONDS) } }
    val seekForward = remember(actions) { { actions.onSeekBy(SEEK_SECONDS) } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TransportButton(Res.drawable.ic_h_to_start, stringResource(Res.string.to_start_description), 20.dp, state.enabled, actions.onToStart)
        TransportButton(Res.drawable.ic_h_back5, stringResource(Res.string.nudge_back_description), 22.dp, state.enabled, seekBack)
        val glow = colors.amberGlow
        IconPill(
            painterResource(if (state.isPlaying) Res.drawable.ic_h_pause else Res.drawable.ic_h_play),
            stringResource(if (state.isPlaying) Res.string.pause else Res.string.play),
            actions.onPlayPause,
            enabled = state.enabled,
            size = DownstepDimens.playButton,
            iconSize = 28.dp,
            container = colors.amber,
            tint = colors.onAmber,
            border = null,
            // The prototype's box-shadow (0 10px 30px amberGlow), drawn as a soft disc below the button.
            modifier = Modifier.drawBehind {
                if (!state.enabled) return@drawBehind
                val radius = size.minDimension / 2 + GLOW_SPREAD.toPx()
                val center = Offset(size.width / 2, size.height / 2 + GLOW_OFFSET.toPx())
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to glow,
                        0.4f to glow,
                        0.7f to glow.copy(alpha = glow.alpha * 0.3f),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            },
        )
        TransportButton(Res.drawable.ic_h_forward5, stringResource(Res.string.nudge_forward_description), 22.dp, state.enabled, seekForward)
        TransportButton(
            Res.drawable.ic_h_next_loop,
            stringResource(Res.string.next_loop_description),
            20.dp,
            state.enabled && state.hasLoops,
            actions.onNextLoop,
        )
    }
}

@Composable
private fun TransportButton(icon: DrawableResource, description: String, iconSize: Dp, enabled: Boolean, onClick: () -> Unit) {
    IconPill(
        painterResource(icon),
        description,
        onClick,
        enabled = enabled,
        size = DownstepDimens.transportButton,
        iconSize = iconSize,
        border = null,
    )
}

/** "Pass 4", plus "· +3% in 2" while progression ramps, or "· at target". */
@Composable
fun PassPill(state: PassState, modifier: Modifier = Modifier) {
    val colors = SlowDownerTheme.colors
    val count = stringResource(Res.string.pass_count, state.passes)
    val hint = when (val progression = state.progression) {
        is ProgressionStatus.Ramping ->
            stringResource(Res.string.pass_ramp, formatSpeed(progression.nextStepPercent), progression.passesUntilNextStep)
        is ProgressionStatus.AtTarget -> stringResource(Res.string.pass_at_target)
        ProgressionStatus.Off -> null
    }
    val number = state.passes.toString()
    val text = buildAnnotatedString {
        // "Pass" in the secondary colour, the number strong: wherever the translation puts it.
        val at = count.lastIndexOf(number)
        if (at < 0) {
            append(count)
        } else {
            append(count.substring(0, at))
            withStyle(SpanStyle(color = colors.fg, fontWeight = FontWeight.SemiBold)) { append(number) }
            append(count.substring(at + number.length))
        }
        if (hint != null) append(" · $hint")
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(28.dp)
            .background(colors.surface1, DownstepShapes.pill)
            .border(1.dp, colors.surface3, DownstepShapes.pill)
            .padding(horizontal = 10.dp),
    ) {
        Text(text, style = SlowDownerTheme.type.meta, color = colors.fg2, maxLines = 1)
    }
}

/** `A` `B` set · `[` `]` loops · `F` focus. */
@Composable
fun KeyboardHints(modifier: Modifier = Modifier) {
    val style = SlowDownerTheme.type.meta.copy(fontSize = 11.sp)
    val color = SlowDownerTheme.colors.fg2
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        KeyCap("A")
        KeyCap("B")
        Text(stringResource(Res.string.hint_set) + " ·", style = style, color = color, maxLines = 1)
        KeyCap("[")
        KeyCap("]")
        Text(stringResource(Res.string.hint_loops) + " ·", style = style, color = color, maxLines = 1)
        KeyCap("F")
        Text(stringResource(Res.string.hint_focus), style = style, color = color, maxLines = 1)
    }
}

private const val SEEK_SECONDS = 5f
private val GLOW_SPREAD = 26.dp
private val GLOW_OFFSET = 10.dp
