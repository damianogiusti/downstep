package com.damianogiusti.slowdowner.domain.source


interface AudioSourceRepository {
    /** Decoding can take seconds: implementations decide which thread it runs on, not the callers. */
    suspend fun open(source: AudioSource): Boolean
    fun current(): AudioSource?
    fun close()
}
