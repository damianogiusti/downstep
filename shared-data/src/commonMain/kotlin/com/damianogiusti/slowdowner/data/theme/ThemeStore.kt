package com.damianogiusti.slowdowner.data.theme

/** Local-only key-value storage of the theme name. Blocking; callers pick the thread. */
interface ThemeStore {
    fun read(): String?
    fun write(mode: String)
}
