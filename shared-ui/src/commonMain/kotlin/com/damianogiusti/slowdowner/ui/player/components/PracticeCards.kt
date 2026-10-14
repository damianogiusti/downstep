package com.damianogiusti.slowdowner.ui.player.components

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.SLIDER_MAX
import com.damianogiusti.slowdowner.ui.common.ValueSlider
import com.damianogiusti.slowdowner.ui.common.formatClock
import com.damianogiusti.slowdowner.ui.common.formatPitch
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.player.PlayerControlsState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.tabular

/** Callbacks of a [ValueCard]: slider position, ±1 step, back to default. */
class ValueCardActions(
    val onSliderChange: (Int) -> Unit,
    val onStep: (Int) -> Unit,
    val onReset: () -> Unit,
)

/** Speed or pitch: big value, the engine slider, and two step buttons. */
@Composable
fun ValueCard(
    label: String,
    value: String,
    sliderPosition: Int,
    accent: Color,
    stepLabels: Pair<String, String>,
    actions: ValueCardActions,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = actions.onReset, enabled = enabled) {
                Icon(
                    painterResource(Res.drawable.ic_restart),
                    contentDescription = stringResource(Res.string.reset_value, label),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(value, style = MaterialTheme.typography.headlineLarge.tabular(), color = accent)
        ValueSlider(
            value = sliderPosition,
            max = SLIDER_MAX,
            onValueChange = actions.onSliderChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = SlowDownerTheme.colors.timelineTrack,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
            StepButton(stepLabels.first, enabled, Modifier.weight(1f)) { actions.onStep(-1) }
            StepButton(stepLabels.second, enabled, Modifier.weight(1f)) { actions.onStep(1) }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.height(40.dp),
        contentPadding = PaddingValues(0.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge.tabular()) }
}

@Composable
fun SpeedCard(controls: PlayerControlsState, actions: ValueCardActions, enabled: Boolean, modifier: Modifier = Modifier) {
    ValueCard(
        label = stringResource(Res.string.speed),
        value = formatSpeed(controls.speedPercent),
        sliderPosition = controls.speedSlider,
        accent = MaterialTheme.colorScheme.primary,
        stepLabels = "−5%" to "+5%",
        actions = actions,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
fun PitchCard(controls: PlayerControlsState, actions: ValueCardActions, enabled: Boolean, modifier: Modifier = Modifier) {
    ValueCard(
        label = stringResource(Res.string.pitch),
        value = formatPitch(controls.pitchSemitones),
        sliderPosition = controls.pitchSlider,
        accent = MaterialTheme.colorScheme.tertiary,
        stepLabels = "−1" to "+1",
        actions = actions,
        enabled = enabled,
        modifier = modifier,
    )
}

/** Callbacks of the [LoopCard]. */
class LoopActions(
    val onToggle: (Boolean) -> Unit,
    val onMarkStart: () -> Unit,
    val onMarkEnd: () -> Unit,
    val onShiftBackward: () -> Unit,
    val onHalve: () -> Unit,
    val onDouble: () -> Unit,
    val onShiftForward: () -> Unit,
)

@Composable
fun LoopCard(controls: PlayerControlsState, actions: LoopActions, enabled: Boolean, modifier: Modifier = Modifier) {
    val loopLength = (controls.loopEnd - controls.loopStart) * controls.durationSeconds
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.loop_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (controls.hasLoopPoints) {
                        stringResource(
                            Res.string.loop_summary,
                            formatClock(controls.loopStart * controls.durationSeconds),
                            formatClock(controls.loopEnd * controls.durationSeconds),
                            loopLength.toInt(),
                        )
                    } else {
                        stringResource(Res.string.loop_hint)
                    },
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = controls.isLoopOn, onCheckedChange = actions.onToggle, enabled = enabled)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MarkerButton("A", stringResource(Res.string.loop_mark_start), SlowDownerTheme.colors.markerA, enabled, actions.onMarkStart)
            MarkerButton("B", stringResource(Res.string.loop_mark_end), SlowDownerTheme.colors.markerB, enabled, actions.onMarkEnd)
            Spacer(Modifier.weight(1f))
            val loopEnabled = enabled && controls.isLoopOn
            LoopIconButton(Res.drawable.ic_chevron_left, Res.string.loop_shift_backward_description, loopEnabled, actions.onShiftBackward)
            LoopTextButton("½", Res.string.loop_halve_description, loopEnabled, actions.onHalve)
            LoopTextButton("×2", Res.string.loop_double_description, loopEnabled, actions.onDouble)
            LoopIconButton(Res.drawable.ic_chevron_right, Res.string.loop_shift_forward_description, loopEnabled, actions.onShiftForward)
        }
    }
}

@Composable
private fun MarkerButton(label: String, description: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = color.copy(alpha = 0.16f),
            contentColor = color,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .size(width = 52.dp, height = 40.dp)
            .semantics { contentDescription = description },
    ) { Text(label, style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun LoopIconButton(icon: DrawableResource, description: StringResource, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, colors = neutralTonalIconColors(), modifier = Modifier.size(40.dp)) {
        Icon(painterResource(icon), contentDescription = stringResource(description), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LoopTextButton(label: String, description: StringResource, enabled: Boolean, onClick: () -> Unit) {
    val text = stringResource(description)
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = neutralTonalIconColors(),
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = text },
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun neutralTonalIconColors() = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
)
