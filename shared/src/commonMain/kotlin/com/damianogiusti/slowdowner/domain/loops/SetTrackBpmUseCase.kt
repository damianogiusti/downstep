package com.damianogiusti.slowdowner.domain.loops

/**
 * Sets the tempo of a track, entered by the user: there is no beat detection. Null clears it, which
 * also stops snapping. False, and nothing saved, outside [BeatGrid.MIN_BPM]..[BeatGrid.MAX_BPM].
 */
class SetTrackBpmUseCase(private val repository: LoopRepository) {
    suspend operator fun invoke(trackId: String, bpm: Float?): Boolean {
        if (bpm != null && !BeatGrid.isValidBpm(bpm)) return false
        repository.setBpm(trackId, bpm)
        return true
    }
}
