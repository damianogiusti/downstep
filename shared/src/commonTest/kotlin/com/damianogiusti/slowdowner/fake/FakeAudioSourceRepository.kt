package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult

/** Opens whatever it is given with [result]; a stems source can have its own [stemsResult]. */
class FakeAudioSourceRepository(
    var result: OpenResult = OpenResult.Opened,
    var stemsResult: OpenResult? = null,
) : AudioSourceRepository {
    var current: AudioSource? = null
    var closed = 0
    /** Every source asked for, in order, whether it opened or not. */
    val opened = mutableListOf<AudioSource>()

    override suspend fun open(source: AudioSource): OpenResult {
        opened += source
        val outcome = if (source is AudioSource.Stems) stemsResult ?: result else result
        current = if (outcome == OpenResult.Opened) source else null
        return outcome
    }
    override fun current() = current
    override fun close() {
        current = null
        closed++
    }
}
