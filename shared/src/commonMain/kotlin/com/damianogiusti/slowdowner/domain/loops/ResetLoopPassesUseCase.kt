package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/** Sets a loop's passes back to zero; with progression on, the speed goes back to its start. */
class ResetLoopPassesUseCase(
    private val repository: LoopRepository,
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(loopId: Long) {
        val loop = repository.loop(loopId) ?: return
        repository.resetPasses(loopId)
        val reset = loop.copy(passes = 0)
        if (engine.isPlayingTrack(loop.trackId, nowPlaying) && repository.isActive(reset)) {
            engine.applyProgressionSpeed(reset)
        }
    }
}
