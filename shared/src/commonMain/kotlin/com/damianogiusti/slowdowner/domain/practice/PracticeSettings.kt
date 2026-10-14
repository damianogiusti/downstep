package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import kotlin.math.abs

/** What a musician tunes to practise a track: speed, pitch and the A/B loop. */
data class PracticeSettings(
    val speedPercent: Float = 100f,
    val pitchSemitones: Float = 0f,
    val loop: LoopRegion = LoopRegion.WHOLE_FILE,
) {
    /**
     * Equal within what the engine can round: its speed comes back through a time stretch
     * factor and loop points move to audio frames.
     */
    fun matches(other: PracticeSettings): Boolean =
        abs(speedPercent - other.speedPercent) < SPEED_TOLERANCE &&
            abs(pitchSemitones - other.pitchSemitones) < PITCH_TOLERANCE &&
            loop.isOn == other.loop.isOn &&
            (!loop.isOn || loop.matches(other.loop))

    private fun LoopRegion.matches(other: LoopRegion) =
        abs(start - other.start) < LOOP_TOLERANCE && abs(end - other.end) < LOOP_TOLERANCE

    private companion object {
        const val SPEED_TOLERANCE = 0.5f
        const val PITCH_TOLERANCE = 0.05f
        const val LOOP_TOLERANCE = 0.001f
    }
}
