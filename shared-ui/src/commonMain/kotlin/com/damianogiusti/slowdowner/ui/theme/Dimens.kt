package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.ui.unit.dp

/** Sizes of the design: README §1 "Spacing & sizes" and palette.json "sizes". */
object DownstepDimens {
    /** Left and right page margin. */
    val gutter = 24.dp
    val headerControl = 40.dp
    val chip = 34.dp
    val waveform = 200.dp
    val zoomStrip = 96.dp
    val playButton = 68.dp
    /** Also the minimum hit target in the transport. */
    val transportButton = 44.dp
    val loopTool = 30.dp
    val drawerWidth = 360.dp
    /** The library drawer never covers more than this share of the window. */
    const val DRAWER_MAX_FRACTION = 0.88f
    val cardGap = 12.dp
    val cardPaddingHorizontal = 20.dp
    val cardPaddingVertical = 16.dp
    /** Title bar strip of the design (the OS draws its own on desktop). */
    val titleBar = 30.dp
}
