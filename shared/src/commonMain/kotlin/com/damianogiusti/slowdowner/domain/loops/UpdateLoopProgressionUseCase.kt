package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Saves a loop's progression, brought into range. Turned on for the active loop of the open track,
 * it sets the speed at once to where the ramp is for the passes so far.
 */
class UpdateLoopProgressionUseCase(
    private val repository: LoopRepository,
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(loopId: Long, progression: Progression): Loop? {
        val loop = repository.loop(loopId) ?: return null
        val coerced = progression.coerced()
        repository.updateProgression(loopId, coerced)
        val updated = loop.copy(progression = coerced)
        if (engine.isPlayingTrack(loop.trackId, nowPlaying) && repository.isActive(updated)) {
            engine.applyProgressionSpeed(updated)
        }
        return updated
    }
}
