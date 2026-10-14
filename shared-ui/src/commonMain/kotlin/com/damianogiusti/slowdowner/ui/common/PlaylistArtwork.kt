package com.damianogiusti.slowdowner.ui.common

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import com.damianogiusti.slowdowner.ui.theme.playlistGradient

@Composable
fun PlaylistArtwork(playlistId: Long, iconSize: Dp, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(playlistGradient(playlistId))),
    ) {
        Icon(
            painterResource(Res.drawable.ic_queue_music),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
            modifier = Modifier.size(iconSize),
        )
    }
}
