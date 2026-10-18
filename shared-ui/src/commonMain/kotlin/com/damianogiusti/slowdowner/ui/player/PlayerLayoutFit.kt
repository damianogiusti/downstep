package com.damianogiusti.slowdowner.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/**
 * How the player fits the height it is given, so that a laptop screen shows it all without scrolling.
 * The controls at the bottom always keep their place; what gives way, in this order, is the
 * overview's height, then the cards' padding and read-outs, then the chips' and transport's
 * padding with a lower zoom strip, then the overview again down to [PlayerPartHeights.overviewMin].
 * Only when even that is too tall do the cards scroll ([overflows]). The overview never grows as the
 * window gets shorter: a stage keeps it at most at the floor of the stage before, so the room a
 * compaction frees stays below the cards instead of jumping into the overview.
 *
 * On a taller window than the design needs, a full screen above all, the spare room goes to the
 * waveforms: the overview takes [OVERVIEW_SHARE] of it and the zoom strip [ZOOM_SHARE], up to
 * [PlayerPartHeights.overviewTall] and [PlayerPartHeights.zoomTall]; what is left stays between the
 * cards and the controls.
 *
 * A pure function of the window's size and of what is on screen: the player computes it again when
 * the window is resized, never while it plays.
 */
@Immutable
data class PlayerLayoutFit(
    val overviewHeight: Dp,
    val zoomHeight: Dp,
    /** Less padding above and below the chips. */
    val compactChips: Boolean,
    /** Less padding above and below the transport. */
    val compactTransport: Boolean,
    /** Speed, Pitch and Isolate with less padding and a smaller read-out. */
    val compactCards: Boolean,
    /** Progression without its track, Notes with a shorter field. */
    val compactLoopCards: Boolean,
    /** The key hints beside the transport: only where its right column has room for them. */
    val showKeyboardHints: Boolean,
    /** Even the most compact layout is taller than the window: the cards scroll. */
    val overflows: Boolean,
) {
    companion object {

        /**
         * The fit for a player of [width] × [height]. [hasLoopCards]: an active loop shows Progression
         * and Notes. [extraTop]: what shows above the cards besides the header (the loading bar, the
         * export banner). [parts] are the natural heights, measured on the real components.
         */
        fun of(
            width: Dp,
            height: Dp,
            hasLoopCards: Boolean,
            extraTop: Dp = 0.dp,
            parts: PlayerPartHeights = PlayerPartHeights(),
        ): PlayerLayoutFit {
            val hints = width >= parts.keyboardHintsMinWidth
            val stages = stages(parts)
            val available = height - extraTop
            for ((index, stage) in stages.withIndex()) {
                val rest = stage.restHeight(parts, width, hasLoopCards)
                val room = available - rest
                if (room >= stage.overviewMin) {
                    // A later stage starts where the one before gave up: more would make the overview jump
                    // up 1dp shorter.
                    if (index > 0) return stage.toFit(min(room, stages[index - 1].overviewMin), hints, overflows = false)
                    // Only an uncompressed layout grows the waveforms into the spare room.
                    val spare = room - parts.overviewGrown
                    if (spare <= 0.dp) return stage.toFit(room, hints, overflows = false)
                    val overview = min(parts.overviewGrown + spare * OVERVIEW_SHARE, parts.overviewTall)
                    val zoom = min(stage.zoom + spare * ZOOM_SHARE, parts.zoomTall)
                    return stage.copy(zoom = zoom).toFit(overview, hints, overflows = false)
                }
            }
            return stages.last().let { it.toFit(it.overviewMin, hints, overflows = true) }
        }

        private fun stages(parts: PlayerPartHeights) = listOf(
            Stage(overviewMin = parts.overviewShrunk, zoom = parts.zoom),
            Stage(overviewMin = parts.overviewShrunk, zoom = parts.zoom, compactCards = true, compactLoopCards = true),
            Stage(parts.overviewMin + STEP, parts.zoomShrunk, compactCards = true, compactLoopCards = true, compactChrome = true),
            Stage(parts.overviewMin, parts.zoomMin, compactCards = true, compactLoopCards = true, compactChrome = true),
        )

        private val STEP = 20.dp

        /** The share of the room past the design's height that the overview grows by. */
        const val OVERVIEW_SHARE = 0.6f

        /** The share of that room the zoom strip grows by. */
        const val ZOOM_SHARE = 0.25f
    }

    private data class Stage(
        val overviewMin: Dp,
        val zoom: Dp,
        val compactCards: Boolean = false,
        val compactLoopCards: Boolean = false,
        val compactChrome: Boolean = false,
    ) {
        /** Everything but the overview. */
        fun restHeight(parts: PlayerPartHeights, width: Dp, hasLoopCards: Boolean): Dp {
            val chrome = (if (compactChrome) parts.chipsCompact else parts.chips) +
                zoom + (if (compactChrome) parts.transportCompact else parts.transport)
            val cards = if (compactCards) parts.cardsCompact else parts.cards
            val loopCards = when {
                !hasLoopCards -> 0.dp
                // The progression's sentence wraps to a second line on a narrow card.
                else -> (if (compactLoopCards) parts.loopCardsCompact else parts.loopCards) +
                    (if (width < parts.loopCardsOneLineWidth) parts.loopCardsWrap else 0.dp)
            }
            return parts.header + cards + loopCards + chrome
        }

        fun toFit(overview: Dp, hints: Boolean, overflows: Boolean) = PlayerLayoutFit(
            overviewHeight = overview,
            zoomHeight = zoom,
            compactChips = compactChrome,
            compactTransport = compactChrome,
            compactCards = compactCards,
            compactLoopCards = compactLoopCards,
            showKeyboardHints = hints,
            overflows = overflows,
        )
    }
}

/**
 * The natural heights of the player's parts, padding included, as the components lay them out
 * (PlayerLayoutRenderTest checks the result on the real screen). Compact = the reduced form.
 */
@Immutable
data class PlayerPartHeights(
    val header: Dp = 74.dp,
    val chips: Dp = 56.dp,
    val chipsCompact: Dp = 44.dp,
    /** Grown into spare room on a tall window: the design's height. */
    val overviewGrown: Dp = 260.dp,
    /** Past the design's height (a full screen), the overview grows on, up to this. */
    val overviewTall: Dp = 640.dp,
    /** The first stage shrinks the overview down to this before anything else gives way. */
    val overviewShrunk: Dp = 160.dp,
    val overviewMin: Dp = 120.dp,
    val zoom: Dp = 96.dp,
    /** The zoom strip grows with the overview past the design's height, up to this. */
    val zoomTall: Dp = 200.dp,
    val zoomShrunk: Dp = 76.dp,
    val zoomMin: Dp = 64.dp,
    val transport: Dp = 96.dp,
    val transportCompact: Dp = 80.dp,
    /** Speed · Pitch · Isolate. */
    val cards: Dp = 115.dp,
    val cardsCompact: Dp = 87.dp,
    /** Progression · Notes. */
    val loopCards: Dp = 142.dp,
    val loopCardsCompact: Dp = 95.dp,
    /** Below this width the progression's sentence takes two lines. */
    val loopCardsOneLineWidth: Dp = 1240.dp,
    val loopCardsWrap: Dp = 36.dp,
    val keyboardHintsMinWidth: Dp = 1180.dp,
)
