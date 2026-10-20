package com.damianogiusti.slowdowner.data.effects

import android.content.SharedPreferences

class SharedPreferencesEffectsSettingsStore(
    private val preferences: SharedPreferences,
) : EffectsSettingsStore {

    override fun read(): EffectsSettingsDto? {
        if (!preferences.contains(KEY_QUALITY)) return null
        return EffectsSettingsDto(
            eqEnabled = preferences.getBoolean(KEY_EQ_ENABLED, false),
            preampDb = preferences.getFloat(KEY_PREAMP, 0f),
            bandGainsDb = preferences.getString(KEY_BANDS, null)
                ?.split(SEPARATOR)
                ?.mapNotNull { it.toFloatOrNull() }
                .orEmpty(),
            qualityName = preferences.getString(KEY_QUALITY, null).orEmpty(),
            // Missing in settings saved before it existed: off, as it was.
            isVolumeNormalized = preferences.getBoolean(KEY_VOLUME_NORMALIZED, false),
        )
    }

    override fun write(settings: EffectsSettingsDto) {
        preferences.edit()
            .putBoolean(KEY_EQ_ENABLED, settings.eqEnabled)
            .putFloat(KEY_PREAMP, settings.preampDb)
            .putString(KEY_BANDS, settings.bandGainsDb.joinToString(SEPARATOR))
            .putString(KEY_QUALITY, settings.qualityName)
            .putBoolean(KEY_VOLUME_NORMALIZED, settings.isVolumeNormalized)
            .apply()
    }

    private companion object {
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_PREAMP = "eq_preamp_db"
        const val KEY_BANDS = "eq_bands_db"
        const val KEY_QUALITY = "quality"
        const val KEY_VOLUME_NORMALIZED = "volume_normalized"
        const val SEPARATOR = ","
    }
}
