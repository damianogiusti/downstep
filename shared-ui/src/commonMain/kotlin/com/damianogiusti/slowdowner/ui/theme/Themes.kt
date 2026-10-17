package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.domain.theme.ThemeSelection
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.theme_downstep
import com.damianogiusti.slowdowner.ui.resources.theme_dusk
import com.damianogiusti.slowdowner.ui.resources.theme_forest
import com.damianogiusti.slowdowner.ui.resources.theme_graphite
import com.damianogiusti.slowdowner.ui.resources.theme_high_contrast
import com.damianogiusti.slowdowner.ui.resources.theme_midnight
import com.damianogiusti.slowdowner.ui.resources.theme_rosewood
import com.damianogiusti.slowdowner.ui.resources.theme_sandstone
import org.jetbrains.compose.resources.StringResource

/**
 * A palette in both modes, picked in the header's theme menu. Every theme keeps the design's meaning
 * of the colours: a warm primary ([DownstepColors.amber]: speed, A, play, loop on), a cool secondary
 * ([DownstepColors.teal]: pitch, B, isolate), four loop colours apart from each other, the surface
 * scale bg/bgDeep/surface1..4 and fg/fg2, all at WCAG AA (ThemeContrastTest).
 *
 * [id] is what is saved (ThemeSelection.themeId): never change one.
 */
@Immutable
class AppTheme(
    val id: String,
    val name: StringResource,
    val dark: DownstepColors,
    val light: DownstepColors,
) {
    fun colors(mode: ThemeMode): DownstepColors = if (mode == ThemeMode.Light) light else dark

    /** The Material roles of [mode]; inversePrimary is the other mode's primary. */
    internal fun material(mode: ThemeMode): ColorScheme =
        colors(mode).toMaterial(inversePrimary = (if (mode == ThemeMode.Light) dark else light).amber)
}

/** The palette of the design (Color.kt), the default and the fallback. */
val DownstepTheme = AppTheme(
    id = ThemeSelection.DEFAULT_THEME_ID,
    name = Res.string.theme_downstep,
    dark = DarkTokens,
    light = LightTokens,
)

// The other themes: a surface hue and four accent hues each, with the lightness of every token tuned
// until ThemeContrastTest passes with a margin (text 4.6:1, controls 3.2:1, loopOn and on-accent 7:1).

/** Neutral greys: only the accents and loops carry colour. */
private val GraphiteTheme = AppTheme(
    id = "graphite",
    name = Res.string.theme_graphite,
    dark = darkPalette(
        bg = 0xFF0F1113, bgDeep = 0xFF090B0E, surface1 = 0xFF181A1C,
        surface2 = 0xFF1D1F22, surface3 = 0xFF282A2D, surface4 = 0xFF37393C,
        fg = 0xFFE6EAEF, fg2 = 0xFFA5ABB4, barMuted = 0xFF2B2D30,
        amber = 0xFFECAE30, amberHover = 0xFFFDBE45, onAmber = 0xFF271700,
        teal = 0xFF4DB4CC, onTeal = 0xFF001A21, tealContainer = 0xFF123E48, onTealContainer = 0xFFC8ECF5,
        loopOnBg = 0xFF533B0A, loopOnFg = 0xFFF7DEB5,
        loop3 = 0xFFE47C75, onLoop3 = 0xFF1F0001, loop4 = 0xFF8DCA80,
    ),
    light = lightPalette(
        bg = 0xFFF4F7FB, bgDeep = 0xFFEDF0F4, surface1 = 0xFFE5E8EC,
        surface2 = 0xFFDDE0E4, surface3 = 0xFFD3D6DA, surface4 = 0xFFC9CCD0,
        fg = 0xFF1D1F23, fg2 = 0xFF565C64, barMuted = 0xFFC9CCD0,
        amber = 0xFF865500, amberHover = 0xFF764800, onAmber = 0xFFFFFFFF,
        teal = 0xFF117083, onTeal = 0xFFFFFFFF, tealContainer = 0xFFC8ECF5, onTealContainer = 0xFF05343D,
        loopOnBg = 0xFFFAE1B8, loopOnFg = 0xFF45310B,
        loop3 = 0xFFAF5B56, onLoop3 = 0xFFFFFFFF, loop4 = 0xFF58894E,
    ),
)

/** Deep navy, an orange primary and a sea-green secondary. */
private val MidnightTheme = AppTheme(
    id = "midnight",
    name = Res.string.theme_midnight,
    dark = darkPalette(
        bg = 0xFF071020, bgDeep = 0xFF040B1A, surface1 = 0xFF101A2A,
        surface2 = 0xFF151F30, surface3 = 0xFF202A3C, surface4 = 0xFF2F3A4C,
        fg = 0xFFE6EAEF, fg2 = 0xFFA5ABB5, barMuted = 0xFF222D3E,
        amber = 0xFFFCA443, amberHover = 0xFFFFB455, onAmber = 0xFF2A1500,
        teal = 0xFF36BABA, onTeal = 0xFF001B1C, tealContainer = 0xFF104040, onTealContainer = 0xFFC7EDED,
        loopOnBg = 0xFF593712, loopOnFg = 0xFFFEDAB8,
        loop3 = 0xFFE97292, onLoop3 = 0xFF160004, loop4 = 0xFFA3C75D,
    ),
    light = lightPalette(
        bg = 0xFFF1F7FF, bgDeep = 0xFFEAF1FB, surface1 = 0xFFE2E8F2,
        surface2 = 0xFFDAE0EA, surface3 = 0xFFD0D6E0, surface4 = 0xFFC6CCD6,
        fg = 0xFF1C2023, fg2 = 0xFF575C65, barMuted = 0xFFC6CCD6,
        amber = 0xFF924E00, amberHover = 0xFF824000, onAmber = 0xFFFFFFFF,
        teal = 0xFF007373, onTeal = 0xFFFFFFFF, tealContainer = 0xFFC7EDED, onTealContainer = 0xFF023536,
        loopOnBg = 0xFFFFDDBB, loopOnFg = 0xFF4A2E10,
        loop3 = 0xFFB5556F, onLoop3 = 0xFFFFFFFF, loop4 = 0xFF6A862E,
    ),
)

/** Green-tinted surfaces, honey and sky accents, a lilac fourth loop. */
private val ForestTheme = AppTheme(
    id = "forest",
    name = Res.string.theme_forest,
    dark = darkPalette(
        bg = 0xFF08130C, bgDeep = 0xFF040E07, surface1 = 0xFF111D15,
        surface2 = 0xFF17221B, surface3 = 0xFF222D25, surface4 = 0xFF303D34,
        fg = 0xFFE9EAE4, fg2 = 0xFFA4AEA7, barMuted = 0xFF243028,
        amber = 0xFFE7B140, amberHover = 0xFFF8C153, onAmber = 0xFF261800,
        teal = 0xFF56B2D4, onTeal = 0xFF001924, tealContainer = 0xFF163D4B, onTealContainer = 0xFFC9EBF9,
        loopOnBg = 0xFF523B0A, loopOnFg = 0xFFF5DEB5,
        loop3 = 0xFFE77B60, onLoop3 = 0xFF1C0000, loop4 = 0xFFCDA4EC,
    ),
    light = lightPalette(
        bg = 0xFFF0FAF3, bgDeep = 0xFFE9F3EC, surface1 = 0xFFE1EBE4,
        surface2 = 0xFFD9E3DC, surface3 = 0xFFCFD9D2, surface4 = 0xFFC5CFC8,
        fg = 0xFF1F201C, fg2 = 0xFF555E58, barMuted = 0xFFC5CFC8,
        amber = 0xFF825800, amberHover = 0xFF734900, onAmber = 0xFFFFFFFF,
        teal = 0xFF206F8A, onTeal = 0xFFFFFFFF, tealContainer = 0xFFC9EBF9, onTealContainer = 0xFF0A3341,
        loopOnBg = 0xFFF9E1B8, loopOnFg = 0xFF45320A,
        loop3 = 0xFFB45C46, onLoop3 = 0xFFFFFFFF, loop4 = 0xFF8B6AA4,
    ),
)

/** Warm red-brown surfaces with an apricot primary and a rose third loop. */
private val RosewoodTheme = AppTheme(
    id = "rosewood",
    name = Res.string.theme_rosewood,
    dark = darkPalette(
        bg = 0xFF190D0C, bgDeep = 0xFF130707, surface1 = 0xFF231515,
        surface2 = 0xFF291B1B, surface3 = 0xFF342626, surface4 = 0xFF443534,
        fg = 0xFFEEE8E5, fg2 = 0xFFB4A7A7, barMuted = 0xFF372828,
        amber = 0xFFF5A939, amberHover = 0xFFFFB94D, onAmber = 0xFF291600,
        teal = 0xFF4AB9B2, onTeal = 0xFF001C1A, tealContainer = 0xFF11403E, onTealContainer = 0xFFC7EEEA,
        loopOnBg = 0xFF56390E, loopOnFg = 0xFFFBDCB6,
        loop3 = 0xFFE177A3, onLoop3 = 0xFF1A000A, loop4 = 0xFFADC367,
    ),
    light = lightPalette(
        bg = 0xFFFFF3F3, bgDeep = 0xFFFAEDEC, surface1 = 0xFFF1E4E4,
        surface2 = 0xFFE9DCDC, surface3 = 0xFFDFD2D2, surface4 = 0xFFD5C9C8,
        fg = 0xFF231E1C, fg2 = 0xFF635857, barMuted = 0xFFD5C9C8,
        amber = 0xFF8C5100, amberHover = 0xFF7C4300, onAmber = 0xFFFFFFFF,
        teal = 0xFF07726E, onTeal = 0xFFFFFFFF, tealContainer = 0xFFC7EEEA, onTealContainer = 0xFF033633,
        loopOnBg = 0xFFFEDFB9, loopOnFg = 0xFF48300D,
        loop3 = 0xFFAE587C, onLoop3 = 0xFFFFFFFF, loop4 = 0xFF738338,
    ),
)

/** Plum surfaces, peach and aqua accents. */
private val DuskTheme = AppTheme(
    id = "dusk",
    name = Res.string.theme_dusk,
    dark = darkPalette(
        bg = 0xFF140D1C, bgDeep = 0xFF0E0716, surface1 = 0xFF1D1625,
        surface2 = 0xFF231B2C, surface3 = 0xFF2E2637, surface4 = 0xFF3D3547,
        fg = 0xFFECE8ED, fg2 = 0xFFADA9B3, barMuted = 0xFF30293A,
        amber = 0xFFF5A84B, amberHover = 0xFFFFB85D, onAmber = 0xFF291500,
        teal = 0xFF49B8B8, onTeal = 0xFF001B1C, tealContainer = 0xFF104040, onTealContainer = 0xFFC7EDED,
        loopOnBg = 0xFF583810, loopOnFg = 0xFFFDDBB7,
        loop3 = 0xFFE87782, onLoop3 = 0xFF1C0003, loop4 = 0xFF89CC7B,
    ),
    light = lightPalette(
        bg = 0xFFF9F4FF, bgDeep = 0xFFF2EEF9, surface1 = 0xFFEAE5F1,
        surface2 = 0xFFE2DDE8, surface3 = 0xFFD8D3DF, surface4 = 0xFFCECAD5,
        fg = 0xFF211E22, fg2 = 0xFF5D5962, barMuted = 0xFFCECAD5,
        amber = 0xFF8C5100, amberHover = 0xFF7D4300, onAmber = 0xFFFFFFFF,
        teal = 0xFF017272, onTeal = 0xFFFFFFFF, tealContainer = 0xFFC7EDED, onTealContainer = 0xFF023536,
        loopOnBg = 0xFFFFDEBA, loopOnFg = 0xFF492F0E,
        loop3 = 0xFFB45862, onLoop3 = 0xFFFFFFFF, loop4 = 0xFF558A49,
    ),
)

/** Coffee and sand surfaces, a burnt-orange primary. */
private val SandstoneTheme = AppTheme(
    id = "sandstone",
    name = Res.string.theme_sandstone,
    dark = darkPalette(
        bg = 0xFF180E05, bgDeep = 0xFF120902, surface1 = 0xFF21170C,
        surface2 = 0xFF271D12, surface3 = 0xFF32281D, surface4 = 0xFF42372B,
        fg = 0xFFEDE9E4, fg2 = 0xFFB1A9A1, barMuted = 0xFF352B1F,
        amber = 0xFFFF9E53, amberHover = 0xFFFFAD62, onAmber = 0xFF2C1302,
        teal = 0xFF51B3D0, onTeal = 0xFF001A22, tealContainer = 0xFF143E4A, onTealContainer = 0xFFC8EBF7,
        loopOnBg = 0xFF5C3518, loopOnFg = 0xFFFFD8BC,
        loop3 = 0xFFE87782, onLoop3 = 0xFF1C0003, loop4 = 0xFF85CC87,
    ),
    light = lightPalette(
        bg = 0xFFFFF5E7, bgDeep = 0xFFFAEEE1, surface1 = 0xFFF2E6D9,
        surface2 = 0xFFE9DDD0, surface3 = 0xFFE0D4C7, surface4 = 0xFFD6CABD,
        fg = 0xFF221F1B, fg2 = 0xFF615B53, barMuted = 0xFFD6CABD,
        amber = 0xFF984B00, amberHover = 0xFF883C00, onAmber = 0xFFFFFFFF,
        teal = 0xFF1A7087, onTeal = 0xFFFFFFFF, tealContainer = 0xFFC8EBF7, onTealContainer = 0xFF07333F,
        loopOnBg = 0xFFFFDBBF, loopOnFg = 0xFF4D2C15,
        loop3 = 0xFFB45862, onLoop3 = 0xFFFFFFFF, loop4 = 0xFF518A53,
    ),
)

/** Near-black and near-white surfaces: text at 7:1 or more, controls at 4.5:1. */
private val HighContrastTheme = AppTheme(
    id = "high-contrast",
    name = Res.string.theme_high_contrast,
    dark = darkPalette(
        bg = 0xFF080808, bgDeep = 0xFF040404, surface1 = 0xFF101010,
        surface2 = 0xFF161616, surface3 = 0xFF202020, surface4 = 0xFF2F2F2F,
        fg = 0xFFF8F8F8, fg2 = 0xFFCECECE, barMuted = 0xFF232323,
        amber = 0xFFFFB407, amberHover = 0xFFFFC430, onAmber = 0xFF271700,
        teal = 0xFF00BCC5, onTeal = 0xFF001B1D, tealContainer = 0xFF104042, onTealContainer = 0xFFC7EDEF,
        loopOnBg = 0xFF543A0B, loopOnFg = 0xFFF8DDB5,
        loop3 = 0xFFF66D67, onLoop3 = 0xFF190000, loop4 = 0xFF89CE5F,
    ),
    light = lightPalette(
        bg = 0xFFF7F7F7, bgDeep = 0xFFF0F0F0, surface1 = 0xFFE8E8E8,
        surface2 = 0xFFDFDFDF, surface3 = 0xFFD6D6D6, surface4 = 0xFFCCCCCC,
        fg = 0xFF1F1F1F, fg2 = 0xFF464646, barMuted = 0xFFCCCCCC,
        amber = 0xFF8D5200, amberHover = 0xFF7E4500, onAmber = 0xFFFFFFFF,
        teal = 0xFF007279, onTeal = 0xFFFFFFFF, tealContainer = 0xFFC7EDEF, onTealContainer = 0xFF023538,
        loopOnBg = 0xFFFBE0B8, loopOnFg = 0xFF46310B,
        loop3 = 0xFFB24440, onLoop3 = 0xFFFFFFFF, loop4 = 0xFF3F7515,
    ),
)

/** In the order of the theme menu. */
val Themes: List<AppTheme> = listOf(
    DownstepTheme,
    GraphiteTheme,
    MidnightTheme,
    ForestTheme,
    RosewoodTheme,
    DuskTheme,
    SandstoneTheme,
    HighContrastTheme,
)

/** The theme saved as [id]; Downstep for an id this version does not know (a newer version's theme). */
fun themeById(id: String): AppTheme = Themes.firstOrNull { it.id == id } ?: DownstepTheme

// The tokens every theme derives the same way as Downstep: the error colours, bars played in fg2,
// the glows, the scrim and the text of the inverted chip (the background).

private fun darkPalette(
    bg: Long, bgDeep: Long, surface1: Long,
    surface2: Long, surface3: Long, surface4: Long,
    fg: Long, fg2: Long, barMuted: Long,
    amber: Long, amberHover: Long, onAmber: Long,
    teal: Long, onTeal: Long, tealContainer: Long, onTealContainer: Long,
    loopOnBg: Long, loopOnFg: Long,
    loop3: Long, onLoop3: Long, loop4: Long,
) = DownstepColors(
    isDark = true,
    bg = Color(bg),
    bgDeep = Color(bgDeep),
    surface1 = Color(surface1),
    surface2 = Color(surface2),
    surface3 = Color(surface3),
    surface4 = Color(surface4),
    fg = Color(fg),
    fg2 = Color(fg2),
    amber = Color(amber),
    amberHover = Color(amberHover),
    onAmber = Color(onAmber),
    teal = Color(teal),
    onTeal = Color(onTeal),
    errBg = DarkTokens.errBg,
    errFg = DarkTokens.errFg,
    loopOnBg = Color(loopOnBg),
    loopOnFg = Color(loopOnFg),
    barMuted = Color(barMuted),
    barPlayed = Color(fg2),
    loopColors = listOf(Color(amber), Color(teal), Color(loop3), Color(loop4)),
    amberGlow = Color(amber).copy(alpha = 0.3f),
    fgGlow = Color(fg).copy(alpha = 0.6f),
    scrim = DarkTokens.scrim,
    chipFgActive = Color(bg),
    tealContainer = Color(tealContainer),
    onTealContainer = Color(onTealContainer),
    onSecondary = Color(onLoop3),
    error = DarkTokens.error,
    onError = DarkTokens.onError,
    onErrorContainer = DarkTokens.onErrorContainer,
)

private fun lightPalette(
    bg: Long, bgDeep: Long, surface1: Long,
    surface2: Long, surface3: Long, surface4: Long,
    fg: Long, fg2: Long, barMuted: Long,
    amber: Long, amberHover: Long, onAmber: Long,
    teal: Long, onTeal: Long, tealContainer: Long, onTealContainer: Long,
    loopOnBg: Long, loopOnFg: Long,
    loop3: Long, onLoop3: Long, loop4: Long,
) = DownstepColors(
    isDark = false,
    bg = Color(bg),
    bgDeep = Color(bgDeep),
    surface1 = Color(surface1),
    surface2 = Color(surface2),
    surface3 = Color(surface3),
    surface4 = Color(surface4),
    fg = Color(fg),
    fg2 = Color(fg2),
    amber = Color(amber),
    amberHover = Color(amberHover),
    onAmber = Color(onAmber),
    teal = Color(teal),
    onTeal = Color(onTeal),
    errBg = LightTokens.errBg,
    errFg = LightTokens.errFg,
    loopOnBg = Color(loopOnBg),
    loopOnFg = Color(loopOnFg),
    barMuted = Color(barMuted),
    barPlayed = Color(fg2),
    loopColors = listOf(Color(amber), Color(teal), Color(loop3), Color(loop4)),
    amberGlow = Color(amber).copy(alpha = 0.28f),
    fgGlow = Color(fg).copy(alpha = 0.3f),
    scrim = Color(fg).copy(alpha = 0.35f),
    chipFgActive = Color(bg),
    tealContainer = Color(tealContainer),
    onTealContainer = Color(onTealContainer),
    onSecondary = Color(onLoop3),
    error = LightTokens.error,
    onError = LightTokens.onError,
    onErrorContainer = LightTokens.onErrorContainer,
)