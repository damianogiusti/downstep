package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Moves a loop's A/B points, from a drag, the A/B keys, ½, ×2 or a nudge (see [LoopMath]). When it
 * is the active loop of the open track the engine follows, keeping looping on or off as it was.
 */
class UpdateLoopRegionUseCase(
    private val repository: LoopRepository,
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(loopId: Long, bounds: LoopBounds): Loop? {
        val loop = repository.loop(loopId) ?: return null
        val isOpen = engine.isPlayingTrack(loop.trackId, nowPlaying)
        val normalized = LoopMath.between(bounds.start, bounds.end, if (isOpen) engine.durationSeconds else 0f)
        repository.updateBounds(loopId, normalized)
        val updated = loop.copy(start = normalized.start, end = normalized.end)
        if (isOpen && repository.isActive(updated)) engine.setLoop(updated.toRegion(isOn = engine.isLoopOn))
        return updated
    }
}
