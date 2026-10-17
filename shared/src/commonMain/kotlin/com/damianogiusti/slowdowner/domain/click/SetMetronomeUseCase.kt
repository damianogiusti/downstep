package com.damianogiusti.slowdowner.domain.click

/**
 * Gives the engine the open track's beat grid and the metronome's state. [engine] is null when the
 * platform has no click (see [ClickTrackEngine]); then nothing happens and the call returns false.
 * It also returns false when the metronome is on without a tempo: it stays silent until one is known.
 */
class SetMetronomeUseCase(private val engine: ClickTrackEngine?) {

    val isAvailable: Boolean get() = engine != null

    operator fun invoke(enabled: Boolean, volume: Float, tempo: TrackTempo?): Boolean {
        val engine = engine ?: return false
        engine.setTempo(tempo)
        engine.setMetronome(enabled && tempo != null, if (volume.isFinite()) volume.coerceIn(0f, 1f) else 0f)
        return !enabled || tempo != null
    }
}
