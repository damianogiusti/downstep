package com.damianogiusti.slowdowner.ui.common

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.library.LibraryTrack

/** Square tile standing in for cover art: the source, or an equalizer for the open track. */
@Composable
fun TrackArtwork(track: LibraryTrack, isNowPlaying: Boolean, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val colors = MaterialTheme.colorScheme
    val (icon, tint, background) = when {
        isNowPlaying -> Triple(Res.drawable.ic_graphic_eq, colors.onPrimary, colors.primary)
        track.sourceUrl != null -> Triple(Res.drawable.ic_smart_display, colors.secondary, colors.surfaceContainerHigh)
        else -> Triple(Res.drawable.ic_music_note, colors.primary, colors.surfaceContainerHigh)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(background, MaterialTheme.shapes.small),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}
