package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.common.TitleBarOverlay
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
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
import com.damianogiusti.slowdowner.ui.common.HorizontalListScrollbar
import com.damianogiusti.slowdowner.ui.common.Tooltip
import com.damianogiusti.slowdowner.ui.common.dashedPillBorder
import com.damianogiusti.slowdowner.ui.common.isDesktop
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One chip per loop in time order (colour dot · name · length · pass badge; the active one inverted),
 * then the dashed suggestions with ✓ / ✕ (only when Analyze returned some), then "+ New loop L".
 * A double click on a chip renames it in place (as does Rename in its context menu: secondary click
 * or long press), and the menu deletes it. [compact]: less padding, for a short window.
 *
 * Chips and suggestions scroll sideways; "+ New loop" stays pinned at the end. On desktop the plain
 * wheel scrolls them too (Shift+wheel and a sideways swipe already do), a thin scrollbar shows under
 * them and edge fades mark the hidden side, only while they overflow. A newly active chip, or one
 * being renamed, is scrolled fully into view.
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
    val listState = rememberLazyListState()
    val chips by rememberUpdatedState(state.chips)
    val density = LocalDensity.current
    val revealMargin = with(density) { EDGE_FADE.toPx() }
    // Only when the active loop changes (a click, [ ], L, a kept suggestion, the overview), never on a
    // pass or a tick: the user may have scrolled away on purpose since.
    val activeId = state.chips.firstOrNull { it.isActive }?.id
    LaunchedEffect(activeId) {
        val index = chips.indexOfFirst { it.id == activeId }
        if (index >= 0) listState.revealItem(index, revealMargin)
    }
    LaunchedEffect(editingId) {
        val index = chips.indexOfFirst { it.id == editingId }
        if (index >= 0) listState.revealItem(index, revealMargin)
    }
    val overflows by remember(listState) { derivedStateOf { listState.canScrollBackward || listState.canScrollForward } }
    val top = if (compact) 6.dp else 12.dp
    val bottom = if (compact) 4.dp else 10.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag(LoopChipsTags.STRIP)
            .then(if (isDesktop) Modifier.wheelScrollsSideways(listState) else Modifier),
    ) {
        // fill = false: with few loops "+ New loop" follows the last chip instead of the window's edge.
        Box(Modifier.weight(1f, fill = false)) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(start = DownstepDimens.gutter, end = CHIP_GAP, top = top, bottom = bottom),
                modifier = Modifier.testTag(LoopChipsTags.LIST).edgeFades(listState),
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
            }
            if (overflows) {
                // In the padding under the chips: showing it moves nothing.
                Box(Modifier.matchParentSize()) {
                    HorizontalListScrollbar(
                        state = listState,
                        thickness = if (compact) 3.dp else 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = DownstepDimens.gutter, end = CHIP_GAP, bottom = if (compact) 0.dp else 3.dp)
                            .testTag(LoopChipsTags.SCROLLBAR),
                    )
                }
            }
        }
        NewLoopChip(
            state.enabled,
            actions.onNewLoop,
            Modifier.padding(end = DownstepDimens.gutter, top = top, bottom = bottom),
        )
    }
}

/** Test tags of the strip. */
object LoopChipsTags {
    const val STRIP = "loop-chips"
    const val LIST = "loop-chips-list"
    const val SCROLLBAR = "loop-chips-scrollbar"
    const val NEW_LOOP = "loop-chips-new"
}

/** Fades out the chips at the side(s) with more of them: read at draw time, so scrolling recomposes nothing. */
private fun Modifier.edgeFades(state: LazyListState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = EDGE_FADE.toPx()
            if (state.canScrollBackward) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = 0f, endX = fade),
                    blendMode = BlendMode.DstOut,
                )
            }
            if (state.canScrollForward) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = size.width - fade, endX = size.width),
                    blendMode = BlendMode.DstOut,
                )
            }
        }

/**
 * The plain wheel is vertical: over the strip it scrolls the chips sideways, [WHEEL_STEP] a notch,
 * animated (a touchpad's fractions of a notch follow the fingers). Read after the list (Main pass), so
 * Shift+wheel and sideways swipes stay the list's own; what the chips cannot use (no overflow, at an
 * end) is left to the window.
 */
private fun Modifier.wheelScrollsSideways(state: LazyListState): Modifier = pointerInput(state) {
    coroutineScope {
        // Pixels still to scroll: notches that come while one animates add up.
        var pending = 0f
        var animation: Job? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val notches = change.scrollDelta.y
                if (change.isConsumed || notches == 0f) continue
                if (if (notches > 0f) !state.canScrollForward else !state.canScrollBackward) continue
                change.consume()
                val delta = notches * WHEEL_STEP.toPx()
                if (abs(notches) < 1f) {
                    animation?.cancel()
                    pending = 0f
                    state.dispatchRawDelta(delta)
                    continue
                }
                pending += delta
                animation?.cancel()
                animation = launch {
                    state.scroll {
                        val target = pending
                        var done = 0f
                        animate(0f, target, animationSpec = tween(WHEEL_MILLIS, easing = LinearOutSlowInEasing)) { value, _ ->
                            val used = scrollBy(value - done)
                            pending -= used
                            done = value
                        }
                        pending = 0f
                    }
                }
            }
        }
    }
}

/**
 * Scrolls the least that shows item [index] whole and clear of the edge fades ([margin] px), animated.
 * An item off the composed range is first scrolled to; one wider than the list is aligned at its start.
 */
internal suspend fun LazyListState.revealItem(index: Int, margin: Float) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) animateScrollToItem(index)
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val start = info.viewportStartOffset + margin
    val end = info.viewportEndOffset - margin
    val delta = when {
        item.offset < start -> item.offset - start
        item.offset + item.size > end -> minOf(item.offset + item.size - end, item.offset - start)
        else -> return
    }
    // Nothing to animate at an end: a scroll in progress would take the next tap to stop it.
    if (if (delta < 0f) canScrollBackward else canScrollForward) animateScrollBy(delta)
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
private fun NewLoopChip(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SlowDownerTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .testTag(LoopChipsTags.NEW_LOOP)
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
private val CHIP_GAP = 8.dp
// As wide as the gutter and a bit: a chip under it reads as cut, not as gone.
private val EDGE_FADE = 32.dp
// About half a chip a notch.
private val WHEEL_STEP = 64.dp
private const val WHEEL_MILLIS = 120
