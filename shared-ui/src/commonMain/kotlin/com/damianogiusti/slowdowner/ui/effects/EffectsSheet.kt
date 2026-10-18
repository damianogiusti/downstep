package com.damianogiusti.slowdowner.ui.effects

import com.damianogiusti.slowdowner.ui.common.TitleBarOverlay
import com.damianogiusti.slowdowner.ui.common.formatSignedDecimal
import org.jetbrains.compose.resources.StringResource
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.SLIDER_MAX
import com.damianogiusti.slowdowner.ui.common.ValueSlider
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.tabular
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsSheet(
    onDismiss: () -> Unit,
    viewModel: EffectsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TitleBarOverlay()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // The state is null until the persisted settings are loaded.
        state?.let {
            EffectsSheetContent(
                state = it,
                onMixChanged = viewModel::onMixChanged,
                onMixReset = viewModel::onMixReset,
                onEqToggled = viewModel::onEqToggled,
                onEqReset = viewModel::onEqReset,
                onPreampChanged = viewModel::onPreampChanged,
                onBandChanged = viewModel::onBandChanged,
                onBandGainReset = viewModel::onBandGainReset,
                onQualitySelected = viewModel::onQualitySelected,
            )
        }
    }
}

@Composable
fun EffectsSheetContent(
    state: EffectsUiState,
    onMixChanged: (Int) -> Unit,
    onMixReset: () -> Unit,
    onEqToggled: (Boolean) -> Unit,
    onEqReset: () -> Unit,
    onPreampChanged: (Float) -> Unit,
    onBandChanged: (index: Int, band: EqBand) -> Unit,
    onBandGainReset: (index: Int) -> Unit,
    onQualitySelected: (ProcessingQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(stringResource(Res.string.effects), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(if (state.trackId != null) Res.string.effects_subtitle else Res.string.effects_subtitle_default),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(Res.string.mix_title), Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.mix_format, 100 - state.mixRightPercent, state.mixRightPercent),
                style = MaterialTheme.typography.labelMedium.tabular(),
            )
            TextButton(onClick = onMixReset) { Text(stringResource(Res.string.reset)) }
        }
        ValueSlider(value = state.mixSlider, max = SLIDER_MAX, onValueChange = onMixChanged, modifier = Modifier.fillMaxWidth())

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(
                text = stringResource(Res.string.eq_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEqReset) { Text(stringResource(Res.string.reset)) }
            Switch(checked = settings.eq.enabled, onCheckedChange = onEqToggled)
        }
        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            EqGraph(
                curve = settings.eq,
                onBandChanged = onBandChanged,
                onBandGainReset = onBandGainReset,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            GainRow(stringResource(Res.string.eq_preamp), settings.eq.preampDb, MaterialTheme.colorScheme.tertiary, onPreampChanged)
        }

        SectionLabel(stringResource(Res.string.quality_title), Modifier.padding(top = 20.dp))
        Text(
            stringResource(Res.string.quality_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QualityChoice(
            selected = settings.quality,
            onSelected = onQualitySelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun GainRow(label: String, gainDb: Float, accent: Color, onGainChanged: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        ValueSlider(
            value = dbToSlider(gainDb),
            max = GAIN_STEPS,
            onValueChange = { onGainChanged(sliderToDb(it)) },
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.gain_format, formatSignedDecimal(gainDb, stringResource(Res.string.decimal_separator))),
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = accent,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun QualityChoice(
    selected: ProcessingQuality,
    onSelected: (ProcessingQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        ProcessingQuality.entries.forEachIndexed { index, quality ->
            SegmentedButton(
                selected = quality == selected,
                onClick = { onSelected(quality) },
                shape = SegmentedButtonDefaults.itemShape(index, ProcessingQuality.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(stringResource(quality.label))
            }
        }
    }
}

private val ProcessingQuality.label: StringResource
    get() = when (this) {
        ProcessingQuality.LOW -> Res.string.quality_low
        ProcessingQuality.MEDIUM -> Res.string.quality_medium
        ProcessingQuality.HIGH -> Res.string.quality_high
        ProcessingQuality.VERY_HIGH -> Res.string.quality_very_high
    }

// 0.1 dB per step over the preamp's ±12 dB range.
private const val GAIN_STEPS = 240
private const val GAIN_RANGE_DB = EqCurve.MAX_PREAMP_DB - EqCurve.MIN_PREAMP_DB

private fun sliderToDb(position: Int) = EqCurve.MIN_PREAMP_DB + position * GAIN_RANGE_DB / GAIN_STEPS

private fun dbToSlider(gainDb: Float) = ((gainDb - EqCurve.MIN_PREAMP_DB) * GAIN_STEPS / GAIN_RANGE_DB).roundToInt()

@Preview(showBackground = true)
@Composable
private fun EffectsSheetContentPreview() {
    SlowDownerTheme {
        Surface {
            EffectsSheetContent(
                state = EffectsUiState(
                    settings = EffectsSettings(
                        eq = EqCurve.fromLegacyGains(enabled = true, preampDb = -2f, gainsDb = listOf(3f, 1.5f, 0f, -2f, 0f, 4.5f, 6f)),
                    ),
                    mixSlider = SLIDER_MAX / 2,
                    mixRightPercent = 50,
                    trackId = "preview",
                ),
                onMixChanged = {},
                onMixReset = {},
                onEqToggled = {},
                onEqReset = {},
                onPreampChanged = {},
                onBandChanged = { _, _ -> },
                onBandGainReset = {},
                onQualitySelected = {},
            )
        }
    }
}
