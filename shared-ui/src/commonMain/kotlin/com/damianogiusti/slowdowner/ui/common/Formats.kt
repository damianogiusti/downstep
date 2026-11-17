package com.damianogiusti.slowdowner.ui.common

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** "1:05" — minutes and seconds, for practice readouts. */
fun formatClock(seconds: Float): String {
    val total = seconds.roundToLong().coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * "2.4": one decimal. Compose Resources only fills `%1$s` and `%1$d`, so decimals are formatted
 * here, not in strings.xml. [separator] comes from `decimal_separator`, so Italian gets "2,4".
 */
fun formatDecimal(value: Float, separator: String = "."): String {
    val tenths = abs((value * 10).roundToInt())
    val sign = if ((value * 10).roundToInt() < 0) "−" else ""
    return "$sign${tenths / 10}$separator${tenths % 10}"
}

/** "+2.5", "−1.0", "0.0": like [formatDecimal], with a sign for gains. */
fun formatSignedDecimal(value: Float, separator: String = "."): String {
    val text = formatDecimal(value, separator)
    return if ((value * 10).roundToInt() > 0) "+$text" else text
}

/** "75%" */
fun formatSpeed(percent: Float): String = "${percent.roundToInt()}%"

/**
 * "−2 st", "0 st", "+3 st", with a real minus sign. With cents, two decimals: "+2.15 st", "−0.30 st".
 * [separator] comes from `decimal_separator`.
 */
fun formatPitch(semitones: Float, separator: String = "."): String {
    val hundredths = (semitones * 100).roundToInt()
    val magnitude = abs(hundredths)
    val sign = if (hundredths < 0) "−" else "+"
    return when {
        hundredths == 0 -> "0 st"
        magnitude % 100 == 0 -> "$sign${magnitude / 100} st"
        else -> "$sign${magnitude / 100}$separator${(magnitude % 100).toString().padStart(2, '0')} st"
    }
}

private const val PITCH_SHOWN_FROM = 0.005f

/** Short summary for a badge or a preset name, e.g. "75%, −2 st, Loop". Null when all default. */
@Composable
fun practiceSummary(settings: PracticeSettings): String? {
    val parts = buildList {
        if (settings.speedPercent.roundToInt() != 100) add(formatSpeed(settings.speedPercent))
        if (abs(settings.pitchSemitones) >= PITCH_SHOWN_FROM) {
            add(formatPitch(settings.pitchSemitones, stringResource(Res.string.decimal_separator)))
        }
        if (settings.loop.isOn) add(stringResource(Res.string.loop))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
