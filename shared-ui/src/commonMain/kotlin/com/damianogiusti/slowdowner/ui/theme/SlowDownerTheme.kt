package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import com.damianogiusti.slowdowner.domain.theme.ThemeMode

/**
 * Downstep 2.0: the Downstep palette in dark by default; the header's theme menu picks another theme
 * and the mode (persisted, see ThemeViewModel). No dynamic color: the practice colors (loops, A/B,
 * accents) must keep their meaning everywhere. The palette is also mapped onto Material's roles, so
 * Material components and the older screens follow it.
 *
 * A change of theme recomposes everything that reads the colours: fine for a pick or a preview in the
 * menu, and nothing on the playback path depends on it.
 */
@Composable
fun SlowDownerTheme(
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    theme: AppTheme = DownstepTheme,
    content: @Composable () -> Unit,
) {
    val tokens = theme.colors(themeMode)
    val colorScheme = remember(theme, themeMode) { theme.material(themeMode) }
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
