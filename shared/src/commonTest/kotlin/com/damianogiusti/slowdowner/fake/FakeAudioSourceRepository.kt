package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository

class FakeAudioSourceRepository(var openSucceeds: Boolean = true) : AudioSourceRepository {
    var current: AudioSource? = null
    var closed = 0
    override fun open(source: AudioSource) = openSucceeds.also { if (it) current = source }
    override fun current() = current
    override fun close() {
        current = null
        closed++
    }
}
