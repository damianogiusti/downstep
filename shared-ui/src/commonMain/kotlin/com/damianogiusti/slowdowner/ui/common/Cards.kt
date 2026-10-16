package com.damianogiusti.slowdowner.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

/** A settings card (README §1): surface1, 1px surface3 border, 20dp radius, 20/16 padding. */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
            .background(SlowDownerTheme.colors.surface1, DownstepShapes.card)
            .border(1.dp, SlowDownerTheme.colors.surface3, DownstepShapes.card)
            .padding(horizontal = DownstepDimens.cardPaddingHorizontal, vertical = DownstepDimens.cardPaddingVertical),
        content = content,
    )
}
