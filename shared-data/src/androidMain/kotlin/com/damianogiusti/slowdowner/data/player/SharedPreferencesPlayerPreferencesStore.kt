package com.damianogiusti.slowdowner.data.player

import android.content.SharedPreferences

class SharedPreferencesPlayerPreferencesStore(
    private val preferences: SharedPreferences,
) : PlayerPreferencesStore {

    override fun read(): PlayerPreferencesDto? {
        if (!preferences.contains(KEY_PITCH_FINE)) return null
        return PlayerPreferencesDto(isPitchFine = preferences.getBoolean(KEY_PITCH_FINE, false))
    }

    override fun write(preferences: PlayerPreferencesDto) {
        this.preferences.edit().putBoolean(KEY_PITCH_FINE, preferences.isPitchFine).apply()
    }

    private companion object {
        const val KEY_PITCH_FINE = "pitch_fine"
    }
}
