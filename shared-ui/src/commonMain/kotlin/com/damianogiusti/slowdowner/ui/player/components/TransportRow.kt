package com.damianogiusti.slowdowner.ui.player.components

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

/** Callbacks of the [TransportRow]. */
class TransportActions(
    val onPlayStop: () -> Unit,
    val onRewind: () -> Unit,
    val onNudge: (seconds: Float) -> Unit,
    val onLoopToggled: (Boolean) -> Unit,
)

@Composable
fun TransportRow(
    isPlaying: Boolean,
    isLoopOn: Boolean,
    enabled: Boolean,
    actions: TransportActions,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = actions.onRewind, enabled = enabled) {
            Icon(painterResource(Res.drawable.ic_skip_previous), stringResource(Res.string.rewind_description))
        }
        NudgeButton(Res.drawable.ic_replay, Res.string.nudge_back_description, enabled) { actions.onNudge(-NUDGE_SECONDS) }
        FilledIconButton(
            onClick = actions.onPlayStop,
            enabled = enabled,
            shape = CircleShape,
            modifier = Modifier
                .size(80.dp)
                .shadow(if (enabled) 16.dp else 0.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(
                painterResource(if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                contentDescription = stringResource(if (isPlaying) Res.string.stop else Res.string.play),
                modifier = Modifier.size(38.dp),
            )
        }
        NudgeButton(Res.drawable.ic_forward, Res.string.nudge_forward_description, enabled) { actions.onNudge(NUDGE_SECONDS) }
        FilledTonalIconToggleButton(
            checked = isLoopOn,
            onCheckedChange = actions.onLoopToggled,
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = SlowDownerTheme.colors.primaryTint,
                checkedContentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(painterResource(Res.drawable.ic_repeat), stringResource(Res.string.loop_toggle_description))
        }
    }
}

@Composable
private fun NudgeButton(icon: DrawableResource, description: StringResource, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), stringResource(description), modifier = Modifier.size(32.dp))
            Text(
                "5",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

private const val NUDGE_SECONDS = 5f
