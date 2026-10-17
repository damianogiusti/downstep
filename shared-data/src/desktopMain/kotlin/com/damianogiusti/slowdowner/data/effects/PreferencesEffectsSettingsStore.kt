package com.damianogiusti.slowdowner.data.effects

import java.util.prefs.Preferences

/**
 * Desktop twin of the Android SharedPreferences store: same keys, in the user's Java preferences.
 * The first EQ's keys (eq_enabled, eq_preamp_db, eq_bands_db) are read until the next write, which
 * stores the curve (eq_curve) and removes them.
 */
class PreferencesEffectsSettingsStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/effects"),
) : EffectsSettingsStore {

    override fun read(): EffectsSettingsDto? {
        val quality = preferences.get(KEY_QUALITY, null)
        val curve = preferences.get(KEY_CURVE, null)
        val legacyBands = preferences.get(KEY_BANDS, null)
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
        if (settings.eqCurveJson != null) preferences.put(KEY_CURVE, settings.eqCurveJson) else preferences.remove(KEY_CURVE)
        preferences.put(KEY_QUALITY, settings.qualityName)
        // Migrated: the curve replaces them.
        preferences.remove(KEY_EQ_ENABLED)
        preferences.remove(KEY_PREAMP)
        preferences.remove(KEY_BANDS)
        preferences.flush()
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
