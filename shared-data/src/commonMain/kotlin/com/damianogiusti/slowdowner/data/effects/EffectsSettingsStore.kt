package com.damianogiusti.slowdowner.data.effects

/** Local-only key-value storage. Blocking; callers pick the thread. */
interface EffectsSettingsStore {
    fun read(): EffectsSettingsDto?
    fun write(settings: EffectsSettingsDto)
}
