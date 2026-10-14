package com.damianogiusti.slowdowner.ui.player.components

import org.jetbrains.compose.resources.DrawableResource
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.practice.PracticePreset

/** Callbacks of the [PresetChips] row. */
class PresetActions(
    val onSave: () -> Unit,
    val onSelect: (PracticePreset) -> Unit,
    val onDelete: (PracticePreset) -> Unit,
)

/** "Save" plus one chip per preset. Tap applies, long press offers delete. */
@Composable
fun PresetChips(
    presets: List<PracticePreset>,
    activePresetId: Long?,
    enabled: Boolean,
    actions: PresetActions,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Icon(
                painterResource(Res.drawable.ic_bookmark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(Res.string.presets_title).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item(key = "save") {
                PresetChip(
                    label = stringResource(Res.string.preset_save),
                    icon = Res.drawable.ic_add,
                    style = ChipStyle.Add,
                    enabled = enabled,
                    onClick = actions.onSave,
                )
            }
            items(presets, key = { it.id }) { preset ->
                val isActive = preset.id == activePresetId
                PresetChip(
                    label = preset.name,
                    icon = if (isActive) Res.drawable.ic_check else null,
                    style = if (isActive) ChipStyle.Selected else ChipStyle.Normal,
                    enabled = enabled,
                    onClick = { actions.onSelect(preset) },
                    onLongClick = { actions.onDelete(preset) },
                )
            }
        }
    }
}

private enum class ChipStyle { Add, Selected, Normal }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    label: String,
    icon: DrawableResource?,
    style: ChipStyle,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content, border) = when (style) {
        ChipStyle.Add -> Triple(Color.Transparent, colors.primary, colors.primary)
        ChipStyle.Selected -> Triple(colors.primaryContainer, colors.onPrimaryContainer, Color.Transparent)
        ChipStyle.Normal -> Triple(Color.Transparent, colors.onSurface, colors.outline)
    }
    val deleteLabel = stringResource(Res.string.preset_delete)
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .height(40.dp)
            .border(1.dp, border, MaterialTheme.shapes.small)
            .semantics {
                selected = style == ChipStyle.Selected
                if (onLongClick != null) onLongClick(deleteLabel) { onLongClick(); true }
            }
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp),
        ) {
            icon?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(18.dp)) }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
