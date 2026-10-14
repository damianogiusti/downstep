package com.damianogiusti.slowdowner.data.support

import java.util.prefs.Preferences

/** In the user's Java preferences, like the effects settings. */
class PreferencesSupportPromptStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/support"),
) : SupportPromptStore {

    override fun read(): SupportPromptDto? {
        if (preferences.get(KEY_LAUNCHES, null) == null) return null
        return SupportPromptDto(
            launchCount = preferences.getInt(KEY_LAUNCHES, 0),
            lastShownAtMillis = preferences.getLong(KEY_LAST_SHOWN, NEVER).takeIf { it != NEVER },
        )
    }

    override fun write(dto: SupportPromptDto) {
        preferences.putInt(KEY_LAUNCHES, dto.launchCount)
        preferences.putLong(KEY_LAST_SHOWN, dto.lastShownAtMillis ?: NEVER)
        preferences.flush()
    }

    private companion object {
        const val KEY_LAUNCHES = "launch_count"
        const val KEY_LAST_SHOWN = "last_shown_at_ms"
        const val NEVER = -1L
    }
}
