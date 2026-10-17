package com.damianogiusti.slowdowner.domain.theme

/**
 * The saved look of the app: a palette ([themeId], one of the UI's themes) in light or dark [mode].
 * The id stays as saved even when this version does not know it: the UI then shows the default
 * palette, and a newer version still finds its own.
 */
data class ThemeSelection(
    val mode: ThemeMode = ThemeMode.DEFAULT,
    val themeId: String = DEFAULT_THEME_ID,
) {
    companion object {
        /** The Downstep 2.0 palette of the design: the only one before themes. */
        const val DEFAULT_THEME_ID = "downstep"
        val DEFAULT = ThemeSelection()
    }
}
