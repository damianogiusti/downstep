package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
