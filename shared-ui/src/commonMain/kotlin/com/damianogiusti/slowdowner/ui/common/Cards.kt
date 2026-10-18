package com.damianogiusti.slowdowner.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

/**
 * A settings card (README §1): surface1, 1px surface3 border, 20dp radius, 20/16 padding. It fills
 * the height it is given, so cards in a row sized to its tallest card line up. A short window asks for
 * less [verticalPadding].
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    verticalPadding: Dp = DownstepDimens.cardPaddingVertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
            .fillMaxHeight()
            .background(SlowDownerTheme.colors.surface1, DownstepShapes.card)
            .border(1.dp, SlowDownerTheme.colors.surface3, DownstepShapes.card)
            .padding(horizontal = DownstepDimens.cardPaddingHorizontal, vertical = verticalPadding),
        content = content,
    )
}

/** "PROGRESSION · Solo": the section in capitals, the loop's name as the user typed it. */
@Composable
fun CardTitle(section: String, loopName: String?, modifier: Modifier = Modifier) {
    val text = if (loopName.isNullOrEmpty()) section.uppercase() else "${section.uppercase()} · $loopName"
    Text(
        text,
        style = SlowDownerTheme.type.sectionLabel,
        color = SlowDownerTheme.colors.fg2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * For the last child of a card: at least [min] tall, and down to the card's bottom when the card has
 * a bounded height. Unlike `weight`, safe in a scrolling screen, where the height is unbounded.
 */
fun Modifier.fillRemainingHeight(min: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val minPx = min.roundToPx().coerceAtMost(constraints.maxHeight)
    val childConstraints = if (constraints.hasBoundedHeight) {
        constraints.copy(minHeight = constraints.maxHeight)
    } else {
        constraints.copy(minHeight = maxOf(minPx, constraints.minHeight))
    }
    val placeable = measurable.measure(childConstraints)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
