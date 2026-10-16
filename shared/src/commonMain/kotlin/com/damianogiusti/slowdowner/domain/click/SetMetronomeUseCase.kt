package com.damianogiusti.slowdowner.domain.click

import com.damianogiusti.slowdowner.domain.loops.BeatGrid

/**
 * Turns the metronome on or off. [engine] is null when the platform has no click (today always,
 * see [ClickTrackEngine]); then nothing happens and the call returns false. It also returns false
 * when turning on without a valid tempo: a click needs one.
 */
class SetMetronomeUseCase(private val engine: ClickTrackEngine?) {

    val isAvailable: Boolean get() = engine != null

    operator fun invoke(enabled: Boolean, bpm: Float?): Boolean {
        val engine = engine ?: return false
        if (enabled && (bpm == null || !BeatGrid.isValidBpm(bpm))) return false
        engine.setMetronome(enabled, bpm ?: 0f)
        return true
    }
}
