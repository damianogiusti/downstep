package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.domain.effects.IsolationBands
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.ui.common.ControlButton
import com.damianogiusti.slowdowner.ui.common.DISABLED_ALPHA
import com.damianogiusti.slowdowner.ui.common.SectionLabel
import com.damianogiusti.slowdowner.ui.common.SettingsCard
import com.damianogiusti.slowdowner.ui.common.fillRemainingHeight
import com.damianogiusti.slowdowner.ui.common.Tooltip
import com.damianogiusti.slowdowner.ui.common.formatPitch
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.player.IsolateActions
import com.damianogiusti.slowdowner.ui.player.SpeedPitchActions
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Speed 25–200%: amber read-out, slider, −5 / +5, reset (README §2.7). */
@Composable
fun SpeedCard(
    speedPercent: Float,
    sliderPosition: Int,
    enabled: Boolean,
    actions: SpeedPitchActions,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.speed)
    ValueCard(
        label = label,
        readout = formatSpeed(speedPercent),
        accent = SlowDownerTheme.colors.amber,
        sliderPosition = sliderPosition,
        onSlider = actions.onSpeedSlider,
        enabled = enabled,
        modifier = modifier,
    ) {
        ControlButton("−5", { actions.onSpeedStep(-1) }, stringResource(Res.string.speed_down_description), enabled = enabled)
        ControlButton("+5", { actions.onSpeedStep(1) }, stringResource(Res.string.speed_up_description), enabled = enabled)
        ResetButton(label, actions.onSpeedReset, enabled)
    }
}

/** Pitch −12…+12 st: teal read-out, slider, −1 / +1, reset (README §2.7). */
@Composable
fun PitchCard(
    pitchSemitones: Float,
    sliderPosition: Int,
    enabled: Boolean,
    actions: SpeedPitchActions,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.pitch)
    ValueCard(
        label = label,
        readout = formatPitch(pitchSemitones),
        accent = SlowDownerTheme.colors.teal,
        sliderPosition = sliderPosition,
        onSlider = actions.onPitchSlider,
        enabled = enabled,
        modifier = modifier,
    ) {
        ControlButton("−1", { actions.onPitchStep(-1) }, stringResource(Res.string.pitch_down_description), enabled = enabled)
        ControlButton("+1", { actions.onPitchStep(1) }, stringResource(Res.string.pitch_up_description), enabled = enabled)
        ResetButton(label, actions.onPitchReset, enabled)
    }
}

/** Low / Mid / High solo toggles (teal when on); "Full mix" or "Low + Mid only" (README §2.7, §4.7). */
@Composable
fun IsolateCard(
    isolation: IsolationBands,
    enabled: Boolean,
    actions: IsolateActions,
    modifier: Modifier = Modifier,
) {
    val low = stringResource(Res.string.band_low)
    val mid = stringResource(Res.string.band_mid)
    val high = stringResource(Res.string.band_high)
    val selected = listOfNotNull(low.takeIf { isolation.low }, mid.takeIf { isolation.mid }, high.takeIf { isolation.high })
    val summary = if (selected.isEmpty()) {
        stringResource(Res.string.isolate_full_mix)
    } else {
        stringResource(Res.string.isolate_only, selected.joinToString(" + "))
    }
    SettingsCard(modifier, spacing = 10.dp) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            modifier = Modifier.fillRemainingHeight(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(stringResource(Res.string.isolate))
                Text(
                    summary,
                    style = SlowDownerTheme.type.meta,
                    color = SlowDownerTheme.colors.fg2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DownstepShapes.pill)
                    .background(SlowDownerTheme.colors.bg, DownstepShapes.pill)
                    .alpha(if (enabled) 1f else DISABLED_ALPHA)
                    .padding(BAND_PILL_PADDING),
            ) {
                BandToggle(low, isolation.low, enabled) { actions.onIsolationChanged(isolation.copy(low = !isolation.low)) }
                BandToggle(mid, isolation.mid, enabled) { actions.onIsolationChanged(isolation.copy(mid = !isolation.mid)) }
                BandToggle(high, isolation.high, enabled) { actions.onIsolationChanged(isolation.copy(high = !isolation.high)) }
            }
        }
    }
}

/** Label + big read-out | slider | buttons: the shared shape of the Speed and Pitch cards. */
@Composable
private fun ValueCard(
    label: String,
    readout: String,
    accent: Color,
    sliderPosition: Int,
    onSlider: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    buttons: @Composable RowScope.() -> Unit,
) {
    SettingsCard(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillRemainingHeight(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.widthIn(min = 96.dp)) {
                SectionLabel(label)
                Text(readout, style = SlowDownerTheme.type.readout, color = accent, maxLines = 1)
            }
            CardSlider(
                position = sliderPosition,
                accent = accent,
                enabled = enabled,
                onPosition = onSlider,
                contentDescription = label,
                modifier = Modifier.weight(1f).widthIn(min = 60.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = buttons)
        }
    }
}

/**
 * Slider over the engine's positions 0..[PlaybackEngine.SLIDER_MAX]: 16dp thumb in [accent], 4dp
 * track at 25%. Every move is reported (the change must be audible while dragging), but the thumb
 * follows the finger from local state read in draw code, so neither the model's echo nor the drag
 * recomposes anything.
 */
@Composable
private fun CardSlider(
    position: Int,
    accent: Color,
    enabled: Boolean,
    onPosition: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val currentPosition by rememberUpdatedState(position)
    val currentOnPosition by rememberUpdatedState(onPosition)
    // NaN = not dragging: draw the model's position.
    val dragFraction = remember { mutableFloatStateOf(Float.NaN) }
    Box(
        modifier = modifier
            .height(SLIDER_HEIGHT)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 0f..PlaybackEngine.SLIDER_MAX.toFloat())
                if (!enabled) disabled()
                setProgress { target ->
                    if (enabled) currentOnPosition(target.roundToInt().coerceIn(0, PlaybackEngine.SLIDER_MAX))
                    enabled
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val thumb = THUMB_SIZE.toPx()
                var lastSent = -1
                fun moveTo(x: Float) {
                    val fraction = ((x - thumb / 2) / (size.width - thumb).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    dragFraction.floatValue = fraction
                    val target = (fraction * PlaybackEngine.SLIDER_MAX).roundToInt()
                    if (target != lastSent) {
                        lastSent = target
                        currentOnPosition(target)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    lastSent = -1
                    moveTo(down.position.x)
                    down.consume()
                    horizontalDrag(down.id) { change ->
                        moveTo(change.position.x)
                        change.consume()
                    }
                    dragFraction.floatValue = Float.NaN
                }
            }
            .drawBehind {
                val thumb = THUMB_SIZE.toPx()
                val track = TRACK_HEIGHT.toPx()
                val drag = dragFraction.floatValue
                val fraction = if (drag.isNaN()) currentPosition.toFloat() / PlaybackEngine.SLIDER_MAX else drag
                val cy = size.height / 2
                drawRoundRect(
                    color = accent.copy(alpha = 0.25f),
                    topLeft = Offset(0f, cy - track / 2),
                    size = Size(size.width, track),
                    cornerRadius = CornerRadius(track / 2),
                )
                val cx = thumb / 2 + fraction.coerceIn(0f, 1f) * (size.width - thumb)
                drawCircle(accent, radius = thumb / 2, center = Offset(cx, cy))
            },
    )
}

/** 36dp transparent reset (↻), surface3 on hover. */
@Composable
private fun ResetButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    val description = stringResource(Res.string.reset_value, label)
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    Tooltip(description) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(DownstepShapes.control)
                .background(if (isHovered && enabled) SlowDownerTheme.colors.surface3 else Color.Transparent, DownstepShapes.control)
                .hoverable(interaction)
                .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .semantics { contentDescription = description },
        ) {
            Icon(painterResource(Res.drawable.ic_c_reset), contentDescription = null, tint = SlowDownerTheme.colors.fg2, modifier = Modifier.size(15.dp))
        }
    }
}

/** One of the three equal 32dp toggles of the Isolate pill: teal / onTeal when on. */
@Composable
private fun RowScope.BandToggle(text: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val colors = SlowDownerTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .height(32.dp)
            .clip(DownstepShapes.pill)
            .background(if (checked) colors.teal else Color.Transparent, DownstepShapes.pill)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Text(
            text,
            style = SlowDownerTheme.type.tag.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (checked) colors.onTeal else colors.fg2,
            maxLines = 1,
        )
    }
}

private val BAND_PILL_PADDING = 3.dp
private val SLIDER_HEIGHT = 24.dp
private val THUMB_SIZE = 16.dp
private val TRACK_HEIGHT = 4.dp
