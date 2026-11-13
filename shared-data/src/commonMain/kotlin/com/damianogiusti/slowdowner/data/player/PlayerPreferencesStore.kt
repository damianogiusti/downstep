package com.damianogiusti.slowdowner.data.player

/** Local-only key-value storage. Blocking; callers pick the thread. */
interface PlayerPreferencesStore {
    fun read(): PlayerPreferencesDto?
    fun write(preferences: PlayerPreferencesDto)
}
