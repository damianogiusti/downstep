package com.damianogiusti.slowdowner.data.player

import java.util.prefs.Preferences

/** Desktop twin of the Android SharedPreferences store: same key, in the user's Java preferences. */
class PreferencesPlayerPreferencesStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/player"),
) : PlayerPreferencesStore {

    override fun read(): PlayerPreferencesDto? {
        if (preferences.get(KEY_PITCH_FINE, null) == null) return null
        return PlayerPreferencesDto(isPitchFine = preferences.getBoolean(KEY_PITCH_FINE, false))
    }

    override fun write(preferences: PlayerPreferencesDto) {
        this.preferences.putBoolean(KEY_PITCH_FINE, preferences.isPitchFine)
        this.preferences.flush()
    }

    private companion object {
        const val KEY_PITCH_FINE = "pitch_fine"
    }
}
