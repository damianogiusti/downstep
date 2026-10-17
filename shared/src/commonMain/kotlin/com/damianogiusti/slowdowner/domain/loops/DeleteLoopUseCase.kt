package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Deletes a loop. If it was the active one, the first remaining loop (by start) takes over; with
 * none left the engine plays the whole file again.
 */
class DeleteLoopUseCase(
    private val repository: LoopRepository,
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(loopId: Long) {
        val loop = repository.loop(loopId) ?: return
        val wasActive = repository.isActive(loop)
        repository.delete(loopId)
        if (!wasActive) return
        val next = repository.trackLoops(loop.trackId).loops.firstOrNull()
        repository.setActiveLoop(loop.trackId, next?.id)
        if (!engine.isPlayingTrack(loop.trackId, nowPlaying)) return
        engine.setLoop(next?.toRegion(isOn = engine.isLoopOn) ?: LoopRegion.WHOLE_FILE)
    }
}
