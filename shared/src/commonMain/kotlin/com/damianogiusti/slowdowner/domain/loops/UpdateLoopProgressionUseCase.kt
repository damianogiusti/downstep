package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Saves a loop's progression, brought into range. Turned on, it restarts the ramp from From (README
 * §4.6): the passes counted before belonged to no ramp, so they are reset. A change of the steppers
 * while it is on keeps the passes. For the active loop of the open track the speed follows at once.
 */
class UpdateLoopProgressionUseCase(
    private val repository: LoopRepository,
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(loopId: Long, progression: Progression): Loop? {
        val loop = repository.loop(loopId) ?: return null
        val coerced = progression.coerced()
        val turningOn = !loop.progression.enabled && coerced.enabled
        repository.updateProgression(loopId, coerced)
        if (turningOn) repository.resetPasses(loopId)
        val updated = loop.copy(progression = coerced, passes = if (turningOn) 0 else loop.passes)
        if (engine.isPlayingTrack(loop.trackId, nowPlaying) && repository.isActive(updated)) {
            engine.applyProgressionSpeed(updated)
        }
        return updated
    }
}
