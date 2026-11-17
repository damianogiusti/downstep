package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult
import kotlinx.coroutines.CompletableDeferred

class FakeAudioSourceRepository(var result: OpenResult) : AudioSourceRepository {
    /** When set, every open waits for it: the decode of a long file, held by the test. */
    var decode: CompletableDeferred<Unit>? = null

    override suspend fun open(source: AudioSource): OpenResult {
        decode?.await()
        return result
    }

    override fun current(): AudioSource? = null
    override fun close() = Unit
}
