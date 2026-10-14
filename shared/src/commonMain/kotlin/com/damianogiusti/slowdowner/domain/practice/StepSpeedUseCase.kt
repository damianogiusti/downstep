package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MAX_SPEED_PERCENT
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MIN_SPEED_PERCENT
import kotlin.math.ceil
import kotlin.math.floor

/** Moves speed by whole 5% steps, snapping to the grid first: 73% + 1 step → 75%. */
class StepSpeedUseCase(private val engine: PlaybackEngine) {

    operator fun invoke(steps: Int) {
        if (steps == 0) return
        val gridIndex = engine.speedPercent / STEP_PERCENT
        // The engine returns values like 74.9998 for 75: ignore that noise before snapping.
        val base = if (steps > 0) floor(gridIndex + EPSILON) else ceil(gridIndex - EPSILON)
        val target = ((base + steps) * STEP_PERCENT).coerceIn(MIN_SPEED_PERCENT, MAX_SPEED_PERCENT)
        engine.setSpeedPercent(target)
    }

    private companion object {
        const val STEP_PERCENT = 5f
        const val EPSILON = 0.01f
    }
}
