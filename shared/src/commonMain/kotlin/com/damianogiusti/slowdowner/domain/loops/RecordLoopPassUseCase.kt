package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Counts one pass of a loop: call it each time the active loop wraps during playback. With
 * progression on, the speed then follows the ramp. Returns the updated loop.
 */
class RecordLoopPassUseCase(
    private val repository: LoopRepository,
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(loopId: Long): Loop? {
        val loop = repository.addPass(loopId) ?: return null
        if (engine.isPlayingTrack(loop.trackId, nowPlaying) && repository.isActive(loop)) {
            engine.applyProgressionSpeed(loop)
        }
        return loop
    }
}
