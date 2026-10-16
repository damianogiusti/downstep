package com.damianogiusti.slowdowner.domain.click

/**
 * Seam for the design's Metronome and Count-in toggles: a click mixed over the track at its tempo,
 * and bars of clicks before playback starts.
 *
 * There is NO implementation yet: the audio engine has no click generator. DI binds nothing for this
 * interface, so [SetMetronomeUseCase.isAvailable] and [SetCountInUseCase.isAvailable] are false and
 * the UI shows both toggles as unavailable instead of pretending to click. An engine that mixes a
 * click only needs a binding to light them up.
 */
interface ClickTrackEngine {
    /** [bpm] is the track's tempo at 100% speed; the engine follows the playback speed itself. */
    fun setMetronome(enabled: Boolean, bpm: Float)

    /** [bars] of 4 clicks before each start of playback. */
    fun setCountIn(enabled: Boolean, bars: Int, bpm: Float)
}
