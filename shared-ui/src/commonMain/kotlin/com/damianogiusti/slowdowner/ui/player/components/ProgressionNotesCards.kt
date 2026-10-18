package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.loops.ProgressionStatus
import com.damianogiusti.slowdowner.ui.common.CardTitle
import com.damianogiusti.slowdowner.ui.common.DISABLED_ALPHA
import com.damianogiusti.slowdowner.ui.common.SettingsCard
import com.damianogiusti.slowdowner.ui.common.Tooltip
import com.damianogiusti.slowdowner.ui.common.fillRemainingHeight
import com.damianogiusti.slowdowner.ui.common.leavesOnEscape
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.common.reportsTextInputFocus
import com.damianogiusti.slowdowner.ui.player.NotesActions
import com.damianogiusti.slowdowner.ui.player.NotesCardState
import com.damianogiusti.slowdowner.ui.player.ProgressionActions
import com.damianogiusti.slowdowner.ui.player.ProgressionCardState
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Progression of the active loop (README §2.8, §4.6): title and status line with the switch, one
 * sentence of pill-steppers ("From 70% to 100%, raise +3% every 2 passes"), the from → to track.
 * Every edit sends the whole progression; the ViewModel coerces it into range. [compact] (a short window)
 * leaves the track out: the status line already says where the ramp is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressionCard(
    state: ProgressionCardState,
    actions: ProgressionActions,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val progression = state.progression
    fun edit(change: Progression.() -> Progression) = actions.onProgressionChanged(state.loopId, progression.change())

    SettingsCard(modifier, spacing = if (compact) 8.dp else 12.dp, verticalPadding = if (compact) COMPACT_PADDING else DownstepDimens.cardPaddingVertical) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                CardTitle(stringResource(Res.string.progression), state.loopName)
                Text(
                    statusText(state.status),
                    style = SlowDownerTheme.type.control,
                    color = SlowDownerTheme.colors.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProgressionSwitch(progression.enabled, state.enabled) { edit { copy(enabled = !enabled) } }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SentenceWord(stringResource(Res.string.progression_from))
            Stepper(
                value = formatSpeed(progression.fromPercent),
                subject = stringResource(Res.string.progression_from_value),
                valueColor = SlowDownerTheme.colors.amber,
                minValueWidth = 44.dp,
                enabled = state.enabled,
                onDecrease = { edit { copy(fromPercent = fromPercent - Progression.FROM_TO_STEP_PERCENT) } },
                onIncrease = { edit { copy(fromPercent = fromPercent + Progression.FROM_TO_STEP_PERCENT) } },
            )
            SentenceWord(stringResource(Res.string.progression_to))
            Stepper(
                value = formatSpeed(progression.toPercent),
                subject = stringResource(Res.string.progression_to_value),
                valueColor = SlowDownerTheme.colors.amber,
                minValueWidth = 44.dp,
                enabled = state.enabled,
                onDecrease = { edit { copy(toPercent = toPercent - Progression.FROM_TO_STEP_PERCENT) } },
                onIncrease = { edit { copy(toPercent = toPercent + Progression.FROM_TO_STEP_PERCENT) } },
            )
            SentenceWord(stringResource(Res.string.progression_raise))
            Stepper(
                value = "+${formatSpeed(progression.stepPercent)}",
                subject = stringResource(Res.string.progression_step_value),
                valueColor = SlowDownerTheme.colors.fg,
                minValueWidth = 36.dp,
                enabled = state.enabled,
                onDecrease = { edit { copy(stepPercent = stepPercent - Progression.STEP_STEP_PERCENT) } },
                onIncrease = { edit { copy(stepPercent = stepPercent + Progression.STEP_STEP_PERCENT) } },
            )
            SentenceWord(stringResource(Res.string.progression_every))
            Stepper(
                value = progression.everyPasses.toString(),
                subject = stringResource(Res.string.progression_every_value),
                valueColor = SlowDownerTheme.colors.fg,
                minValueWidth = 24.dp,
                enabled = state.enabled,
                onDecrease = { edit { copy(everyPasses = everyPasses - Progression.EVERY_STEP_PASSES) } },
                onIncrease = { edit { copy(everyPasses = everyPasses + Progression.EVERY_STEP_PASSES) } },
            )
            SentenceWord(pluralStringResource(Res.plurals.progression_passes_word, progression.everyPasses))
        }
        if (!compact) {
            ProgressTrack(
                from = formatSpeed(progression.fromPercent),
                to = formatSpeed(progression.toPercent),
                fraction = if (progression.enabled) state.progressFraction else 0f,
                isOn = progression.enabled,
            )
        }
    }
}

/**
 * Free-text notes of the active loop (README §4.10). The field keeps its own text while typing (the
 * saved one arrives late, debounced by the ViewModel) and starts over from the saved text when the
 * active loop changes. [compact]: a shorter field, for a short window.
 */
@Composable
fun NotesCard(
    state: NotesCardState,
    actions: NotesActions,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var text by remember(state.loopId) { mutableStateOf(state.notes) }
    val colors = SlowDownerTheme.colors
    val textStyle = SlowDownerTheme.type.body.copy(fontSize = 13.sp, lineHeight = 19.sp, color = colors.fg)
    SettingsCard(modifier, spacing = if (compact) 6.dp else 8.dp, verticalPadding = if (compact) COMPACT_PADDING else DownstepDimens.cardPaddingVertical) {
        CardTitle(stringResource(Res.string.notes), state.loopName)
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                actions.onNotesChanged(state.loopId, it)
            },
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.amber),
            modifier = Modifier
                .fillMaxWidth()
                .fillRemainingHeight(min = if (compact) NOTES_MIN_HEIGHT_COMPACT else NOTES_MIN_HEIGHT)
                .leavesOnEscape()
                .reportsTextInputFocus(),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bg, DownstepShapes.field)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (text.isEmpty()) {
                        Text(stringResource(Res.string.notes_placeholder), style = textStyle, color = colors.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun statusText(status: ProgressionStatus): String = when (status) {
    ProgressionStatus.Off -> stringResource(Res.string.progression_off)
    is ProgressionStatus.Ramping -> pluralStringResource(
        Res.plurals.progression_ramping,
        status.passesUntilNextStep,
        status.passesUntilNextStep,
        formatSpeed(status.currentPercent),
        formatSpeed(status.nextStepPercent),
    )
    is ProgressionStatus.AtTarget -> stringResource(Res.string.progression_at_target, formatSpeed(status.percent))
}

/** 44×26 switch: amber with the knob right when on, surface3 with the knob left when off. */
@Composable
private fun ProgressionSwitch(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val description = stringResource(Res.string.progression_toggle)
    val colors = SlowDownerTheme.colors
    val knobOffset by animateDpAsState(if (checked) SWITCH_KNOB_ON else SWITCH_KNOB_OFF, tween(150))
    Tooltip(description) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(CircleShape)
                .background(if (checked) colors.amber else colors.surface3, CircleShape)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .semantics { contentDescription = description },
        ) {
            Box(
                modifier = Modifier
                    .offset(x = knobOffset, y = 3.dp)
                    .size(20.dp)
                    .background(colors.fg, CircleShape),
            )
        }
    }
}

/** A word of the progression sentence, centred on the 30dp steppers. */
@Composable
private fun SentenceWord(text: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(STEPPER_HEIGHT)) {
        Text(text, style = SlowDownerTheme.type.body, color = SlowDownerTheme.colors.fg2, maxLines = 1)
    }
}

/** "− 70% +" on `bg`: a 30dp pill with two 28dp buttons around the value. */
@Composable
private fun Stepper(
    value: String,
    subject: String,
    valueColor: Color,
    minValueWidth: Dp,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(STEPPER_HEIGHT)
            .clip(DownstepShapes.pill)
            .background(SlowDownerTheme.colors.bg, DownstepShapes.pill)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        StepButton("−", stringResource(Res.string.stepper_decrease, subject), enabled, onDecrease)
        Text(
            value,
            style = SlowDownerTheme.type.controlStrong.copy(fontSize = 14.sp),
            color = valueColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = minValueWidth),
        )
        StepButton("+", stringResource(Res.string.stepper_increase, subject), enabled, onIncrease)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = SlowDownerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val highlighted = isHovered && enabled
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(28.dp)
            .height(STEPPER_HEIGHT)
            .background(if (highlighted) colors.surface3 else Color.Transparent)
            .hoverable(interaction)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(
            symbol,
            style = SlowDownerTheme.type.body.copy(fontWeight = FontWeight.Normal),
            color = if (highlighted) colors.fg else colors.fg2,
        )
    }
}

/** "70% ━━━●────── 100%": where the ramp is between its start and its target. */
@Composable
private fun ProgressTrack(from: String, to: String, fraction: Float, isOn: Boolean) {
    val colors = SlowDownerTheme.colors
    val labelStyle = SlowDownerTheme.type.meta.copy(fontSize = 11.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(from, style = labelStyle, color = colors.fg2, maxLines = 1, modifier = Modifier.widthIn(min = 34.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(PROGRESS_DOT)
                .drawBehind {
                    val track = PROGRESS_TRACK.toPx()
                    val top = (size.height - track) / 2
                    val radius = CornerRadius(track / 2)
                    drawRoundRect(colors.bg, Offset(0f, top), Size(size.width, track), radius)
                    val x = fraction.coerceIn(0f, 1f) * size.width
                    if (x > 0f) drawRoundRect(colors.amber, Offset(0f, top), Size(x, track), radius)
                    val dot = PROGRESS_DOT.toPx() / 2
                    val center = Offset(x.coerceIn(dot, size.width - dot), size.height / 2)
                    drawCircle(colors.surface1, radius = dot, center = center)
                    drawCircle(if (isOn) colors.amber else colors.surface4, radius = dot - 2.dp.toPx(), center = center)
                },
        )
        Text(to, style = labelStyle, color = colors.fg2, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.widthIn(min = 34.dp))
    }
}

private val STEPPER_HEIGHT = 30.dp
private val SWITCH_KNOB_OFF = 3.dp
private val SWITCH_KNOB_ON = 21.dp
private val PROGRESS_TRACK = 4.dp
private val PROGRESS_DOT = 10.dp
private val NOTES_MIN_HEIGHT = 72.dp
private val NOTES_MIN_HEIGHT_COMPACT = 40.dp
private val COMPACT_PADDING = 10.dp
