package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MAX_PITCH_SEMITONES
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MIN_PITCH_SEMITONES
import kotlin.math.roundToInt

/** How far one pitch step goes: a semitone, or 5 cents in Fine mode. */
enum class PitchStep(val semitones: Float) {
    Semitone(1f),
    Fine(0.05f),
}

/** Moves pitch by [PitchStep]s. The cents already there are kept. */
class StepPitchUseCase(private val engine: PlaybackEngine) {

    operator fun invoke(steps: Int, step: PitchStep = PitchStep.Semitone) {
        val target = (engine.pitchSemitones + steps * step.semitones).coerceIn(MIN_PITCH_SEMITONES, MAX_PITCH_SEMITONES)
        // Two decimals: 2.15f + 0.05f must give 2.2, not 2.1999998.
        engine.setPitchSemitones((target * 100).roundToInt() / 100f)
    }
}
