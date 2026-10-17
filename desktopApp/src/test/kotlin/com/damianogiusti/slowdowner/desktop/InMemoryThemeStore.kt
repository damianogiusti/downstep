package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.theme.ThemeStore

/** Keeps a test's theme picks out of the developer's real Java preferences. */
class InMemoryThemeStore(private var mode: String? = null, private var themeId: String? = null) : ThemeStore {
    override fun readMode() = mode
    override fun readThemeId() = themeId
    override fun write(mode: String, themeId: String) {
        this.mode = mode
        this.themeId = themeId
    }
}
