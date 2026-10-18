package com.damianogiusti.slowdowner.data.click

import java.util.prefs.Preferences

/** The metronome and count-in settings in the user's Java preferences, beside the effects and theme. */
class PreferencesClickSettingsStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/click"),
) : ClickSettingsStore {

    override fun read(): ClickSettingsDto = ClickSettingsDto(
        metronomeEnabled = preferences.get(KEY_METRONOME, null)?.toBooleanStrictOrNull(),
        volume = preferences.get(KEY_VOLUME, null)?.toFloatOrNull(),
        countInEnabled = preferences.get(KEY_COUNT_IN, null)?.toBooleanStrictOrNull(),
        countInBeats = preferences.get(KEY_COUNT_IN_BEATS, null)?.toIntOrNull(),
        countInEveryLoop = preferences.get(KEY_COUNT_IN_EVERY_LOOP, null)?.toBooleanStrictOrNull(),
    )

    override fun write(settings: ClickSettingsDto) {
        settings.metronomeEnabled?.let { preferences.putBoolean(KEY_METRONOME, it) }
        settings.volume?.let { preferences.putFloat(KEY_VOLUME, it) }
        settings.countInEnabled?.let { preferences.putBoolean(KEY_COUNT_IN, it) }
        settings.countInBeats?.let { preferences.putInt(KEY_COUNT_IN_BEATS, it) }
        settings.countInEveryLoop?.let { preferences.putBoolean(KEY_COUNT_IN_EVERY_LOOP, it) }
        preferences.flush()
    }

    private companion object {
        const val KEY_METRONOME = "metronome_enabled"
        const val KEY_VOLUME = "click_volume"
        const val KEY_COUNT_IN = "count_in_enabled"
        const val KEY_COUNT_IN_BEATS = "count_in_beats"
        const val KEY_COUNT_IN_EVERY_LOOP = "count_in_every_loop"
    }
}
