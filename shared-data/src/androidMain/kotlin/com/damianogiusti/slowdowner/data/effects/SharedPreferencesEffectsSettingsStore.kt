package com.damianogiusti.slowdowner.data.effects

import android.content.SharedPreferences

/**
 * The first EQ's keys (eq_enabled, eq_preamp_db, eq_bands_db) are read until the next write, which
 * stores the curve (eq_curve) and removes them.
 */
class SharedPreferencesEffectsSettingsStore(
    private val preferences: SharedPreferences,
) : EffectsSettingsStore {

    override fun read(): EffectsSettingsDto? {
        val quality = preferences.getString(KEY_QUALITY, null)
        val curve = preferences.getString(KEY_CURVE, null)
        val legacyBands = preferences.getString(KEY_BANDS, null)
        if (quality == null && curve == null && legacyBands == null) return null
        return EffectsSettingsDto(
            eqCurveJson = curve,
            qualityName = quality.orEmpty(),
            legacyEq = legacyBands?.let { bands ->
                LegacyEqDto(
                    enabled = preferences.getBoolean(KEY_EQ_ENABLED, false),
                    preampDb = preferences.getFloat(KEY_PREAMP, 0f),
                    bandGainsDb = bands.split(SEPARATOR).mapNotNull { it.toFloatOrNull() },
                )
            },
        )
    }

    override fun write(settings: EffectsSettingsDto) {
        preferences.edit()
            .putString(KEY_CURVE, settings.eqCurveJson)
            .putString(KEY_QUALITY, settings.qualityName)
            // Migrated: the curve replaces them.
            .remove(KEY_EQ_ENABLED)
            .remove(KEY_PREAMP)
            .remove(KEY_BANDS)
            .apply()
    }

    private companion object {
        const val KEY_CURVE = "eq_curve"
        const val KEY_QUALITY = "quality"
        // The first EQ's.
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_PREAMP = "eq_preamp_db"
        const val KEY_BANDS = "eq_bands_db"
        const val SEPARATOR = ","
    }
}
