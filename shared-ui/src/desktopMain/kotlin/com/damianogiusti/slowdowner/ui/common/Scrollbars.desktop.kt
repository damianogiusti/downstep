package com.damianogiusti.slowdowner.ui.common

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

@Composable
internal actual fun HorizontalListScrollbar(state: LazyListState, thickness: Dp, modifier: Modifier) {
    val colors = SlowDownerTheme.colors
    val style = remember(colors, thickness) {
        ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = thickness,
            shape = DownstepShapes.pill,
            hoverDurationMillis = 150,
            unhoverColor = colors.surface4,
            hoverColor = colors.fg2,
        )
    }
    HorizontalScrollbar(rememberScrollbarAdapter(state), modifier, style = style)
}
