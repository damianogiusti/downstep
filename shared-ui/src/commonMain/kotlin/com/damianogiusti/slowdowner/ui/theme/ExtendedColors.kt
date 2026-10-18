package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Downstep 2.0 palette (palette.json), read through [SlowDownerTheme.colors]. Material has no role
 * for most of these (loop colours, waveform bars, the two accents and what sits on them).
 *
 * Accents: [amber] = speed, loop start (A), play, loop on; [teal] = pitch, loop end (B), isolate.
 * Surfaces: [surface1] cards → [surface2] hover → [surface3] borders → [surface4] strongest.
 */
@Immutable
data class DownstepColors(
    val isDark: Boolean,
    val bg: Color,
    /** Waveform ground, title bar. */
    val bgDeep: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surface4: Color,
    val fg: Color,
    val fg2: Color,
    val amber: Color,
    val amberHover: Color,
    val onAmber: Color,
    val teal: Color,
    val onTeal: Color,
    val errBg: Color,
    val errFg: Color,
    /** The loop toggle pill while looping. */
    val loopOnBg: Color,
    val loopOnFg: Color,
    /** Waveform bars not yet played / played (outside the active loop). */
    val barMuted: Color,
    val barPlayed: Color,
    /** Cycled by loop id: see [loopColor]. */
    val loopColors: List<Color>,
    /** Play button glow. */
    val amberGlow: Color,
    /** Playhead glow. */
    val fgGlow: Color,
    /** Behind the library drawer. */
    val scrim: Color,
    /** Text on the inverted (active) loop chip and the selected segment of a segmented pill. */
    val chipFgActive: Color,
    // Material roles only (see toMaterial), per palette so that every theme's Material screens match it.
    /** Material's tertiaryContainer and what sits on it. */
    val tealContainer: Color,
    val onTealContainer: Color,
    /** Text on loopColors[2], which is Material's secondary. */
    val onSecondary: Color,
    /** Material's error (text and icons on the background), onError and onErrorContainer (on [errBg]). */
    val error: Color,
    val onError: Color,
    val onErrorContainer: Color,
) {
    /** The colour of a loop, from its colour index (loop id mod 4, see LoopChipModel.colorIndex). */
    fun loopColor(colorIndex: Int): Color = loopColors[colorIndex.mod(loopColors.size)]

    // Roles of the 1.x screens, kept so they follow the new palette without changes.
    val loopRegion: Color get() = amber.copy(alpha = if (isDark) 0.20f else 0.18f)
    val markerA: Color get() = amber
    val markerB: Color get() = teal
    val timelineTrack: Color get() = surface3
    val primaryTint: Color get() = amber.copy(alpha = if (isDark) 0.16f else 0.14f)
    val tertiaryTint: Color get() = teal.copy(alpha = if (isDark) 0.16f else 0.14f)
    val nowPlayingRow: Color get() = amber.copy(alpha = 0.08f)
}

internal val LocalDownstepColors = staticCompositionLocalOf { DarkTokens }

/** Cover gradients for playlists, picked by id so a playlist keeps its color. */
val PlaylistGradients = listOf(
    listOf(Color(0xFFF2A541), Color(0xFFE07A5F)),
    listOf(Color(0xFF62B6B0), Color(0xFF33598C)),
    listOf(Color(0xFFE07A5F), Color(0xFF734073)),
    listOf(Color(0xFFB8C46A), Color(0xFF3F7A5A)),
)

fun playlistGradient(playlistId: Long) = PlaylistGradients[(playlistId % PlaylistGradients.size).toInt()]
