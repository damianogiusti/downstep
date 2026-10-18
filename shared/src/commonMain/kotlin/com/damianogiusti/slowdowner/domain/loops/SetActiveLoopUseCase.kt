package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Makes a loop the active one, as a click on its chip does: on the open track the engine loops it
 * and the playhead jumps to its start. A null [loopId] leaves the track without an active loop and
 * turns looping off.
 *
 * A loop grabbed on the waveform becomes active without either: [seekToStart] false keeps the
 * playhead, [loopOn] false keeps looping off (with looping on, a playhead outside the loop still goes
 * to its start, as for any change to the looped region).
 */
class SetActiveLoopUseCase(
    private val repository: LoopRepository,
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(trackId: String, loopId: Long?, seekToStart: Boolean = true, loopOn: Boolean = true) {
        val loop = loopId?.let { repository.loop(it) }?.takeIf { it.trackId == trackId }
        repository.setActiveLoop(trackId, loop?.id)
        if (!engine.isPlayingTrack(trackId, nowPlaying)) return
        if (loop == null) {
            engine.setLoopOn(false)
            return
        }
        engine.setLoop(loop.toRegion(isOn = loopOn))
        if (seekToStart) engine.seekTo(loop.start)
        engine.applyProgressionSpeed(loop)
    }
}
