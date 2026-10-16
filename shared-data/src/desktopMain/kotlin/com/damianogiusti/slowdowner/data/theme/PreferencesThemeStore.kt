package com.damianogiusti.slowdowner.data.theme

import java.util.prefs.Preferences

/** The theme in the user's Java preferences, next to the effects settings. */
class PreferencesThemeStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/theme"),
) : ThemeStore {

    override fun read(): String? = preferences.get(KEY_MODE, null)

    override fun write(mode: String) {
        preferences.put(KEY_MODE, mode)
        preferences.flush()
    }

    private companion object {
        const val KEY_MODE = "mode"
    }
}
