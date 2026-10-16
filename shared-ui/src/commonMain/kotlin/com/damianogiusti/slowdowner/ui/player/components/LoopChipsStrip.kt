package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.ui.common.CountBadge
import com.damianogiusti.slowdowner.ui.common.DISABLED_ALPHA
import com.damianogiusti.slowdowner.ui.common.TextInputDialog
import com.damianogiusti.slowdowner.ui.common.TextInputDialogTexts
import com.damianogiusti.slowdowner.ui.common.Tooltip
import com.damianogiusti.slowdowner.ui.common.dashedPillBorder
import com.damianogiusti.slowdowner.ui.player.LoopChipActions
import com.damianogiusti.slowdowner.ui.player.LoopChipModel
import com.damianogiusti.slowdowner.ui.player.LoopChipsState
import com.damianogiusti.slowdowner.ui.player.SuggestionModel
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * One chip per loop in time order (colour dot · name · length · pass badge; the active one inverted),
 * then the dashed suggestions with ✓ / ✕ (only when Analyze returned some), then "+ New loop L".
 * Rename and delete from a chip's context menu (secondary click or long press). Scrolls horizontally.
 */
@Composable
fun LoopChipsStrip(
    state: LoopChipsState,
    suggestions: List<SuggestionModel>,
    actions: LoopChipActions,
    modifier: Modifier = Modifier,
) {
    var renaming by remember { mutableStateOf<LoopChipModel?>(null) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(start = DownstepDimens.gutter, end = DownstepDimens.gutter, top = 12.dp, bottom = 10.dp),
        modifier = modifier,
    ) {
        items(state.chips, key = { it.id }) { chip ->
            LoopChip(chip, state.enabled, actions, onRename = { renaming = chip })
        }
        items(suggestions, key = { "suggestion-${it.id}" }) { suggestion ->
            SuggestionChip(suggestion, state.enabled, actions)
        }
        item(key = "new") {
            NewLoopChip(state.enabled, actions.onNewLoop)
        }
    }
    renaming?.let { chip ->
        TextInputDialog(
            texts = TextInputDialogTexts(
                title = stringResource(Res.string.loop_rename),
                label = stringResource(Res.string.loop_rename),
                confirm = stringResource(Res.string.save),
            ),
            initialText = chip.name,
            onConfirm = { name ->
                renaming = null
                actions.onRename(chip.id, name)
            },
            onDismiss = { renaming = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoopChip(chip: LoopChipModel, enabled: Boolean, actions: LoopChipActions, onRename: () -> Unit) {
    val colors = SlowDownerTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val passesDescription = pluralStringResource(Res.plurals.loop_passes, chip.passes, chip.passes)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .height(DownstepDimens.chip)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(DownstepShapes.pill)
                .background(if (chip.isActive) colors.fg else colors.surface1, DownstepShapes.pill)
                .border(1.dp, if (chip.isActive) colors.fg else colors.surface3, DownstepShapes.pill)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            // Initial pass: the menu takes the right click before the chip selects.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                event.changes.forEach { it.consume() }
                                menuOpen = true
                            }
                        }
                    }
                }
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onLongClick = { menuOpen = true },
                    onClick = { actions.onSelect(chip.id) },
                )
                .padding(horizontal = 10.dp),
        ) {
            val text = if (chip.isActive) colors.chipFgActive else colors.fg
            Box(Modifier.size(8.dp).background(colors.loopColor(chip.colorIndex), CircleShape))
            Text(chip.name, style = SlowDownerTheme.type.controlStrong, color = text, maxLines = 1)
            Text(
                stringResource(Res.string.loop_length_seconds, chip.lengthSeconds.roundToInt()),
                style = SlowDownerTheme.type.control.copy(fontWeight = FontWeight.Normal),
                color = text.copy(alpha = LENGTH_ALPHA),
                maxLines = 1,
            )
            CountBadge(
                chip.passes.toString(),
                container = if (chip.isActive) colors.amber else colors.surface3,
                contentColor = if (chip.isActive) colors.onAmber else colors.fg2,
                modifier = Modifier.semantics { contentDescription = passesDescription },
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.surface2) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.loop_rename)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuOpen = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.loop_delete)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuOpen = false
                    actions.onDelete(chip.id)
                },
            )
        }
    }
}

/** A loop Analyze suggests: dashed amber until kept (✓) or dismissed (✕), README §4.8. */
@Composable
private fun SuggestionChip(suggestion: SuggestionModel, enabled: Boolean, actions: LoopChipActions) {
    val colors = SlowDownerTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(DownstepDimens.chip)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .dashedPillBorder(colors.amber)
            .padding(start = 12.dp, end = 4.dp),
    ) {
        Text(suggestion.name, style = SlowDownerTheme.type.control, color = colors.amber, maxLines = 1)
        Text(
            stringResource(Res.string.loop_length_seconds, suggestion.lengthSeconds.roundToInt()),
            style = SlowDownerTheme.type.control.copy(fontWeight = FontWeight.Normal),
            color = colors.amber.copy(alpha = SUGGESTION_LENGTH_ALPHA),
            maxLines = 1,
        )
        RoundIcon(
            icon = Res.drawable.ic_h_check,
            description = stringResource(Res.string.suggestion_accept),
            enabled = enabled,
            container = colors.amber,
            tint = colors.onAmber,
            iconSize = 13,
            modifier = Modifier.padding(start = 4.dp),
            onClick = { actions.onSuggestionAccepted(suggestion.id) },
        )
        RoundIcon(
            icon = Res.drawable.ic_h_close,
            description = stringResource(Res.string.suggestion_dismiss),
            enabled = enabled,
            container = null,
            tint = colors.amber,
            iconSize = 12,
            onClick = { actions.onSuggestionDismissed(suggestion.id) },
        )
    }
}

@Composable
private fun RoundIcon(
    icon: DrawableResource,
    description: String,
    enabled: Boolean,
    container: Color?,
    tint: Color,
    iconSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tooltip(description, modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(if (container != null) Modifier.background(container, CircleShape) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(iconSize.dp))
        }
    }
}

@Composable
private fun NewLoopChip(enabled: Boolean, onClick: () -> Unit) {
    val colors = SlowDownerTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(DownstepDimens.chip)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(DownstepShapes.pill)
            .dashedPillBorder(colors.surface4)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp),
    ) {
        Icon(painterResource(Res.drawable.ic_h_plus), contentDescription = null, tint = colors.fg2, modifier = Modifier.size(14.dp))
        Text(stringResource(Res.string.new_loop), style = SlowDownerTheme.type.control, color = colors.fg2, maxLines = 1)
        Text(
            stringResource(Res.string.key_new_loop),
            style = SlowDownerTheme.type.control.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = colors.fg,
            modifier = Modifier
                .background(colors.surface3, KeyShape)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

private const val LENGTH_ALPHA = 0.6f
private const val SUGGESTION_LENGTH_ALPHA = 0.7f
private val KeyShape = RoundedCornerShape(4.dp)
