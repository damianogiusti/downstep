package com.damianogiusti.slowdowner.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

internal val SlowDownerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Radii of the design (palette.json "radii"): pills for state, cards for settings. */
object DownstepShapes {
    /** Toggles, chips, badges, segmented pills: 999px. */
    val pill = CircleShape
    /** Settings cards (Speed, Pitch, Isolate, Progression, Notes). */
    val card = RoundedCornerShape(20.dp)
    /** Square-ish buttons: −5 / +5, −1 / +1, reset. */
    val control = RoundedCornerShape(10.dp)
    /** Library drawer rows. */
    val row = RoundedCornerShape(14.dp)
    /** Artwork tiles in the drawer. */
    val artwork = RoundedCornerShape(12.dp)
    /** Notes text area, suggestion outlines. */
    val field = RoundedCornerShape(12.dp)
}
