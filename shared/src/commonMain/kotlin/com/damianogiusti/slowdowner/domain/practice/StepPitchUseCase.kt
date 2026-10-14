package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MAX_PITCH_SEMITONES
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MIN_PITCH_SEMITONES
import kotlin.math.roundToInt

/** Moves pitch by whole semitones. */
class StepPitchUseCase(private val engine: PlaybackEngine) {

    operator fun invoke(steps: Int) {
        val target = (engine.pitchSemitones.roundToInt() + steps).toFloat()
        engine.setPitchSemitones(target.coerceIn(MIN_PITCH_SEMITONES, MAX_PITCH_SEMITONES))
    }
}
