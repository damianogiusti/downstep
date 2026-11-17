package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

/** A card's Fine pill: on, it fills with the card's [accent]. */
internal class FineToggleState(val isOn: Boolean, val isEnabled: Boolean, val accent: Color, val onAccent: Color)

// A flat pill, not a FilterChip: the chip's elevation and label layer looked heavy on the cards.
/** Finer control of a card: 5-cent pitch steps, or a zoom on the loop. */
@Composable
internal fun FineToggle(state: FineToggleState, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val contentColor = when {
        !state.isEnabled -> colors.onSurface.copy(alpha = 0.38f)
        state.isOn -> state.onAccent
        else -> colors.onSurfaceVariant
    }
    Surface(
        checked = state.isOn,
        onCheckedChange = onToggle,
        enabled = state.isEnabled,
        shape = CircleShape,
        color = if (state.isOn && state.isEnabled) state.accent else colors.surfaceContainerHigh,
        contentColor = contentColor,
    ) {
        Text(
            stringResource(Res.string.fine),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
