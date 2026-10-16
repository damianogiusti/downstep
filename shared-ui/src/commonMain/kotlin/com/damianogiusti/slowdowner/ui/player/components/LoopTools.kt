package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.ui.common.DISABLED_ALPHA
import com.damianogiusti.slowdowner.ui.common.Pill
import com.damianogiusti.slowdowner.ui.common.SegmentedPill
import com.damianogiusti.slowdowner.ui.common.Tooltip
import com.damianogiusti.slowdowner.ui.player.LoopToolActions
import com.damianogiusti.slowdowner.ui.player.LoopToolsState
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The active-loop tool group (◀ nudge · ½ · ×2 · nudge ▶ · ✕ delete, 30dp buttons in a segmented
 * pill; only with an active loop) and the Snap to beat toggle (disabled without a BPM, tooltip why).
 *
 * Emits the two as siblings, each with [modifier], so the transport's flow row can wrap Snap to its
 * own line when the window is narrow (as in the design at 1280dp).
 */
@Composable
fun LoopToolGroup(
    state: LoopToolsState,
    actions: LoopToolActions,
    modifier: Modifier = Modifier,
) {
    val colors = SlowDownerTheme.colors
    if (state.hasActiveLoop) {
        val nudgeBack = remember(actions) { { actions.onNudge(-1) } }
        val nudgeForward = remember(actions) { { actions.onNudge(1) } }
        SegmentedPill(padding = 3.dp, modifier = modifier) {
            ToolIcon(
                Res.drawable.ic_h_chevron_left,
                stringResource(Res.string.loop_shift_backward_description),
                state.enabled && state.canNudgeBack,
                nudgeBack,
            )
            ToolText(stringResource(Res.string.loop_halve), stringResource(Res.string.loop_halve_description), state.enabled && state.canHalve, actions.onHalve)
            ToolText(stringResource(Res.string.loop_double), stringResource(Res.string.loop_double_description), state.enabled && state.canDouble, actions.onDouble)
            ToolIcon(
                Res.drawable.ic_h_chevron_right,
                stringResource(Res.string.loop_shift_forward_description),
                state.enabled && state.canNudgeForward,
                nudgeForward,
            )
            ToolIcon(
                Res.drawable.ic_h_close,
                stringResource(Res.string.loop_delete),
                state.enabled,
                actions.onDelete,
                tint = colors.fg2,
                hoverContainer = colors.errBg,
                hoverTint = colors.errFg,
            )
        }
    }
    val snapping = state.snapToBeat && state.canSnap
    Pill(
        onClick = actions.onSnapToggled,
        modifier = modifier,
        enabled = state.enabled && state.canSnap,
        height = 36.dp,
        container = if (snapping) colors.surface3 else null,
        contentColor = if (snapping) colors.fg else colors.fg2,
        border = if (snapping) colors.surface4 else colors.surface3,
        contentPadding = PaddingValues(horizontal = 12.dp),
        tooltip = stringResource(if (state.canSnap) Res.string.snap_tooltip else Res.string.snap_needs_bpm),
    ) {
        Icon(painterResource(Res.drawable.ic_h_snap), contentDescription = null, modifier = Modifier.size(14.dp))
        Text(stringResource(Res.string.snap_to_beat), style = SlowDownerTheme.type.control.copy(fontSize = 12.sp), maxLines = 1)
    }
}

@Composable
private fun ToolIcon(
    icon: DrawableResource,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = SlowDownerTheme.colors.fg,
    hoverContainer: Color = SlowDownerTheme.colors.surface3,
    hoverTint: Color = tint,
) {
    ToolButton(description, enabled, onClick, hoverContainer, Modifier.size(DownstepDimens.loopTool)) { hovered ->
        Icon(painterResource(icon), contentDescription = null, tint = if (hovered) hoverTint else tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ToolText(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    ToolButton(
        description,
        enabled,
        onClick,
        SlowDownerTheme.colors.surface3,
        Modifier.height(DownstepDimens.loopTool).widthIn(min = DownstepDimens.loopTool).padding(horizontal = 10.dp),
    ) {
        Text(text, style = SlowDownerTheme.type.controlStrong.copy(fontSize = 12.sp), color = SlowDownerTheme.colors.fg, maxLines = 1)
    }
}

/** A borderless round button of the group: transparent, [hoverContainer] under the pointer. */
@Composable
private fun ToolButton(
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    hoverContainer: Color,
    sizing: Modifier,
    content: @Composable (hovered: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val showHover = hovered && enabled
    Tooltip(description) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(DownstepShapes.pill)
                .background(if (showHover) hoverContainer else Color.Transparent, DownstepShapes.pill)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .then(sizing),
        ) {
            content(showHover)
        }
    }
}
