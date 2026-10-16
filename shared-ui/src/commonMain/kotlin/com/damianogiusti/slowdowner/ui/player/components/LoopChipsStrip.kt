package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.common.CountBadge
import com.damianogiusti.slowdowner.ui.common.Pill
import com.damianogiusti.slowdowner.ui.player.LoopChipActions
import com.damianogiusti.slowdowner.ui.player.LoopChipsState
import com.damianogiusti.slowdowner.ui.player.SuggestionModel
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// PLACEHOLDER (group H): final signature, minimal body. See ui-contract.md, README §2.3.

/**
 * One chip per loop in time order (colour dot · name · length · pass badge; the active one inverted),
 * then the dashed suggestions with ✓ / ✕ (only when Analyze returned some), then "+ New loop L".
 * Rename and delete from a chip's context menu. Scrolls horizontally.
 */
@Composable
fun LoopChipsStrip(
    state: LoopChipsState,
    suggestions: List<SuggestionModel>,
    actions: LoopChipActions,
    modifier: Modifier = Modifier,
) {
    val colors = SlowDownerTheme.colors
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = DownstepDimens.gutter, vertical = 10.dp),
        modifier = modifier,
    ) {
        items(state.chips, key = { it.id }) { chip ->
            Pill(
                onClick = { actions.onSelect(chip.id) },
                enabled = state.enabled,
                height = DownstepDimens.chip,
                container = if (chip.isActive) colors.fg else colors.surface1,
                contentColor = if (chip.isActive) colors.chipFgActive else colors.fg,
                border = if (chip.isActive) colors.fg else colors.surface3,
            ) {
                androidx.compose.foundation.layout.Box(Modifier.size(8.dp).background(colors.loopColor(chip.colorIndex), DownstepShapes.pill))
                Text(chip.name, style = SlowDownerTheme.type.controlStrong)
                Text(stringResource(Res.string.loop_length_seconds, chip.lengthSeconds.roundToInt()))
                CountBadge(chip.passes.toString())
            }
        }
        item(key = "new") {
            Pill(onClick = actions.onNewLoop, enabled = state.enabled, height = DownstepDimens.chip, border = colors.surface4, dashed = true) {
                Text("+ " + stringResource(Res.string.new_loop))
            }
        }
    }
}
