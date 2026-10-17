package com.damianogiusti.slowdowner.ui.common

import androidx.compose.ui.input.pointer.PointerIcon

// Pointer icons common code has no name for (it only knows Default, Crosshair, Text and Hand).

/** Over something that moves sideways as a whole: a loop's top bar. */
internal expect val MovePointerIcon: PointerIcon

/** Over an edge that resizes sideways: a loop's A or B. */
internal expect val ResizeHorizontalPointerIcon: PointerIcon
