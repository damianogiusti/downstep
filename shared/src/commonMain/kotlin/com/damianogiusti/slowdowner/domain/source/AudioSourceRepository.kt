package com.damianogiusti.slowdowner.domain.source


interface AudioSourceRepository {
    fun open(source: AudioSource): Boolean
    fun current(): AudioSource?
    fun close()
}
