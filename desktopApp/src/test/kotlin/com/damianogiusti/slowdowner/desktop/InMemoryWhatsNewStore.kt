package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.whatsnew.WhatsNewStore

/** Keeps tests away from the developer's real Java preferences. */
class InMemoryWhatsNewStore : WhatsNewStore {
    var stored: String? = null
    override fun readLastSeenVersion() = stored
    override fun writeLastSeenVersion(version: String) {
        stored = version
    }
}
