package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult

class FakeAudioSourceRepository(var result: OpenResult = OpenResult.Opened) : AudioSourceRepository {
    var current: AudioSource? = null
    var closed = 0
    override suspend fun open(source: AudioSource): OpenResult {
        current = if (result == OpenResult.Opened) source else null
        return result
    }
    override fun current() = current
    override fun close() {
        current = null
        closed++
    }
}
