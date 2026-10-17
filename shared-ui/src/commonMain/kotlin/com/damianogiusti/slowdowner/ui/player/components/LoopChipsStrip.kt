package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.common.TitleBarOverlay
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.domain.loops.Loop
import com.damianogiusti.slowdowner.ui.common.CountBadge
import com.damianogiusti.slowdowner.ui.common.DISABLED_ALPHA
import com.damianogiusti.slowdowner.ui.common.Tooltip
import com.damianogiusti.slowdowner.ui.common.dashedPillBorder
import com.damianogiusti.slowdowner.ui.common.reportsTextInputFocus
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One chip per loop in time order (colour dot · name · length · pass badge; the active one inverted),
 * then the dashed suggestions with ✓ / ✕ (only when Analyze returned some), then "+ New loop L".
 * A double click on a chip renames it in place (as does Rename in its context menu: secondary click
 * or long press), and the menu deletes it. Scrolls horizontally. [compact]: less padding, for a short window.
 */
@Composable
fun LoopChipsStrip(
    state: LoopChipsState,
    suggestions: List<SuggestionModel>,
    actions: LoopChipActions,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // The chip whose name is being edited; one at a time.
    var editingId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.chips, state.enabled) {
        if (!state.enabled || state.chips.none { it.id == editingId }) editingId = null
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(
            start = DownstepDimens.gutter,
            end = DownstepDimens.gutter,
            top = if (compact) 6.dp else 12.dp,
            bottom = if (compact) 4.dp else 10.dp,
        ),
        modifier = modifier,
    ) {
        items(state.chips, key = { it.id }) { chip ->
            LoopChip(
                chip = chip,
                enabled = state.enabled,
                actions = actions,
                isEditing = chip.id == editingId,
                onStartRename = { editingId = chip.id },
                onEndRename = { if (editingId == chip.id) editingId = null },
            )
        }
        items(suggestions, key = { "suggestion-${it.id}" }) { suggestion ->
            SuggestionChip(suggestion, state.enabled, actions)
        }
        item(key = "new") {
            NewLoopChip(state.enabled, actions.onNewLoop)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoopChip(
    chip: LoopChipModel,
    enabled: Boolean,
    actions: LoopChipActions,
    isEditing: Boolean,
    onStartRename: () -> Unit,
    onEndRename: () -> Unit,
) {
    val colors = SlowDownerTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    // Double click by hand: combinedClickable's onDoubleClick would hold every single click (select
    // and seek) back for the double-click timeout.
    val doubleClickMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val lastClick = remember { arrayOfNulls<TimeMark>(1) }
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
                .pointerInput(enabled, isEditing) {
                    if (!enabled || isEditing) return@pointerInput
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
                    enabled = enabled && !isEditing,
                    role = Role.Button,
                    onLongClick = { menuOpen = true },
                    onClick = {
                        val previous = lastClick[0]
                        if (previous != null && previous.elapsedNow().inWholeMilliseconds <= doubleClickMillis) {
                            lastClick[0] = null
                            onStartRename()
                        } else {
                            lastClick[0] = TimeSource.Monotonic.markNow()
                            actions.onSelect(chip.id)
                        }
                    },
                )
                .padding(horizontal = 10.dp),
        ) {
            val text = if (chip.isActive) colors.chipFgActive else colors.fg
            // On the fg-filled active chip a loop colour can be as light as fg: a ring of the chip's text
            // colour keeps the dot 3:1 apart.
            Box(
                Modifier
                    .size(8.dp)
                    .background(colors.loopColor(chip.colorIndex), CircleShape)
                    .then(if (chip.isActive) Modifier.border(1.dp, colors.chipFgActive, CircleShape) else Modifier),
            )
            if (isEditing) {
                LoopNameEditor(
                    initial = chip.name,
                    color = text,
                    onCommit = { name ->
                        onEndRename()
                        actions.onRename(chip.id, name)
                    },
                    onCancel = onEndRename,
                )
            } else {
                Text(chip.name, style = SlowDownerTheme.type.controlStrong, color = text, maxLines = 1)
            }
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
            TitleBarOverlay()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.loop_rename)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuOpen = false
                    onStartRename()
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

/**
 * The chip's name as a text field, focused with the name selected. Enter or leaving the field saves,
 * Esc cancels, and an empty or unchanged name keeps the old one. While it has the focus, the window's
 * shortcuts are off ([reportsTextInputFocus]), so typing "L" or Space types.
 */
@Composable
private fun LoopNameEditor(initial: String, color: Color, onCommit: (String) -> Unit, onCancel: () -> Unit) {
    val colors = SlowDownerTheme.colors
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    // [0] focused now, [1] focused once, [2] finished (saved or cancelled: only once), [3] a press that
    // began in the field is still down.
    val flags = remember { booleanArrayOf(false, false, false, false) }
    val description = stringResource(Res.string.loop_rename)

    fun finish(save: Boolean) {
        if (flags[2]) return
        flags[2] = true
        when (val name = loopNameEdit(initial, value.text, save)) {
            null -> onCancel()
            else -> onCommit(name)
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = { value = it.copy(text = it.text.take(Loop.MAX_NAME_LENGTH)) },
        singleLine = true,
        textStyle = SlowDownerTheme.type.controlStrong.copy(color = color),
        cursorBrush = SolidColor(colors.amber),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish(save = true) }),
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .widthIn(min = EDITOR_MIN_WIDTH, max = EDITOR_MAX_WIDTH)
            .pointerInput(Unit) {
                // A press in the field is not the user leaving it, however long it is held: the field
                // takes the focus back on release (a tap), or not at all (an empty field, a touch drag).
                // Either way, once every pointer is up the field has the focus again.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    flags[3] = true
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                    } while (event.changes.any { it.pressed })
                    flags[3] = false
                    scope.launch {
                        withFrameNanos { }
                        if (!flags[0] && !flags[2]) focus.requestFocus()
                    }
                }
            }
            .focusRequester(focus)
            .reportsTextInputFocus()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> finish(save = true)
                    Key.Escape -> finish(save = false)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .onFocusChanged { state ->
                flags[0] = state.isFocused
                if (state.isFocused) {
                    flags[1] = true
                } else if (flags[1]) {
                    // A press anywhere first clears the focus (clearsFocusOnPress). A press inside the
                    // field marks itself a moment later, in the same event, and gives the focus back
                    // on release: only a blur with no press in the field is the user leaving it.
                    scope.launch {
                        withFrameNanos { }
                        if (!flags[0] && !flags[3]) finish(save = true)
                    }
                }
            }
            .semantics { contentDescription = description },
    )
}

/**
 * The name a rename saves: the trimmed text, or null to keep [initial] (cancelled, empty, or the same
 * name). Never longer than [Loop.MAX_NAME_LENGTH].
 */
internal fun loopNameEdit(initial: String, text: String, save: Boolean): String? {
    if (!save) return null
    val name = text.trim().take(Loop.MAX_NAME_LENGTH)
    return name.takeIf { it.isNotEmpty() && it != initial }
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
        // Set apart by weight only: amber is just AA as small text, any dimming would not be.
        Text(
            stringResource(Res.string.loop_length_seconds, suggestion.lengthSeconds.roundToInt()),
            style = SlowDownerTheme.type.control.copy(fontWeight = FontWeight.Normal),
            color = colors.amber,
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

// The least dimming that keeps the length AA on surface1 in every light theme (ThemeContrastTest).
private const val LENGTH_ALPHA = 0.65f
private val EDITOR_MIN_WIDTH = 48.dp
private val EDITOR_MAX_WIDTH = 220.dp
private val KeyShape = RoundedCornerShape(4.dp)
