package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import com.damianogiusti.slowdowner.domain.theme.ThemeMode

/**
 * Downstep 2.0: dark by default, light on the header toggle (persisted, see ThemeViewModel). No dynamic
 * color: the practice colors (loops, A/B, accents) must keep their meaning everywhere. The palette is
 * also mapped onto Material's roles, so Material components and the older screens follow it.
 */
@Composable
fun SlowDownerTheme(
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    val tokens = if (themeMode == ThemeMode.Light) LightTokens else DarkTokens
    val colorScheme = remember(tokens) { tokens.toMaterial() }
    CompositionLocalProvider(
        LocalDownstepColors provides tokens,
        LocalDownstepType provides rememberDownstepType(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = slowDownerTypography(),
            shapes = SlowDownerShapes,
            content = content,
        )
    }
}

object SlowDownerTheme {
    val colors: DownstepColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDownstepColors.current

    val type: DownstepType
        @Composable
        @ReadOnlyComposable
        get() = LocalDownstepType.current
}
