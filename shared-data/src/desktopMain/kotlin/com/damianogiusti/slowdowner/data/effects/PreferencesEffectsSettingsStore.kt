package com.damianogiusti.slowdowner.data.effects

import java.util.prefs.Preferences

/** Desktop twin of the Android SharedPreferences store: same keys, in the user's Java preferences. */
class PreferencesEffectsSettingsStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/effects"),
) : EffectsSettingsStore {

    override fun read(): EffectsSettingsDto? {
        val quality = preferences.get(KEY_QUALITY, null) ?: return null
        return EffectsSettingsDto(
            eqEnabled = preferences.getBoolean(KEY_EQ_ENABLED, false),
            preampDb = preferences.getFloat(KEY_PREAMP, 0f),
            bandGainsDb = preferences.get(KEY_BANDS, null)
                ?.split(SEPARATOR)
                ?.mapNotNull { it.toFloatOrNull() }
                .orEmpty(),
            qualityName = quality,
            // Missing in settings saved before it existed: off, as it was.
            isVolumeNormalized = preferences.getBoolean(KEY_VOLUME_NORMALIZED, false),
        )
    }

    override fun write(settings: EffectsSettingsDto) {
        preferences.putBoolean(KEY_EQ_ENABLED, settings.eqEnabled)
        preferences.putFloat(KEY_PREAMP, settings.preampDb)
        preferences.put(KEY_BANDS, settings.bandGainsDb.joinToString(SEPARATOR))
        preferences.put(KEY_QUALITY, settings.qualityName)
        preferences.putBoolean(KEY_VOLUME_NORMALIZED, settings.isVolumeNormalized)
        preferences.flush()
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
