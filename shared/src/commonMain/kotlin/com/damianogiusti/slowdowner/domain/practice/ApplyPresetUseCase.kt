package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.loops.LoopRepository
import com.damianogiusti.slowdowner.domain.loops.activeLoopRegion
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Applies a preset's speed and pitch, and its loop switch. Not its region: the engine loops the active
 * named loop of the track (README §4.1), and a preset saved before 2.0, or with another loop active,
 * holds a region that matches no loop. Without an active loop nothing loops.
 */
class ApplyPresetUseCase(
    private val engine: PlaybackEngine,
    private val loops: LoopRepository,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(preset: PracticePreset) {
        engine.setSpeedPercent(preset.settings.speedPercent)
        engine.setPitchSemitones(preset.settings.pitchSemitones)
        // Tracks opened outside the library (Spotify) have no loops: their region stays as it is.
        val track = nowPlaying.observe().value ?: return
        val trackLoops = loops.trackLoops(track.id)
        // Another track may have opened while the loops loaded.
        if (!engine.isOpen || nowPlaying.observe().value?.id != track.id) return
        engine.setLoop(trackLoops.activeLoopRegion(isOn = preset.settings.loop.isOn))
    }
}
