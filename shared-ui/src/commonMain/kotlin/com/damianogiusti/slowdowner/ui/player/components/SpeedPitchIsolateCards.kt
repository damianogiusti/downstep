package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.effects.IsolationBands
import com.damianogiusti.slowdowner.ui.common.ControlButton
import com.damianogiusti.slowdowner.ui.common.SectionLabel
import com.damianogiusti.slowdowner.ui.common.SettingsCard
import com.damianogiusti.slowdowner.ui.common.formatPitch
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.player.IsolateActions
import com.damianogiusti.slowdowner.ui.player.SpeedPitchActions
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.stringResource

// PLACEHOLDER (group C): final signatures, minimal bodies. See ui-contract.md, README §2.7.

/** Speed 25–200%: amber read-out, slider, −5 / +5, reset. */
@Composable
fun SpeedCard(
    speedPercent: Float,
    sliderPosition: Int,
    enabled: Boolean,
    actions: SpeedPitchActions,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier) {
        SectionLabel(stringResource(Res.string.speed))
        Text(formatSpeed(speedPercent), style = SlowDownerTheme.type.readout, color = SlowDownerTheme.colors.amber)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ControlButton("−5", { actions.onSpeedStep(-1) }, stringResource(Res.string.speed_down_description), enabled = enabled)
            ControlButton("+5", { actions.onSpeedStep(1) }, stringResource(Res.string.speed_up_description), enabled = enabled)
        }
    }
}

/** Pitch −12…+12 st: teal read-out, slider, −1 / +1, reset. */
@Composable
fun PitchCard(
    pitchSemitones: Float,
    sliderPosition: Int,
    enabled: Boolean,
    actions: SpeedPitchActions,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier) {
        SectionLabel(stringResource(Res.string.pitch))
        Text(formatPitch(pitchSemitones), style = SlowDownerTheme.type.readout, color = SlowDownerTheme.colors.teal)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ControlButton("−1", { actions.onPitchStep(-1) }, stringResource(Res.string.pitch_down_description), enabled = enabled)
            ControlButton("+1", { actions.onPitchStep(1) }, stringResource(Res.string.pitch_up_description), enabled = enabled)
        }
    }
}

/** Low / Mid / High solo toggles (teal when on); "Full mix" or "Low + Mid only". */
@Composable
fun IsolateCard(
    isolation: IsolationBands,
    enabled: Boolean,
    actions: IsolateActions,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier) {
        SectionLabel(stringResource(Res.string.isolate))
        Text(stringResource(Res.string.isolate_full_mix), style = SlowDownerTheme.type.meta, color = SlowDownerTheme.colors.fg2)
    }
}
