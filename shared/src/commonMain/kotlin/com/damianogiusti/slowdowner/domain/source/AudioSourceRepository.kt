package com.damianogiusti.slowdowner.domain.source


interface AudioSourceRepository {
    /** Decoding can take seconds: implementations decide which thread it runs on, not the callers. */
    suspend fun open(source: AudioSource): OpenResult
    fun current(): AudioSource?
    fun close()
}
