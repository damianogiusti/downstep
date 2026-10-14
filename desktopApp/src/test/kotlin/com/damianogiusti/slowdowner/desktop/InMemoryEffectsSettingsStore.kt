package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.effects.EffectsSettingsDto
import com.damianogiusti.slowdowner.data.effects.EffectsSettingsStore

/** Keeps tests away from the developer's real Java preferences. */
class InMemoryEffectsSettingsStore : EffectsSettingsStore {
    private var stored: EffectsSettingsDto? = null
    override fun read() = stored
    override fun write(settings: EffectsSettingsDto) {
        stored = settings
    }
}
