package com.damianogiusti.slowdowner.domain.loops

/** Remembers the snap toggle per track. It only has an effect once the track has a tempo. */
class SetSnapToBeatUseCase(private val repository: LoopRepository) {
    suspend operator fun invoke(trackId: String, enabled: Boolean) = repository.setSnapToBeat(trackId, enabled)
}
