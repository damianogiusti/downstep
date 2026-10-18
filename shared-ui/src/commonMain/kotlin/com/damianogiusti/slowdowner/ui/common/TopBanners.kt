package com.damianogiusti.slowdowner.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Banners at the window's top (an update, no audio output), above [content], whose header is the
 * window's title bar where the window has one ([LocalWindowTitleBar]). While a banner shows, the
 * banners are the window's top: they drag the window where they have no control, and carry the
 * window buttons in the window's corner; [content]'s header keeps dragging the window, without the
 * buttons. [banners] get the room the buttons take at their right end ([endInset]), and compose
 * nothing when there is nothing to say.
 */
@Composable
fun TopBanners(
    banners: @Composable ColumnScope.(endInset: Dp) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val titleBar = LocalWindowTitleBar.current
    // Changes when a banner comes or goes, not per frame.
    var hasBanner by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { hasBanner = it.height > 0 }
                .then(if (titleBar != null) Modifier.titleBarCaption() else Modifier),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                banners(if (titleBar != null && hasBanner) titleBar.buttonsWidth else 0.dp)
            }
            if (titleBar != null && hasBanner) {
                Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.matchParentSize()) {
                    titleBar.WindowButtons(Modifier.fillMaxHeight())
                }
            }
        }
        val below = if (titleBar != null && hasBanner) titleBar.withoutButtons() else titleBar
        CompositionLocalProvider(LocalWindowTitleBar provides below) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}
