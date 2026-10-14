package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.support.SupportPromptDto
import com.damianogiusti.slowdowner.data.support.SupportPromptStore

/** Keeps tests away from the developer's real Java preferences: a test run is not a launch. */
class InMemorySupportPromptStore : SupportPromptStore {
    var stored: SupportPromptDto? = null
    override fun read() = stored
    override fun write(dto: SupportPromptDto) {
        stored = dto
    }
}
