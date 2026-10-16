package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Adds a loop to a track: from the `L` key ([LoopMath.newLoopAt]) or an accepted suggestion. Without
 * a valid [name] it is called "Loop N". A new loop becomes the active one unless `activate` is false.
 * Returns null when the track is gone.
 */
class CreateLoopUseCase(
    private val repository: LoopRepository,
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
    private val setActiveLoop: SetActiveLoopUseCase,
) {
    suspend operator fun invoke(
        trackId: String,
        bounds: LoopBounds,
        name: String? = null,
        activate: Boolean = true,
    ): Loop? {
        val existing = repository.trackLoops(trackId).loops
        val loopName = name?.let(Loop::normalizeName) ?: Loop.nextDefaultName(existing)
        // The minimum length depends on the duration, which only the open track has.
        val duration = if (engine.isPlayingTrack(trackId, nowPlaying)) engine.durationSeconds else 0f
        val created = repository.create(trackId, loopName, LoopMath.between(bounds.start, bounds.end, duration))
            ?: return null
        if (activate) setActiveLoop(trackId, created.id)
        return created
    }
}
