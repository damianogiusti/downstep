package com.damianogiusti.slowdowner.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * A thin draggable scrollbar under a horizontal lazy list (desktop: there is no swipe with a mouse);
 * nothing on Android, where a finger scrolls the list. [thickness] is the thumb's.
 */
@Composable
internal expect fun HorizontalListScrollbar(state: LazyListState, thickness: Dp, modifier: Modifier = Modifier)
