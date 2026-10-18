package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.x AA for every theme in both modes, over the pairs the UI draws: small text at 4.5:1, large
 * text (the 34sp and 26sp bold readouts) at 3:1, the accents and loop colours as control marks (sliders,
 * A/B lines, chips' dots, waveform regions) at 3:1, and Material's roles as toMaterial maps them. A
 * colour drawn with alpha is composited over its actual ground first. Downstep passes without exemptions:
 * its dark palette as the design has it, its light amber, amberHover and teal darkened for it (Color.kt).
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
            // The loop chips' and the presets' count badges, idle.
            add(Check("fg2", colors.fg2, "surface3" to colors.surface3, TEXT))
            add(Check("onAmber", colors.onAmber, "amber" to colors.amber, TEXT))
            // The library's Import button, hovered.
            add(Check("onAmber", colors.onAmber, "amberHover" to colors.amberHover, TEXT))
            add(Check("onTeal", colors.onTeal, "teal" to colors.teal, TEXT))
            add(Check("loopOnFg", colors.loopOnFg, "loopOnBg" to colors.loopOnBg, TEXT))
            // The active loop chip, the library's selected tab and the theme menu's chosen mode: fg filled.
            add(Check("chipFgActive", colors.chipFgActive, "fg" to colors.fg, TEXT))
            add(Check("errFg", colors.errFg, "errBg" to colors.errBg, TEXT))
            // A loop chip's length, dimmed, on the idle chip and on the active (fg filled) one.
            add(Check("fg ${LOOP_LENGTH_ALPHA}α", colors.fg.copy(alpha = LOOP_LENGTH_ALPHA), "surface1" to colors.surface1, TEXT))
            add(Check("chipFgActive ${LOOP_LENGTH_ALPHA}α", colors.chipFgActive.copy(alpha = LOOP_LENGTH_ALPHA), "fg" to colors.fg, TEXT))
        }
    }

    @Test
    fun `accent text meets AA where it is drawn`() = assertAll { colors, _ ->
        listOf(
            // The suggestion chips (name and length), the progression steppers' values.
            Check("amber", colors.amber, "bg" to colors.bg, TEXT),
            // The overview's 10sp suggestion labels.
            Check("amber", colors.amber, "bgDeep" to colors.bgDeep, TEXT),
            // The header menus' chosen count-in and active preset.
            Check("amber", colors.amber, "surface2" to colors.surface2, TEXT),
            // The library's current track, on its tinted row in the drawer.
            Check("amber", colors.amber, "nowPlayingRow on surface1" to colors.nowPlayingRow.over(colors.surface1), TEXT),
            // Analyze while it runs: amber on its faint amber fill.
            Check("amber", colors.amber, "analyze fill on bg" to colors.amber.copy(alpha = ANALYZE_FILL_ALPHA).over(colors.bg), TEXT),
        )
    }

    @Test
    fun `the speed and pitch readouts meet AA as large text`() = assertAll { colors, _ ->
        // 34sp bold (26sp compact): large text, on the cards' surface1.
        listOf(
            Check("amber", colors.amber, "surface1" to colors.surface1, LARGE_TEXT),
            Check("teal", colors.teal, "surface1" to colors.surface1, LARGE_TEXT),
        )
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
            // The check of the chosen theme and the click volume slider, in the menus.
            add(Check("amber", colors.amber, "surface2" to colors.surface2, CONTROL))
            // The active loop chip's dot: the loop colour in a chipFgActive ring, on fg.
            for ((name, color) in controls.drop(2)) {
                add(Check(name, color, "chipFgActive ring" to colors.chipFgActive, CONTROL))
            }
            add(Check("chipFgActive ring", colors.chipFgActive, "fg" to colors.fg, CONTROL))
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
    fun `Material accents meet AA on the surfaces of dialogs, sheets and menus`() = assertAll { colors, material ->
        val grounds = listOf(
            "surface" to material.surface,
            // Bottom sheets (import, effects, add to playlist).
            "surfaceContainerLow" to material.surfaceContainerLow,
            // Menus, and the effects sheet's EQ box.
            "surfaceContainer" to material.surfaceContainer,
            // AlertDialog.
            "surfaceContainerHigh" to material.surfaceContainerHigh,
        )
        buildList {
            // The EQ readout and the preamp value, in the effects sheet's EQ box.
            add(Check("tertiary", material.tertiary, "surfaceContainer" to material.surfaceContainer, TEXT))
            for (ground in grounds) {
                // TextButtons, "New playlist", the practice badge; the update dialog's error.
                add(Check("primary", material.primary, ground, TEXT))
                add(Check("error", material.error, ground, TEXT))
                // Switches, sliders, the radio and check marks.
                add(Check("primary", material.primary, ground, CONTROL))
                add(Check("tertiary", material.tertiary, ground, CONTROL))
            }
            // The playlists' practice badge: primary on its primaryTint.
            add(Check("primary", material.primary, "primaryTint on surface" to colors.primaryTint.over(material.surface), TEXT))
        }
    }

    @Test
    fun `the four loop colours stay apart`() {
        // Downstep's light teal and green (0.047 in OKLab) are the closest pair the design has; no theme
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

    @Test
    fun `a colour with alpha composites over its ground`() {
        val half = Color.White.copy(alpha = 0.5f).over(Color.Black)
        assertTrue(half.alpha == 1f && kotlin.math.abs(half.red - 128 / 255f) < 1e-6, "$half")
    }

    private class Check(val fgName: String, val fg: Color, val ground: Pair<String, Color>, val min: Double)

    private fun assertAll(pairs: (DownstepColors, ColorScheme) -> List<Check>) {
        val failures = forEachTheme { colors, material ->
            pairs(colors, material).mapNotNull { pair ->
                val ratio = contrast(pair.fg.over(pair.ground.second), pair.ground.second)
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
        /** 18.66px bold or 24px and up. */
        const val LARGE_TEXT = 3.0
        const val CONTROL = 3.0
        const val MIN_LOOP_DISTANCE = 0.04

        // The alphas the components draw with (private there): LoopChipsStrip's LENGTH_ALPHA and
        // PlayerHeader's RUNNING_FILL_ALPHA.
        const val LOOP_LENGTH_ALPHA = 0.65f
        const val ANALYZE_FILL_ALPHA = 0.13f

        /**
         * This colour drawn over an opaque [ground], as Skia blends it: in sRGB, rounded to the 8 bits
         * of the surface. Opaque colours come back as they are.
         */
        fun Color.over(ground: Color): Color {
            require(ground.alpha == 1f) { "the ground must be opaque: $ground" }
            if (alpha == 1f) return this
            fun channel(top: Float, bottom: Float) = (alpha * top + (1 - alpha) * bottom).times(255).roundToInt() / 255f
            return Color(channel(red, ground.red), channel(green, ground.green), channel(blue, ground.blue))
        }

        // WCAG 2.x relative luminance, sRGB with its 0.03928 threshold; opaque colours only (see over).
        fun luminance(color: Color): Double {
            require(color.alpha == 1f) { "composite $color over its ground first" }
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
