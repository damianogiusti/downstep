package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.x AA for every theme in both modes, over the pairs the UI draws: text at 4.5:1, the accents
 * and loop colours as control colours (sliders, A/B lines, chips' dots, waveform regions) at 3:1, and
 * Material's roles as toMaterial maps them. Downstep passes as the design has it, without exemptions.
 */
class ThemeContrastTest {

    @Test
    fun `text meets AA on every surface it sits on`() = assertAll { colors, _ ->
        buildList {
            for (ground in listOf("bg" to colors.bg, "bgDeep" to colors.bgDeep, "surface1" to colors.surface1, "surface2" to colors.surface2)) {
                add(Check("fg", colors.fg, ground, TEXT))
                add(Check("fg2", colors.fg2, ground, TEXT))
            }
            // Material's secondaryContainer (tonal buttons, the selected segment) and the chips' borders.
            add(Check("fg", colors.fg, "surface3" to colors.surface3, TEXT))
            add(Check("onAmber", colors.onAmber, "amber" to colors.amber, TEXT))
            add(Check("onTeal", colors.onTeal, "teal" to colors.teal, TEXT))
            add(Check("loopOnFg", colors.loopOnFg, "loopOnBg" to colors.loopOnBg, TEXT))
            // The active loop chip, the library's selected tab and the theme menu's chosen mode: fg filled.
            add(Check("chipFgActive", colors.chipFgActive, "fg" to colors.fg, TEXT))
            add(Check("errFg", colors.errFg, "errBg" to colors.errBg, TEXT))
        }
    }

    @Test
    fun `accents and loop colours stand out as controls`() = assertAll { colors, _ ->
        val controls = listOf("amber" to colors.amber, "teal" to colors.teal) +
            colors.loopColors.mapIndexed { i, color -> "loop$i" to color }
        buildList {
            for ((name, color) in controls) {
                for (ground in listOf("bg" to colors.bg, "bgDeep" to colors.bgDeep, "surface1" to colors.surface1)) {
                    add(Check(name, color, ground, CONTROL))
                }
            }
            // The check of the chosen theme, in the menu.
            add(Check("amber", colors.amber, "surface2" to colors.surface2, CONTROL))
        }
    }

    @Test
    fun `Material roles meet AA`() = assertAll { _, material ->
        listOf(
            Check("onPrimary", material.onPrimary, "primary" to material.primary, TEXT),
            Check("onPrimaryContainer", material.onPrimaryContainer, "primaryContainer" to material.primaryContainer, TEXT),
            Check("onSecondary", material.onSecondary, "secondary" to material.secondary, TEXT),
            Check("onSecondaryContainer", material.onSecondaryContainer, "secondaryContainer" to material.secondaryContainer, TEXT),
            Check("onTertiary", material.onTertiary, "tertiary" to material.tertiary, TEXT),
            Check("onTertiaryContainer", material.onTertiaryContainer, "tertiaryContainer" to material.tertiaryContainer, TEXT),
            Check("onSurface", material.onSurface, "surface" to material.surface, TEXT),
            Check("onSurfaceVariant", material.onSurfaceVariant, "surfaceVariant" to material.surfaceVariant, TEXT),
            Check("onBackground", material.onBackground, "background" to material.background, TEXT),
            Check("onError", material.onError, "error" to material.error, TEXT),
            Check("onErrorContainer", material.onErrorContainer, "errorContainer" to material.errorContainer, TEXT),
            Check("error", material.error, "background" to material.background, TEXT),
            Check("inverseOnSurface", material.inverseOnSurface, "inverseSurface" to material.inverseSurface, TEXT),
        )
    }

    @Test
    fun `the four loop colours stay apart`() {
        // Downstep's light amber and rust (0.043 in OKLab) are the closest pair the design has; no theme
        // comes closer.
        val failures = forEachTheme { colors, _ ->
            val loops = colors.loopColors
            buildList {
                for (i in loops.indices) for (j in i + 1 until loops.size) {
                    val distance = oklabDistance(loops[i], loops[j])
                    if (distance < MIN_LOOP_DISTANCE) add("loop$i/loop$j %.3f".format(distance))
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `the WCAG formula gives the known extremes`() {
        assertTrue(kotlin.math.abs(contrast(Color.Black, Color.White) - 21.0) < 0.01)
        assertTrue(kotlin.math.abs(contrast(Color(0xFF777777), Color.White) - 4.48) < 0.01)
    }

    private class Check(val fgName: String, val fg: Color, val ground: Pair<String, Color>, val min: Double)

    private fun assertAll(pairs: (DownstepColors, ColorScheme) -> List<Check>) {
        val failures = forEachTheme { colors, material ->
            pairs(colors, material).mapNotNull { pair ->
                val ratio = contrast(pair.fg, pair.ground.second)
                if (ratio + 1e-9 >= pair.min) null else "${pair.fgName} on ${pair.ground.first}: %.2f < %.1f".format(ratio, pair.min)
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun forEachTheme(check: (DownstepColors, ColorScheme) -> List<String>): List<String> =
        Themes.flatMap { theme ->
            ThemeMode.entries.flatMap { mode ->
                check(theme.colors(mode), theme.material(mode)).map { "${theme.id} ${mode.name}: $it" }
            }
        }

    private companion object {
        const val TEXT = 4.5
        const val CONTROL = 3.0
        const val MIN_LOOP_DISTANCE = 0.04

        // WCAG 2.x relative luminance, sRGB with its 0.03928 threshold; alpha ignored (all opaque).
        fun luminance(color: Color): Double {
            fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }

        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }

        fun oklabDistance(a: Color, b: Color): Double {
            val x = oklab(a)
            val y = oklab(b)
            return sqrt((x[0] - y[0]).pow(2) + (x[1] - y[1]).pow(2) + (x[2] - y[2]).pow(2))
        }

        private fun oklab(color: Color): DoubleArray {
            fun linear(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            val r = linear(color.red)
            val g = linear(color.green)
            val b = linear(color.blue)
            val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            return doubleArrayOf(
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
            )
        }
    }
}
