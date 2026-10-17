package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.click.ClickSettingsDto
import com.damianogiusti.slowdowner.data.click.ClickSettingsStore

/** Keeps tests away from the developer's real Java preferences: a count-in left on there stalls playback. */
class InMemoryClickSettingsStore : ClickSettingsStore {
    private var stored = ClickSettingsDto()
    override fun read() = stored
    override fun write(settings: ClickSettingsDto) {
        stored = settings
    }
}
