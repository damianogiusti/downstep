package com.damianogiusti.slowdowner.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

// PLACEHOLDER (group D): final signature, minimal body (the 1.x library inside the panel).
// See ui-contract.md, README §2.9, screenshots 05/06.

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
    if (!isOpen) return
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SlowDownerTheme.colors.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        Box(modifier = Modifier.fillMaxHeight().width(DownstepDimens.drawerWidth).background(SlowDownerTheme.colors.surface1)) {
            LibraryScreen(navigation = navigation, snackbarHostState = snackbarHostState)
        }
    }
}
