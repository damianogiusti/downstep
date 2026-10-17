package com.damianogiusti.slowdowner.domain.click

/**
 * The engine's metronome and count-in: a click mixed over the track on its beat grid, and clicks
 * before playback starts. The engine follows the playback speed, seeks and loop wraps itself; the
 * click is never stretched, pitched or equalised, and exports never contain it.
 *
 * Platforms without one bind nothing: [SetMetronomeUseCase.isAvailable] and
 * [SetCountInUseCase.isAvailable] are then false and the UI shows both toggles as unavailable.
 */
interface ClickTrackEngine {
    /** The open track's beat grid at 100% speed; null clears it (no metronome, count-in at 120 BPM). */
    fun setTempo(tempo: TrackTempo?)

    /** [volume] 0..1, the click's own level (the count-in's too). */
    fun setMetronome(enabled: Boolean, volume: Float)

    /** [beats] clicks before playback starts (0 = off); [everyLoop] also before each loop restart. */
    fun setCountIn(beats: Int, everyLoop: Boolean)

    /** Count-in clicks still to play; 0 when not counting in. */
    val countInRemaining: Int
}
