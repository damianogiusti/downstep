package com.damianogiusti.slowdowner.data.support

/** Local-only key-value storage, outside the library: a backup restore must not reset it. Blocking. */
interface SupportPromptStore {
    fun read(): SupportPromptDto?
    fun write(dto: SupportPromptDto)
}
