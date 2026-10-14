package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Custom studio theme, dark first. No dynamic color: the practice colors
 * (loop region, A/B markers) must keep their meaning on every wallpaper.
 */
@Composable
fun SlowDownerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSlowDownerColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = slowDownerTypography(),
            shapes = SlowDownerShapes,
            content = content,
        )
    }
}

object SlowDownerTheme {
    val colors: SlowDownerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSlowDownerColors.current
}
