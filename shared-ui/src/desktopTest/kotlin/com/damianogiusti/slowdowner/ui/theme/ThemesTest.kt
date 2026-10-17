package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.ui.graphics.Color
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.domain.theme.ThemeSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThemesTest {

    @Test
    fun `the ids are unique and saved ones never change`() {
        assertEquals(
            listOf("downstep", "graphite", "midnight", "forest", "rosewood", "dusk", "sandstone", "high-contrast"),
            Themes.map { it.id },
        )
    }

    @Test
    fun `Downstep is the design's palette and the first theme`() {
        assertSame(DownstepTheme, Themes.first())
        assertEquals(ThemeSelection.DEFAULT_THEME_ID, DownstepTheme.id)
        assertSame(DarkTokens, DownstepTheme.colors(ThemeMode.Dark))
        assertSame(LightTokens, DownstepTheme.colors(ThemeMode.Light))
    }

    @Test
    fun `Downstep keeps the design's accents but for the light ones darkened to AA`() {
        // Dark: palette.json as it is.
        assertEquals(Color(0xFFF2A541), DarkTokens.amber)
        assertEquals(Color(0xFFFFB85A), DarkTokens.amberHover)
        assertEquals(Color(0xFF62B6B0), DarkTokens.teal)
        // Light: palette.json's A86400, 8F5500 and 2D7C77 were not AA as small text (ThemeContrastTest).
        assertEquals(Color(0xFF935100), LightTokens.amber)
        assertEquals(Color(0xFF7B4200), LightTokens.amberHover)
        assertEquals(Color(0xFF23746F), LightTokens.teal)
        assertEquals(listOf(Color(0xFF935100), Color(0xFF23746F), Color(0xFFB0533A), Color(0xFF3F7A5A)), LightTokens.loopColors)
        assertEquals(Color(0xFF935100).copy(alpha = 0.28f), LightTokens.amberGlow)
    }

    @Test
    fun `an unknown id falls back to Downstep`() {
        assertSame(DownstepTheme, themeById("from-a-newer-version"))
        assertSame(DownstepTheme, themeById(""))
        assertEquals("forest", themeById("forest").id)
    }

    @Test
    fun `every palette has the mode it is used in and four loop colours`() {
        for (theme in Themes) {
            assertTrue(theme.dark.isDark, theme.id)
            assertTrue(!theme.light.isDark, theme.id)
            assertEquals(4, theme.dark.loopColors.size, theme.id)
            assertEquals(4, theme.light.loopColors.size, theme.id)
            // Loop 0 and 1 are the primary and the secondary, as in the design (A amber, B teal).
            for (colors in listOf(theme.dark, theme.light)) {
                assertEquals(colors.amber, colors.loopColors[0], theme.id)
                assertEquals(colors.teal, colors.loopColors[1], theme.id)
            }
        }
    }

    @Test
    fun `Downstep's Material roles are those before themes`() {
        val dark = DownstepTheme.material(ThemeMode.Dark)
        assertEquals(Color(0xFFF2A541), dark.primary)
        assertEquals(Color(0xFFE07A5F), dark.secondary)
        assertEquals(Color(0xFF3A0F04), dark.onSecondary)
        assertEquals(Color(0xFF17403D), dark.tertiaryContainer)
        assertEquals(Color(0xFFCDEEEA), dark.onTertiaryContainer)
        assertEquals(Color(0xFFFF6B6B), dark.error)
        assertEquals(Color(0xFF410002), dark.onError)
        assertEquals(Color(0xFFFFDAD6), dark.onErrorContainer)
        assertEquals(Color(0xFF935100), dark.inversePrimary)
        assertEquals(Color(0xFF3B424C), dark.surfaceBright)
        assertEquals(Color(0xFF0B0D10), dark.surfaceContainerLowest)

        val light = DownstepTheme.material(ThemeMode.Light)
        assertEquals(Color(0xFF935100), light.primary)
        assertEquals(Color.White, light.onSecondary)
        assertEquals(Color(0xFFCDEEEA), light.tertiaryContainer)
        assertEquals(Color(0xFF00201E), light.onTertiaryContainer)
        assertEquals(Color(0xFFBA1A1A), light.error)
        assertEquals(Color.White, light.onError)
        assertEquals(Color(0xFF410002), light.onErrorContainer)
        assertEquals(Color(0xFFF2A541), light.inversePrimary)
        assertEquals(Color.White, light.surfaceBright)
        assertEquals(Color(0xFFE6DED1), light.surfaceDim)
    }

    @Test
    fun `another theme's Material roles come from its own palette`() {
        val forest = themeById("forest")
        val material = forest.material(ThemeMode.Light)
        assertEquals(forest.light.amber, material.primary)
        assertEquals(forest.dark.amber, material.inversePrimary)
        assertEquals(forest.light.tealContainer, material.tertiaryContainer)
        assertEquals(forest.light.bg, material.background)
    }
}
