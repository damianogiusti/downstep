package com.damianogiusti.slowdowner.ui.library

import com.damianogiusti.slowdowner.ui.common.TitleBarOverlay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

/**
 * The library as a 360dp drawer over the left edge, with a scrim: title, search, close; Tracks /
 * Playlists segmented pill; rows with artwork tile, title, meta (source · loop count), the current
 * track in amber; full-width amber Import at the bottom (the existing import sheet). Clicking the
 * scrim, ✕ or Esc calls [onClose]; picking a track loads it (the ViewModel closes the drawer).
 * Draws nothing while closed, and animates in and out.
 */
@Composable
fun LibraryDrawer(
    isOpen: Boolean,
    navigation: LibraryNavigation,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // Outside the animated content, so the drawer reopens on the tab it was closed on.
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Tracks) }
    val onTabSelected = remember { { selected: LibraryTab -> tab = selected } }
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isOpen, enter = fadeIn(tween(DURATION_MS)), exit = fadeOut(tween(DURATION_MS))) {
            // The scrim covers the header: a click there closes the drawer, it does not move the window.
            TitleBarOverlay()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SlowDownerTheme.colors.scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(tween(DURATION_MS)) { -it },
            exit = slideOutHorizontally(tween(DURATION_MS)) { -it },
        ) {
            BoxWithConstraints {
                val border = SlowDownerTheme.colors.surface3
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(min(DownstepDimens.drawerWidth, maxWidth * DownstepDimens.DRAWER_MAX_FRACTION))
                        .background(SlowDownerTheme.colors.surface1)
                        .drawBehind {
                            val x = size.width - 0.5.dp.toPx()
                            drawLine(border, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        }
                        // Takes the clicks on the panel's empty space, or they would reach the scrim and close it.
                        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                ) {
                    LibraryScreen(
                        navigation = navigation,
                        snackbarHostState = snackbarHostState,
                        tab = tab,
                        onTabSelected = onTabSelected,
                        onClose = onClose,
                    )
                }
            }
        }
    }
}

private const val DURATION_MS = 200
