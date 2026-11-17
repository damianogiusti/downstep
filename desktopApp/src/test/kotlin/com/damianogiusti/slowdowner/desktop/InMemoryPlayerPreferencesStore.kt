package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.player.PlayerPreferencesDto
import com.damianogiusti.slowdowner.data.player.PlayerPreferencesStore

/** Keeps tests away from the developer's real Java preferences. */
class InMemoryPlayerPreferencesStore : PlayerPreferencesStore {
    var stored: PlayerPreferencesDto? = null
    override fun read() = stored
    override fun write(preferences: PlayerPreferencesDto) {
        stored = preferences
    }
}
