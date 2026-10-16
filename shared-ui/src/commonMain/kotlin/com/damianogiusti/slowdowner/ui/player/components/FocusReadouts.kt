package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.common.KeyCap
import com.damianogiusti.slowdowner.ui.common.Pill
import com.damianogiusti.slowdowner.ui.common.formatPitch
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.stringResource

// PLACEHOLDER (group D): final signatures, minimal bodies. See ui-contract.md, README §4.9, screenshot 03.

/** Focus mode's compact header: "Title — Loop" (Space Grotesk 20) and an "Exit focus  F" pill. */
@Composable
fun FocusHeader(
    title: String?,
    loopName: String?,
    onExitFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = DownstepDimens.gutter, end = DownstepDimens.gutter, top = 14.dp, bottom = 8.dp),
    ) {
        Text(
            stringResource(Res.string.focus_title, title.orEmpty(), loopName ?: stringResource(Res.string.focus_no_loop)),
            style = SlowDownerTheme.type.focusTitle,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Pill(onClick = onExitFocus, height = 32.dp) {
            Text(stringResource(Res.string.exit_focus))
            KeyCap("F")
        }
    }
}

/** Big speed (amber) and pitch (teal) read-outs beside the transport in focus mode: Space Grotesk 18. */
@Composable
fun FocusReadouts(
    speedPercent: Float,
    pitchSemitones: Float,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier) {
        Text(formatSpeed(speedPercent), style = SlowDownerTheme.type.focusReadout, color = SlowDownerTheme.colors.amber)
        Text(formatPitch(pitchSemitones), style = SlowDownerTheme.type.focusReadout, color = SlowDownerTheme.colors.teal)
    }
}
