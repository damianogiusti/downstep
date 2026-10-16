package com.damianogiusti.slowdowner.domain.loops

import kotlinx.coroutines.flow.Flow

/** The loops of a track, sorted by start, with its active loop, tempo and snap setting. */
class ObserveLoopsUseCase(private val repository: LoopRepository) {
    operator fun invoke(trackId: String): Flow<TrackLoops> = repository.observe(trackId)
}
