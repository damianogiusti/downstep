package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.ui.common.TrackArtwork
import com.damianogiusti.slowdowner.ui.common.practiceSummary
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

/** The open track, docked above the content of every screen but the player. */
@Composable
fun MiniPlayerBar(
    track: LibraryTrack,
    controls: PlayerControlsState,
    onOpen: () -> Unit,
    onPlayStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.clickable(onClick = onOpen)) {
            LinearProgressIndicator(
                progress = { controls.position },
                trackColor = SlowDownerTheme.colors.timelineTrack,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                TrackArtwork(track, isNowPlaying = true, size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        practiceSummary(controls.practiceSettings) ?: stringResource(Res.string.practice_default),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                FilledIconButton(onClick = onPlayStop, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painterResource(if (controls.isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play),
                        contentDescription = stringResource(if (controls.isPlaying) Res.string.stop else Res.string.play),
                    )
                }
            }
        }
    }
}
