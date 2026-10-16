package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import kotlin.math.abs
import kotlin.math.min

/**
 * Speed ramp of a loop: starts at [fromPercent] and adds [stepPercent] every [everyPasses]
 * passes until [toPercent]. Defaults are the design's: from 70% to 100%, +3% every 2 passes.
 */
data class Progression(
    val enabled: Boolean = false,
    val fromPercent: Float = 70f,
    val toPercent: Float = 100f,
    val stepPercent: Float = 3f,
    val everyPasses: Int = 2,
) {
    /** The speed after [passes] passes, never past [toPercent]. Works for a ramp down too. */
    fun speedAfter(passes: Int): Float {
        val steps = passes.coerceAtLeast(0) / everyPasses.coerceAtLeast(1)
        val distance = abs(toPercent - fromPercent)
        val moved = min(steps * stepPercent.coerceAtLeast(0f), distance)
        return if (toPercent >= fromPercent) fromPercent + moved else fromPercent - moved
    }

    fun isAtTarget(passes: Int): Boolean = abs(speedAfter(passes) - toPercent) < EPSILON || stepPercent <= 0f

    /** Where the ramp is, for the transport pill and the card's status line. */
    fun status(passes: Int): ProgressionStatus {
        if (!enabled) return ProgressionStatus.Off
        val current = speedAfter(passes)
        if (isAtTarget(passes)) return ProgressionStatus.AtTarget(toPercent)
        val every = everyPasses.coerceAtLeast(1)
        return ProgressionStatus.Ramping(
            currentPercent = current,
            nextStepPercent = min(stepPercent, abs(toPercent - current)),
            passesUntilNextStep = every - passes.coerceAtLeast(0) % every,
        )
    }

    /** 0..1 along the from → to track. */
    fun progressFraction(passes: Int): Float {
        val distance = abs(toPercent - fromPercent)
        if (distance < EPSILON) return 1f
        return (abs(speedAfter(passes) - fromPercent) / distance).coerceIn(0f, 1f)
    }

    /** Values a stepper or a backup could push out of range, brought back in. */
    fun coerced(): Progression = copy(
        fromPercent = fromPercent.finiteOr(DEFAULT.fromPercent)
            .coerceIn(PlaybackEngine.MIN_SPEED_PERCENT, PlaybackEngine.MAX_SPEED_PERCENT),
        toPercent = toPercent.finiteOr(DEFAULT.toPercent)
            .coerceIn(PlaybackEngine.MIN_SPEED_PERCENT, PlaybackEngine.MAX_SPEED_PERCENT),
        stepPercent = stepPercent.finiteOr(DEFAULT.stepPercent).coerceIn(MIN_STEP_PERCENT, MAX_STEP_PERCENT),
        everyPasses = everyPasses.coerceIn(MIN_EVERY_PASSES, MAX_EVERY_PASSES),
    )

    companion object {
        val DEFAULT = Progression()

        // Stepper increments from the design: ±5% for from/to, ±1% for the step, ±1 for every.
        const val FROM_TO_STEP_PERCENT = 5f
        const val STEP_STEP_PERCENT = 1f
        const val EVERY_STEP_PASSES = 1

        const val MIN_STEP_PERCENT = 1f
        const val MAX_STEP_PERCENT = 25f
        const val MIN_EVERY_PASSES = 1
        const val MAX_EVERY_PASSES = 20

        private const val EPSILON = 0.001f

        private fun Float.finiteOr(fallback: Float) = if (isFinite()) this else fallback
    }
}

sealed interface ProgressionStatus {
    /** "Off — speed stays where you set it". */
    data object Off : ProgressionStatus

    /** "Now 76% · next +3% in 1 pass". */
    data class Ramping(val currentPercent: Float, val nextStepPercent: Float, val passesUntilNextStep: Int) : ProgressionStatus

    /** "At target — 100%". */
    data class AtTarget(val percent: Float) : ProgressionStatus
}
