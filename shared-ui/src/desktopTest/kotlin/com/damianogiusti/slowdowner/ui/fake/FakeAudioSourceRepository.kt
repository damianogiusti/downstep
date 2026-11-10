package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult

class FakeAudioSourceRepository(var result: OpenResult) : AudioSourceRepository {
    override suspend fun open(source: AudioSource) = result
    override fun current(): AudioSource? = null
    override fun close() = Unit
}
