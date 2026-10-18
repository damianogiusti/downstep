package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.effects.ActiveEq
import com.damianogiusti.slowdowner.domain.loops.LoopRepository
import com.damianogiusti.slowdowner.domain.loops.activeLoopRegion
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Applies a preset's speed and pitch, its loop switch and its EQ. Not its region: the engine loops
 * the active named loop of the track (README §4.1), and a preset saved before 2.0, or with another
 * loop active, holds a region that matches no loop. Without an active loop nothing loops. A preset
 * saved without an EQ leaves the EQ alone; one with an EQ makes it the track's own, as an edit would.
 */
class ApplyPresetUseCase(
    private val engine: PlaybackEngine,
    private val loops: LoopRepository,
    private val nowPlaying: NowPlayingRepository,
    private val activeEq: ActiveEq,
) {
    suspend operator fun invoke(preset: PracticePreset) {
        engine.setSpeedPercent(preset.settings.speedPercent)
        engine.setPitchSemitones(preset.settings.pitchSemitones)
        // Tracks opened outside the library (Spotify) have no loops: their region stays as it is.
        val track = nowPlaying.observe().value ?: return
        preset.eq?.let { activeEq.request(track.id, it) }
        val trackLoops = loops.trackLoops(track.id)
        // Another track may have opened while the loops loaded.
        if (!engine.isOpen || nowPlaying.observe().value?.id != track.id) return
        engine.setLoop(trackLoops.activeLoopRegion(isOn = preset.settings.loop.isOn))
    }
}
