package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Downstep 2.0 tokens (the "downstep" theme, see Themes.kt): exactly docs/design/downstep-2/assets/palette.json.
// The four marked "prototype" are not in the JSON; they come from the palettes of the clickable prototype.
// The Material ones are the values Material had before themes.

internal val DarkTokens = DownstepColors(
    isDark = true,
    bg = Color(0xFF0F1114),
    bgDeep = Color(0xFF0B0D10),
    surface1 = Color(0xFF1A1E24),
    surface2 = Color(0xFF20252C),
    surface3 = Color(0xFF2C323B),
    surface4 = Color(0xFF3B424C),
    fg = Color(0xFFECE8E2),
    fg2 = Color(0xFFA4AAB4),
    amber = Color(0xFFF2A541),
    amberHover = Color(0xFFFFB85A),
    onAmber = Color(0xFF2A1800),
    teal = Color(0xFF62B6B0),
    onTeal = Color(0xFF00201E),
    errBg = Color(0xFF93000A),
    errFg = Color(0xFFFFDAD6),
    loopOnBg = Color(0xFF5B3B0B),
    loopOnFg = Color(0xFFFFDDB0),
    barMuted = Color(0xFF2E343D),
    barPlayed = Color(0xFFA4AAB4),
    loopColors = listOf(Color(0xFFF2A541), Color(0xFF62B6B0), Color(0xFFE07A5F), Color(0xFFB8C46A)),
    // Prototype.
    amberGlow = Color(0xFFF2A541).copy(alpha = 0.3f),
    fgGlow = Color(0xFFECE8E2).copy(alpha = 0.6f),
    scrim = Color.Black.copy(alpha = 0.55f),
    chipFgActive = Color(0xFF0F1114),
    // Material.
    tealContainer = Color(0xFF17403D),
    onTealContainer = Color(0xFFCDEEEA),
    onSecondary = Color(0xFF3A0F04),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF410002),
    onErrorContainer = Color(0xFFFFDAD6),
)

internal val LightTokens = DownstepColors(
    isDark = false,
    bg = Color(0xFFFAF6F0),
    bgDeep = Color(0xFFF4EEE5),
    surface1 = Color(0xFFEEE7DC),
    surface2 = Color(0xFFE6DED1),
    surface3 = Color(0xFFDDD4C6),
    surface4 = Color(0xFFD3CABD),
    fg = Color(0xFF1C1B19),
    fg2 = Color(0xFF5E5A54),
    amber = Color(0xFFA86400),
    amberHover = Color(0xFF8F5500),
    onAmber = Color(0xFFFFFFFF),
    teal = Color(0xFF2D7C77),
    onTeal = Color(0xFFFFFFFF),
    errBg = Color(0xFFFFDAD6),
    errFg = Color(0xFFBA1A1A),
    loopOnBg = Color(0xFFFFE1B8),
    loopOnFg = Color(0xFF3A2300),
    barMuted = Color(0xFFD3CABD),
    barPlayed = Color(0xFF5E5A54),
    loopColors = listOf(Color(0xFFA86400), Color(0xFF2D7C77), Color(0xFFB0533A), Color(0xFF3F7A5A)),
    // Prototype.
    amberGlow = Color(0xFFA86400).copy(alpha = 0.28f),
    fgGlow = Color(0xFF1C1B19).copy(alpha = 0.3f),
    scrim = Color(0xFF1C1B19).copy(alpha = 0.35f),
    chipFgActive = Color(0xFFFAF6F0),
    // Material.
    tealContainer = Color(0xFFCDEEEA),
    onTealContainer = Color(0xFF00201E),
    onSecondary = Color.White,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
)

/**
 * The tokens as Material roles, so the Material screens and dialogs (playlist, import, effects,
 * backup, update) match the player. [inversePrimary] is the other mode's [DownstepColors.amber].
 */
internal fun DownstepColors.toMaterial(inversePrimary: Color): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = amber,
        onPrimary = onAmber,
        primaryContainer = loopOnBg,
        onPrimaryContainer = loopOnFg,
        secondary = loopColors[2],
        onSecondary = onSecondary,
        // Material uses secondaryContainer for slider tracks, segmented and tonal buttons:
        // a neutral keeps those calm and leaves the accent colors to the practice controls.
        secondaryContainer = surface3,
        onSecondaryContainer = fg,
        tertiary = teal,
        onTertiary = onTeal,
        tertiaryContainer = tealContainer,
        onTertiaryContainer = onTealContainer,
        error = error,
        onError = onError,
        errorContainer = errBg,
        onErrorContainer = onErrorContainer,
        background = bg,
        onBackground = fg,
        surface = bg,
        onSurface = fg,
        surfaceVariant = surface2,
        onSurfaceVariant = fg2,
        surfaceTint = amber,
        inverseSurface = fg,
        inverseOnSurface = bg,
        inversePrimary = inversePrimary,
        outline = surface4,
        outlineVariant = surface3,
        scrim = Color.Black,
        surfaceBright = surface4,
        surfaceDim = bg,
        surfaceContainerLowest = bgDeep,
        surfaceContainerLow = surface1,
        surfaceContainer = surface2,
        surfaceContainerHigh = surface3,
        surfaceContainerHighest = surface4,
    )
} else {
    lightColorScheme(
        primary = amber,
        onPrimary = onAmber,
        primaryContainer = loopOnBg,
        onPrimaryContainer = loopOnFg,
        secondary = loopColors[2],
        onSecondary = onSecondary,
        secondaryContainer = surface3,
        onSecondaryContainer = fg,
        tertiary = teal,
        onTertiary = onTeal,
        tertiaryContainer = tealContainer,
        onTertiaryContainer = onTealContainer,
        error = error,
        onError = onError,
        errorContainer = errBg,
        onErrorContainer = onErrorContainer,
        background = bg,
        onBackground = fg,
        surface = bg,
        onSurface = fg,
        surfaceVariant = surface2,
        onSurfaceVariant = fg2,
        surfaceTint = amber,
        inverseSurface = fg,
        inverseOnSurface = bg,
        inversePrimary = inversePrimary,
        outline = surface4,
        outlineVariant = surface3,
        scrim = Color.Black,
        // Light surfaces top out at white whatever the palette's tint.
        surfaceBright = Color.White,
        surfaceDim = surface2,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = bgDeep,
        surfaceContainer = surface1,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface3,
    )
}
