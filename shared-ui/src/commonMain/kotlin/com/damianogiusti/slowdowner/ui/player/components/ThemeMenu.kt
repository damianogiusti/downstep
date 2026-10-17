package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.common.TitleBarOverlay
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
import androidx.compose.runtime.Immutable
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
import com.damianogiusti.slowdowner.ui.theme.themeById
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The header's theme button (sun or moon: the mode on screen) and its menu: Dark / Light, then the
 * themes, each with a swatch of its colours in the mode on screen. What the pointer or the keyboard
 * focus is on is previewed across the app (the pointer's item first, see PreviewTarget); Esc or
 * closing the menu goes back to [selection]; a click, or Enter or Space on what is previewed, applies
 * and saves. A theme closes the menu, a mode keeps it open so a theme can follow.
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
            TitleBarOverlay()
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

/** An item of the menu, by what it changes, so it applies onto the selection of the moment. */
@Immutable
private sealed interface MenuItem {
    /** A theme closes the menu; a mode keeps it open so a theme can follow. */
    val closes: Boolean

    fun option(selection: ThemeSelection): ThemeSelection

    data class Mode(val mode: ThemeMode) : MenuItem {
        override val closes get() = false
        override fun option(selection: ThemeSelection) = selection.copy(mode = mode)
    }

    data class Theme(val id: String) : MenuItem {
        override val closes get() = true
        override fun option(selection: ThemeSelection) = selection.copy(themeId = id)
    }
}

/**
 * What the menu previews and what Enter or Space applies: always the same item, [shown]. The pointer's
 * item wins over the keyboard's; leaving it falls back to the focused item, which is the chosen theme
 * until a key moves the focus (so leaving the menu without touching the keyboard looks like a revert).
 * A key that moves the focus elsewhere drops the pointer's item until the pointer moves again.
 */
@Stable
private class PreviewTarget {
    private var hovered by mutableStateOf<MenuItem?>(null)
    private var focused by mutableStateOf<MenuItem?>(null)

    val shown: MenuItem? get() = hovered ?: focused

    /** Set once a pick closes the menu: its fade-out must not preview what the pointer crosses. */
    var isClosing = false

    fun enter(item: MenuItem) {
        if (!isClosing) hovered = item
    }

    fun exit(item: MenuItem) {
        if (hovered == item) hovered = null
    }

    fun focus(item: MenuItem) {
        if (isClosing) return
        focused = item
        if (hovered != item) hovered = null
    }

    fun blur(item: MenuItem) {
        if (focused == item) focused = null
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
    val currentSelection by rememberUpdatedState(selection)
    val currentOnPreview by rememberUpdatedState(onPreview)
    val currentOnPreviewEnd by rememberUpdatedState(onPreviewEnd)
    LaunchedEffect(target) {
        // drop(1): opening previews nothing. The option follows the selection: a mode applied meanwhile
        // must not come back when the pointer falls back to the focused theme.
        snapshotFlow { target.shown?.option(currentSelection) }.drop(1).collect {
            if (target.isClosing) return@collect
            if (it != null) currentOnPreview(it) else currentOnPreviewEnd()
        }
    }
    // Gone without a pick (or after one, harmless): back to the chosen theme.
    DisposableEffect(Unit) { onDispose { currentOnPreviewEnd() } }

    // The keyboard starts on the checked theme, so the arrows move from there.
    val checkedRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { checkedRow.requestFocus() } }

    val apply = { item: MenuItem ->
        if (item.closes) target.isClosing = true
        onApply(item.option(selection), item.closes)
    }
    Column(
        modifier = Modifier
            .width(MENU_WIDTH)
            .onPreviewKeyEvent { event ->
                // Enter and Space apply what is previewed, on release; here rather than in clickable, so the
                // window's shortcuts (Space plays) and the click never both run. The press is taken too.
                if (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar) {
                    val shown = target.shown ?: return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) apply(shown)
                    return@onPreviewKeyEvent true
                }
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
                val item = MenuItem.Mode(mode)
                ModeOption(
                    mode = mode,
                    selected = mode == selection.mode,
                    modifier = Modifier.weight(1f).previewsOn(item, target),
                    onClick = { apply(item) },
                )
            }
        }
        HorizontalDivider(color = colors.surface3, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        MenuLabel(stringResource(Res.string.theme_palettes))
        Column(Modifier.selectableGroup().padding(bottom = 4.dp)) {
            val mode = if (colors.isDark) ThemeMode.Dark else ThemeMode.Light
            // An unknown saved id (a newer version's theme) checks Downstep, which is what shows.
            val checkedId = themeById(selection.themeId).id
            Themes.forEach { theme ->
                val item = MenuItem.Theme(theme.id)
                val checked = theme.id == checkedId
                ThemeRow(
                    theme = theme,
                    swatch = theme.colors(mode),
                    selected = checked,
                    modifier = Modifier
                        .then(if (checked) Modifier.focusRequester(checkedRow) else Modifier)
                        .previewsOn(item, target),
                    onClick = { apply(item) },
                )
            }
        }
    }
}

/**
 * The pointer and the focus on [item] tell [target]. The items count as text input meanwhile: the
 * window's shortcuts leave the arrows, Enter, Space and Esc to the menu.
 */
private fun Modifier.previewsOn(item: MenuItem, target: PreviewTarget): Modifier = this
    .pointerInput(item) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    // A move too: the first event in a just opened popup is not always an Enter.
                    PointerEventType.Enter, PointerEventType.Move -> target.enter(item)
                    PointerEventType.Exit -> target.exit(item)
                }
            }
        }
    }
    .reportsTextInputFocus()
    .onFocusChanged { if (it.isFocused) target.focus(item) else target.blur(item) }

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
