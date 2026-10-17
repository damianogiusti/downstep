package com.damianogiusti.slowdowner.data.theme

import java.util.prefs.Preferences

/**
 * The theme in the user's Java preferences, next to the effects settings. "mode" is the key of the
 * versions before themes, so their choice of light or dark is still read.
 */
class PreferencesThemeStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/theme"),
) : ThemeStore {

    override fun readMode(): String? = preferences.get(KEY_MODE, null)

    override fun readThemeId(): String? = preferences.get(KEY_THEME, null)

    override fun write(mode: String, themeId: String) {
        preferences.put(KEY_MODE, mode)
        preferences.put(KEY_THEME, themeId)
        preferences.flush()
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_THEME = "theme"
    }
}
