package com.damianogiusti.slowdowner.domain.click

/**
 * Count-in before playback (and before each loop restart with [everyLoop]): [beats] clicks at the
 * effective tempo, the BPM set with [SetMetronomeUseCase] times the speed, or 120 BPM without one.
 * False when the platform has no click.
 */
class SetCountInUseCase(private val engine: ClickTrackEngine?) {

    val isAvailable: Boolean get() = engine != null

    operator fun invoke(enabled: Boolean, beats: Int = ClickSettings.DEFAULT_COUNT_IN_BEATS, everyLoop: Boolean = false): Boolean {
        val engine = engine ?: return false
        val count = beats.coerceIn(ClickSettings.MIN_COUNT_IN_BEATS, ClickSettings.MAX_COUNT_IN_BEATS)
        engine.setCountIn(if (enabled) count else 0, enabled && everyLoop)
        return true
    }
}
