package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Practice-specific colors that Material 3 has no role for. */
@Immutable
data class SlowDownerColors(
    val loopRegion: Color,
    val markerA: Color,
    val markerB: Color,
    val timelineTrack: Color,
    val primaryTint: Color,
    val tertiaryTint: Color,
    val nowPlayingRow: Color,
)

internal val DarkExtendedColors = SlowDownerColors(
    loopRegion = Color(0xFFF2A541).copy(alpha = 0.20f),
    markerA = Color(0xFFF2A541),
    markerB = Color(0xFF62B6B0),
    timelineTrack = Color(0xFF2C323B),
    primaryTint = Color(0xFFF2A541).copy(alpha = 0.16f),
    tertiaryTint = Color(0xFF62B6B0).copy(alpha = 0.16f),
    nowPlayingRow = Color(0xFFF2A541).copy(alpha = 0.08f),
)

internal val LightExtendedColors = SlowDownerColors(
    loopRegion = Color(0xFFA86400).copy(alpha = 0.18f),
    markerA = Color(0xFFA86400),
    markerB = Color(0xFF2D7C77),
    timelineTrack = Color(0xFFE2D9CC),
    primaryTint = Color(0xFFA86400).copy(alpha = 0.14f),
    tertiaryTint = Color(0xFF2D7C77).copy(alpha = 0.14f),
    nowPlayingRow = Color(0xFFA86400).copy(alpha = 0.08f),
)

internal val LocalSlowDownerColors = staticCompositionLocalOf { DarkExtendedColors }

/** Cover gradients for playlists, picked by id so a playlist keeps its color. */
val PlaylistGradients = listOf(
    listOf(Color(0xFFF2A541), Color(0xFFE07A5F)),
    listOf(Color(0xFF62B6B0), Color(0xFF33598C)),
    listOf(Color(0xFFE07A5F), Color(0xFF734073)),
    listOf(Color(0xFFB8C46A), Color(0xFF3F7A5A)),
)

fun playlistGradient(playlistId: Long) = PlaylistGradients[(playlistId % PlaylistGradients.size).toInt()]
