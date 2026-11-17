package com.damianogiusti.slowdowner.data.player

import android.content.SharedPreferences

class SharedPreferencesPlayerPreferencesStore(
    private val preferences: SharedPreferences,
) : PlayerPreferencesStore {

    override fun read(): PlayerPreferencesDto? {
        if (KEYS.none(preferences::contains)) return null
        return PlayerPreferencesDto(
            isPitchFine = preferences.getBoolean(KEY_PITCH_FINE, false),
            isLoopFine = preferences.getBoolean(KEY_LOOP_FINE, false),
            isWaveformShown = preferences.getBoolean(KEY_WAVEFORM_SHOWN, false),
        )
    }

    override fun write(preferences: PlayerPreferencesDto) {
        this.preferences.edit()
            .putBoolean(KEY_PITCH_FINE, preferences.isPitchFine)
            .putBoolean(KEY_LOOP_FINE, preferences.isLoopFine)
            .putBoolean(KEY_WAVEFORM_SHOWN, preferences.isWaveformShown)
            .apply()
    }

    private companion object {
        const val KEY_PITCH_FINE = "pitch_fine"
        const val KEY_LOOP_FINE = "loop_fine"
        const val KEY_WAVEFORM_SHOWN = "waveform_shown"
        val KEYS = listOf(KEY_PITCH_FINE, KEY_LOOP_FINE, KEY_WAVEFORM_SHOWN)
    }
}
