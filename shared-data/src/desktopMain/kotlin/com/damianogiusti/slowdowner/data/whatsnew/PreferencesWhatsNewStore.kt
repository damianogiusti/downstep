package com.damianogiusti.slowdowner.data.whatsnew

import java.util.prefs.Preferences

/** In the user's Java preferences, like the support prompt. */
class PreferencesWhatsNewStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/whatsnew"),
) : WhatsNewStore {

    override fun readLastSeenVersion(): String? = preferences.get(KEY_LAST_SEEN, null)

    override fun writeLastSeenVersion(version: String) {
        preferences.put(KEY_LAST_SEEN, version)
        preferences.flush()
    }

    private companion object {
        const val KEY_LAST_SEEN = "last_seen_version"
    }
}
