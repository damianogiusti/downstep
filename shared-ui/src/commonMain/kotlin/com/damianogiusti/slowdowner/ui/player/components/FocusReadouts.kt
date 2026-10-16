package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.ui.common.Pill
import com.damianogiusti.slowdowner.ui.common.formatPitch
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.stringResource

/** Focus mode's compact header: "Title — Loop" (Space Grotesk 20, the loop part secondary) and an "Exit focus  F" pill. */
@Composable
fun FocusHeader(
    title: String?,
    loopName: String?,
    onExitFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SlowDownerTheme.colors
    val shownTitle = title ?: stringResource(Res.string.no_track)
    val full = stringResource(Res.string.focus_title, shownTitle, loopName ?: stringResource(Res.string.focus_no_loop))
    val text = remember(full, shownTitle, colors.fg2) { focusTitle(full, shownTitle, colors.fg2) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = DownstepDimens.gutter, end = DownstepDimens.gutter, top = 14.dp, bottom = 8.dp),
    ) {
        Text(
            text,
            style = SlowDownerTheme.type.focusTitle,
            color = colors.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Pill(
            onClick = onExitFocus,
            height = 32.dp,
            container = colors.surface1,
            contentColor = colors.fg2,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(stringResource(Res.string.exit_focus), style = SlowDownerTheme.type.control.copy(fontSize = 12.sp), maxLines = 1)
            Text(
                "F",
                style = SlowDownerTheme.type.tag.copy(fontSize = 10.sp),
                color = colors.fg,
                modifier = Modifier.background(colors.surface3, KeyShape).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** The title as is, everything after it (" — Loop", in whatever order the language puts it) in [secondary]. */
internal fun focusTitle(full: String, title: String, secondary: Color): AnnotatedString = buildAnnotatedString {
    val titleAt = full.indexOf(title)
    if (titleAt != 0 || title.isEmpty()) {
        append(full)
        return@buildAnnotatedString
    }
    append(title)
    withStyle(SpanStyle(color = secondary, fontWeight = FontWeight.Medium)) { append(full.substring(title.length)) }
}

/** Big speed (amber) and pitch (teal) read-outs beside the transport in focus mode: Space Grotesk 18. */
@Composable
fun FocusReadouts(
    speedPercent: Float,
    pitchSemitones: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(formatSpeed(speedPercent), style = SlowDownerTheme.type.focusReadout, color = SlowDownerTheme.colors.amber, maxLines = 1)
        Text(formatPitch(pitchSemitones), style = SlowDownerTheme.type.focusReadout, color = SlowDownerTheme.colors.teal, maxLines = 1)
    }
}

private val KeyShape = RoundedCornerShape(4.dp)
