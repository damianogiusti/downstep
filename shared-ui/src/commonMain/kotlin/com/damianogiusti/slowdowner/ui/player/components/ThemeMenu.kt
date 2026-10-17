package com.damianogiusti.slowdowner.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.domain.theme.ThemeSelection
import com.damianogiusti.slowdowner.ui.common.IconPill
import com.damianogiusti.slowdowner.ui.common.SegmentedPill
import com.damianogiusti.slowdowner.ui.common.reportsTextInputFocus
import com.damianogiusti.slowdowner.ui.player.HeaderActions
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.ic_h_check
import com.damianogiusti.slowdowner.ui.resources.ic_h_moon
import com.damianogiusti.slowdowner.ui.resources.ic_h_sun
import com.damianogiusti.slowdowner.ui.resources.theme
import com.damianogiusti.slowdowner.ui.resources.theme_mode
import com.damianogiusti.slowdowner.ui.resources.theme_mode_dark
import com.damianogiusti.slowdowner.ui.resources.theme_mode_light
import com.damianogiusti.slowdowner.ui.resources.theme_palettes
import com.damianogiusti.slowdowner.ui.theme.AppTheme
import com.damianogiusti.slowdowner.ui.theme.DownstepColors
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.Themes
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The header's theme button (sun or moon: the mode on screen) and its menu: Dark / Light, then the
 * themes, each with a swatch of its colours in the mode on screen. What the pointer or the keyboard
 * focus is on is previewed across the app; leaving the item, Esc or closing the menu goes back to
 * [selection]; a click, Enter or Space applies and saves. A theme closes the menu, a mode keeps it
 * open so a theme can follow.
 */
@Composable
internal fun ThemeMenuButton(selection: ThemeSelection, actions: HeaderActions) {
    val colors = SlowDownerTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val close = {
        expanded = false
        actions.onThemePreviewEnd()
    }
    Box {
        IconPill(
            painterResource(if (colors.isDark) Res.drawable.ic_h_sun else Res.drawable.ic_h_moon),
            stringResource(Res.string.theme),
            { expanded = true },
            container = colors.surface1,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = close, containerColor = colors.surface2) {
            ThemeMenuContent(
                selection = selection,
                onPreview = actions.onThemePreview,
                onPreviewEnd = actions.onThemePreviewEnd,
                onApply = { applied, closes ->
                    if (closes) expanded = false
                    actions.onThemeApplied(applied)
                },
                onClose = close,
            )
        }
    }
}

/** What the menu previews: the item last entered by the pointer or the focus, until it is left. */
@Stable
private class PreviewTarget {
    var value by mutableStateOf<ThemeSelection?>(null)
        private set

    /** Set once a pick closes the menu: its fade-out must not preview what the pointer crosses. */
    var isClosing = false

    fun enter(selection: ThemeSelection) {
        if (!isClosing) value = selection
    }

    fun leave(selection: ThemeSelection) {
        if (value == selection) value = null
    }
}

@Composable
private fun ThemeMenuContent(
    selection: ThemeSelection,
    onPreview: (ThemeSelection) -> Unit,
    onPreviewEnd: () -> Unit,
    onApply: (ThemeSelection, closes: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val colors = SlowDownerTheme.colors
    val focusManager = LocalFocusManager.current
    val target = remember { PreviewTarget() }
    val currentOnPreview by rememberUpdatedState(onPreview)
    val currentOnPreviewEnd by rememberUpdatedState(onPreviewEnd)
    LaunchedEffect(target) {
        // drop(1): opening previews nothing.
        snapshotFlow { target.value }.drop(1).collect { if (it != null) currentOnPreview(it) else currentOnPreviewEnd() }
    }
    // Gone without a pick (or after one, harmless): back to the chosen theme.
    DisposableEffect(Unit) { onDispose { currentOnPreviewEnd() } }

    // The keyboard starts on the checked theme, so the arrows move from there.
    val checkedRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { checkedRow.requestFocus() } }

    val apply = { applied: ThemeSelection, closes: Boolean ->
        if (closes) target.isClosing = true
        onApply(applied, closes)
    }
    Column(
        modifier = Modifier
            .width(MENU_WIDTH)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown, Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Next)
                    Key.DirectionUp, Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Previous)
                    Key.Escape -> onClose()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        MenuLabel(stringResource(Res.string.theme_mode))
        SegmentedPill(
            container = colors.surface1,
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().selectableGroup(),
        ) {
            ThemeMode.entries.forEach { mode ->
                val option = selection.copy(mode = mode)
                ModeOption(
                    mode = mode,
                    selected = mode == selection.mode,
                    modifier = Modifier.weight(1f).previewsOn(option, target),
                    onClick = { apply(option, false) },
                )
            }
        }
        HorizontalDivider(color = colors.surface3, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        MenuLabel(stringResource(Res.string.theme_palettes))
        Column(Modifier.selectableGroup().padding(bottom = 4.dp)) {
            val mode = if (colors.isDark) ThemeMode.Dark else ThemeMode.Light
            Themes.forEach { theme ->
                val option = selection.copy(themeId = theme.id)
                val checked = theme.id == selection.themeId
                ThemeRow(
                    theme = theme,
                    swatch = theme.colors(mode),
                    selected = checked,
                    modifier = Modifier
                        .then(if (checked) Modifier.focusRequester(checkedRow) else Modifier)
                        .previewsOn(option, target),
                    onClick = { apply(option, true) },
                )
            }
        }
    }
}

/**
 * Hover and focus preview [selection]; leaving it ends the preview. Enter and Space apply on release,
 * here rather than in clickable, so the window's shortcuts (Space plays) and the click never both run.
 * The items count as text input meanwhile: the shortcuts leave the arrows, Space and Esc to the menu.
 */
private fun Modifier.previewsOn(selection: ThemeSelection, target: PreviewTarget): Modifier = this
    .pointerInput(selection) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    // A move too: the first event in a just opened popup is not always an Enter.
                    PointerEventType.Enter, PointerEventType.Move -> target.enter(selection)
                    PointerEventType.Exit -> target.leave(selection)
                }
            }
        }
    }
    .reportsTextInputFocus()
    .onFocusChanged { if (it.isFocused) target.enter(selection) else target.leave(selection) }

/** Enter, numpad Enter and Space click on release; the press is taken too, so nothing else sees it. */
private fun Modifier.clicksOnKeys(onClick: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Enter && event.key != Key.NumPadEnter && event.key != Key.Spacebar) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyUp) onClick()
    true
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        style = SlowDownerTheme.type.meta,
        color = SlowDownerTheme.colors.fg2,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp).semantics { heading() },
    )
}

/** One half of the Dark / Light pill: inverted (fg, as the active loop chip) when chosen. */
@Composable
private fun ModeOption(mode: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = SlowDownerTheme.colors
    val content = if (selected) colors.chipFgActive else colors.fg
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .height(MODE_HEIGHT)
            .clip(DownstepShapes.pill)
            .background(if (selected) colors.fg else colors.surface1, DownstepShapes.pill)
            .clicksOnKeys(onClick)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Icon(
            painterResource(if (mode == ThemeMode.Dark) Res.drawable.ic_h_moon else Res.drawable.ic_h_sun),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp),
        )
        Text(
            stringResource(if (mode == ThemeMode.Dark) Res.string.theme_mode_dark else Res.string.theme_mode_light),
            style = SlowDownerTheme.type.control,
            color = content,
            maxLines = 1,
        )
    }
}

/** Swatch, name and the check of the chosen theme. The name stays fg: amber text is not AA on every menu. */
@Composable
private fun ThemeRow(theme: AppTheme, swatch: DownstepColors, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = SlowDownerTheme.colors
    val name = stringResource(theme.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clicksOnKeys(onClick)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Swatch(swatch)
        Text(
            name,
            style = SlowDownerTheme.type.control,
            color = colors.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(painterResource(Res.drawable.ic_h_check), contentDescription = null, tint = colors.amber, modifier = Modifier.size(16.dp))
        } else {
            Spacer(Modifier.size(16.dp))
        }
    }
}

/** The theme's background with its primary, secondary and other two loop colours as dots. */
@Composable
private fun Swatch(palette: DownstepColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .size(width = 48.dp, height = 24.dp)
            .clip(SwatchShape)
            .background(palette.bg, SwatchShape)
            .border(1.dp, palette.surface4, SwatchShape),
    ) {
        palette.loopColors.forEach { color ->
            Box(Modifier.size(DOT).background(color, CircleShape))
        }
    }
}

private val MENU_WIDTH = 248.dp
private val MODE_HEIGHT = DownstepDimens.chip - 4.dp
private val ROW_HEIGHT = 40.dp
private val DOT = 7.dp
private val SwatchShape = RoundedCornerShape(6.dp)
