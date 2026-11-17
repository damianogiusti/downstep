package com.damianogiusti.slowdowner.data.player

import java.util.prefs.Preferences

/** Desktop twin of the Android SharedPreferences store: same keys, in the user's Java preferences. */
class PreferencesPlayerPreferencesStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/player"),
) : PlayerPreferencesStore {

    override fun read(): PlayerPreferencesDto? {
        if (KEYS.none { preferences.get(it, null) != null }) return null
        return PlayerPreferencesDto(
            isPitchFine = preferences.getBoolean(KEY_PITCH_FINE, false),
            isLoopFine = preferences.getBoolean(KEY_LOOP_FINE, false),
            isWaveformShown = preferences.getBoolean(KEY_WAVEFORM_SHOWN, false),
        )
    }

    override fun write(preferences: PlayerPreferencesDto) {
        this.preferences.putBoolean(KEY_PITCH_FINE, preferences.isPitchFine)
        this.preferences.putBoolean(KEY_LOOP_FINE, preferences.isLoopFine)
        this.preferences.putBoolean(KEY_WAVEFORM_SHOWN, preferences.isWaveformShown)
        this.preferences.flush()
    }

    private companion object {
        const val KEY_PITCH_FINE = "pitch_fine"
        const val KEY_LOOP_FINE = "loop_fine"
        const val KEY_WAVEFORM_SHOWN = "waveform_shown"
        val KEYS = listOf(KEY_PITCH_FINE, KEY_LOOP_FINE, KEY_WAVEFORM_SHOWN)
    }
}
