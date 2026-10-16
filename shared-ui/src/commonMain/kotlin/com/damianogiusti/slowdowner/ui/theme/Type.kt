package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.ui.resources.*
import org.jetbrains.compose.resources.Font

// Bundled, not downloadable: the app must look the same offline. Resource fonts load in composition.
@Composable
private fun spaceGrotesk() = FontFamily(
    Font(Res.font.space_grotesk_medium, FontWeight.Medium),
    Font(Res.font.space_grotesk_bold, FontWeight.Bold),
)

@Composable
private fun inter() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
)

private fun display(family: FontFamily, size: Int, lineHeight: Int, weight: FontWeight, tracking: Double = 0.0) =
    style(family, size, lineHeight, weight, tracking)

private fun text(family: FontFamily, size: Int, lineHeight: Int, weight: FontWeight, tracking: Double) =
    style(family, size, lineHeight, weight, tracking)

private fun style(family: FontFamily, size: Int, lineHeight: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

@Composable
internal fun slowDownerTypography(): Typography {
    val displayFamily = spaceGrotesk()
    val textFamily = inter()
    return remember(displayFamily, textFamily) { typography(displayFamily, textFamily) }
}

private fun typography(displayFamily: FontFamily, textFamily: FontFamily) = Typography(
    displayLarge = display(displayFamily, 57, 64, FontWeight.Bold, -0.5),
    displayMedium = display(displayFamily, 44, 52, FontWeight.Bold, -0.5),
    displaySmall = display(displayFamily, 36, 44, FontWeight.Bold, -0.25),
    headlineLarge = display(displayFamily, 32, 40, FontWeight.Bold, -0.25),
    headlineMedium = display(displayFamily, 28, 36, FontWeight.Bold),
    headlineSmall = display(displayFamily, 24, 32, FontWeight.Medium),
    titleLarge = display(displayFamily, 22, 28, FontWeight.Medium),
    titleMedium = text(textFamily, 16, 24, FontWeight.SemiBold, 0.1),
    titleSmall = text(textFamily, 14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = text(textFamily, 16, 24, FontWeight.Normal, 0.2),
    bodyMedium = text(textFamily, 14, 20, FontWeight.Normal, 0.2),
    bodySmall = text(textFamily, 12, 16, FontWeight.Normal, 0.3),
    labelLarge = text(textFamily, 14, 20, FontWeight.Medium, 0.1),
    labelMedium = text(textFamily, 12, 16, FontWeight.Medium, 0.4),
    labelSmall = text(textFamily, 11, 16, FontWeight.Medium, 0.5),
)

/** Fixed-width digits, so speed, pitch and time readouts don't shift while they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * The player's type scale (README §1 "Type"), read through [SlowDownerTheme.type]. Space Grotesk 700
 * for the title and big read-outs, Inter for everything else. All tabular: timecodes and percentages
 * never jitter.
 */
@Immutable
class DownstepType internal constructor(
    /** Track title in the header: 26/30, −0.3 tracking. */
    val title: TextStyle,
    /** Compact title in focus mode: 20. */
    val focusTitle: TextStyle,
    /** Library drawer title: 24. */
    val drawerTitle: TextStyle,
    /** Speed and pitch cards: 34/38. */
    val readout: TextStyle,
    /** Speed and pitch beside the transport in focus mode: 18. */
    val focusReadout: TextStyle,
    /** Buttons, pills, chips: Inter 500, 13. */
    val control: TextStyle,
    /** Chip names, emphasised controls: Inter 600, 13. */
    val controlStrong: TextStyle,
    /** Drawer row titles, notes text, progression sentence: Inter 400, 14. */
    val body: TextStyle,
    /** Meta lines, hints: Inter 400, 12. */
    val meta: TextStyle,
    /** Uppercase section labels (SPEED, PITCH, NOTES · Solo): Inter 500, 11, 0.6 tracking. */
    val sectionLabel: TextStyle,
    /** A/B and playhead timecode pills, small waveform labels: Inter 600, 10–11. */
    val tag: TextStyle,
)

private fun downstepType(displayFamily: FontFamily, textFamily: FontFamily) = DownstepType(
    title = style(displayFamily, 26, 30, FontWeight.Bold, -0.3).tabular(),
    focusTitle = style(displayFamily, 20, 26, FontWeight.Bold, -0.2).tabular(),
    drawerTitle = style(displayFamily, 24, 30, FontWeight.Bold, 0.0).tabular(),
    readout = style(displayFamily, 34, 38, FontWeight.Bold, 0.0).tabular(),
    focusReadout = style(displayFamily, 18, 22, FontWeight.Bold, 0.0).tabular(),
    control = style(textFamily, 13, 18, FontWeight.Medium, 0.0).tabular(),
    controlStrong = style(textFamily, 13, 18, FontWeight.SemiBold, 0.0).tabular(),
    body = style(textFamily, 14, 20, FontWeight.Normal, 0.0).tabular(),
    meta = style(textFamily, 12, 16, FontWeight.Normal, 0.0).tabular(),
    sectionLabel = style(textFamily, 11, 14, FontWeight.Medium, 0.6).tabular(),
    tag = style(textFamily, 11, 14, FontWeight.SemiBold, 0.0).tabular(),
)

@Composable
internal fun rememberDownstepType(): DownstepType {
    val displayFamily = spaceGrotesk()
    val textFamily = inter()
    return remember(displayFamily, textFamily) { downstepType(displayFamily, textFamily) }
}

internal val LocalDownstepType = staticCompositionLocalOf<DownstepType> { error("DownstepType outside SlowDownerTheme") }
