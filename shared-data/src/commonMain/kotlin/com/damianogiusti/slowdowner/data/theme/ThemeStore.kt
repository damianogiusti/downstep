package com.damianogiusti.slowdowner.data.theme

/**
 * Local-only key-value storage of the theme: the mode name and the palette id, each null until first
 * saved. Blocking; callers pick the thread.
 */
interface ThemeStore {
    fun readMode(): String?
    fun readThemeId(): String?
    fun write(mode: String, themeId: String)
}
