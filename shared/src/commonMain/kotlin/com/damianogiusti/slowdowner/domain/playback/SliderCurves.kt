package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.SLIDER_MAX
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.truncate

/**
 * Slider position ↔ value curves, so that presets, backups and slider feel match on every platform. Sliders run 0..[SLIDER_MAX] with a dead zone at the centre.
 * The Android engine applies them natively; other engines use these.
 */
object SliderCurves {
    private const val CENTRE = SLIDER_MAX / 2

    // Speed: one exponential curve per half, on the stretch factor (100 / speed%).
    private const val SPEED_DEAD_ZONE = 100
    private const val SPEED_HALF_SPAN = CENTRE - SPEED_DEAD_ZONE
    private const val MAX_STRETCH = 4.0 // 25%
    private const val MIN_STRETCH = 0.5 // 200%

    // Pitch and mix: linear per half.
    private const val PITCH_DEAD_ZONE = 20
    private const val PITCH_RANGE = 12.0
    private const val MIX_DEAD_ZONE = 303

    fun speedPercent(position: Int): Float {
        val d = CENTRE - position.coerceIn(0, SLIDER_MAX)
        val stretch = when {
            d > SPEED_DEAD_ZONE -> MAX_STRETCH.pow((d - SPEED_DEAD_ZONE).toDouble() / SPEED_HALF_SPAN)
            d < -SPEED_DEAD_ZONE -> MIN_STRETCH.pow((-d - SPEED_DEAD_ZONE).toDouble() / SPEED_HALF_SPAN)
            else -> 1.0
        }
        return (100.0 / stretch).toFloat()
    }

    fun speedPosition(speedPercent: Float): Int {
        val stretch = 100.0 / speedPercent.coerceIn(PlaybackEngine.MIN_SPEED_PERCENT, PlaybackEngine.MAX_SPEED_PERCENT)
        val position = when {
            stretch > 1.0 -> CENTRE - SPEED_DEAD_ZONE - SPEED_HALF_SPAN * ln(stretch) / ln(MAX_STRETCH)
            stretch < 1.0 -> CENTRE + SPEED_DEAD_ZONE + SPEED_HALF_SPAN * ln(stretch) / ln(MIN_STRETCH)
            else -> CENTRE.toDouble()
        }
        return position.roundToInt().coerceIn(0, SLIDER_MAX)
    }

    /**
     * With [snapToSemitones], the slider picks whole semitones and keeps the cents of [currentSemitones],
     * so a fine-tuned track stays fine-tuned.
     */
    fun pitchSemitones(position: Int, snapToSemitones: Boolean, currentSemitones: Float = 0f): Float {
        val value = linear(position, PITCH_DEAD_ZONE) * PITCH_RANGE
        if (!snapToSemitones) return value.toFloat()
        val cents = currentSemitones - truncate(currentSemitones)
        return (value.roundToInt() + cents).coerceIn(-PITCH_RANGE.toFloat(), PITCH_RANGE.toFloat())
    }

    fun pitchPosition(semitones: Float): Int = position(semitones / PITCH_RANGE, PITCH_DEAD_ZONE)

    /** 0 = left channel only, 0.5 = both unchanged, 1 = right channel only. */
    fun mixRight(position: Int): Float = ((linear(position, MIX_DEAD_ZONE) + 1) / 2).toFloat()

    fun mixPosition(mixRight: Float): Int = position(mixRight * 2.0 - 1, MIX_DEAD_ZONE)

    /** -1..1, flat at 0 inside the dead zone. */
    private fun linear(position: Int, deadZone: Int): Double {
        val p = position.coerceIn(0, SLIDER_MAX)
        val span = (CENTRE - deadZone).toDouble()
        return when {
            p < CENTRE - deadZone -> -1 + p / span
            p > CENTRE + deadZone -> (p - CENTRE - deadZone) / span
            else -> 0.0
        }
    }

    private fun position(value: Double, deadZone: Int): Int {
        val v = value.coerceIn(-1.0, 1.0)
        val span = CENTRE - deadZone
        val position = when {
            v < 0 -> (v + 1) * span
            v > 0 -> CENTRE + deadZone + v * span
            else -> CENTRE.toDouble()
        }
        return position.roundToInt().coerceIn(0, SLIDER_MAX)
    }
}
