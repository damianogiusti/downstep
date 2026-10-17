package com.damianogiusti.slowdowner.domain.click

import com.damianogiusti.slowdowner.domain.loops.BeatGrid

/** Count-in before playback; same availability and tempo rules as [SetMetronomeUseCase]. */
class SetCountInUseCase(private val engine: ClickTrackEngine?) {

    val isAvailable: Boolean get() = engine != null

    operator fun invoke(enabled: Boolean, bpm: Float?, bars: Int = DEFAULT_BARS): Boolean {
        val engine = engine ?: return false
        if (enabled && (bpm == null || !BeatGrid.isValidBpm(bpm))) return false
        engine.setCountIn(enabled, bars.coerceIn(1, MAX_BARS), bpm ?: 0f)
        return true
    }

    companion object {
        const val DEFAULT_BARS = 1
        const val MAX_BARS = 4
    }
}
