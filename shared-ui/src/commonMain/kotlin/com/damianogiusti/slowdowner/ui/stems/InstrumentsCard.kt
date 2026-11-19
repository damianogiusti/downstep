package com.damianogiusti.slowdowner.ui.stems

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.ui.common.ValueSlider
import com.damianogiusti.slowdowner.ui.resources.*
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.tabular
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** What the panel can trigger. */
class InstrumentsActions(
    val onModeSelected: (TrackMode) -> Unit,
    val onGainChanged: (StemKind, Int) -> Unit,
    val onMuteToggled: (StemKind) -> Unit,
    val onSoloToggled: (StemKind) -> Unit,
)

/**
 * The Instruments card of the player, below the timeline: the switch between the full track and the instruments,
 * and one dense row per instrument. Always shown for a track that has stems; in the full-track mode the rows
 * stay but are disabled, so the switch is what explains them.
 */
@Composable
fun InstrumentsCard(state: InstrumentsUiState.Ready, actions: InstrumentsActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        ModeChoice(
            mode = state.mode,
            isEnabled = !state.isSwitching,
            onSelected = actions.onModeSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isSwitching) {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(Res.string.instruments_switching),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.padding(top = 4.dp)) {
            for (row in state.stems) {
                StemRow(row, isEnabled = state.areStemControlsEnabled, actions = actions)
            }
        }
    }
}

@Composable
private fun ModeChoice(mode: TrackMode, isEnabled: Boolean, onSelected: (TrackMode) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        TrackMode.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == mode,
                onClick = { onSelected(option) },
                enabled = isEnabled,
                shape = SegmentedButtonDefaults.itemShape(index, TrackMode.entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(stringResource(if (option == TrackMode.STEMS) Res.string.instruments_mode_stems else Res.string.instruments_mode_full_track))
            }
        }
    }
}

@Composable
private fun StemRow(row: InstrumentRow, isEnabled: Boolean, actions: InstrumentsActions) {
    val name = stringResource(row.name)
    // A silent stem, muted or silenced by another's solo, is drawn quieter; only its slider keeps working.
    val textColor = if (row.isSilent) MaterialTheme.colorScheme.onSurface.copy(alpha = SILENT_ALPHA) else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(ROW_HEIGHT)) {
        IconToggleButton(
            checked = row.isMuted,
            onCheckedChange = { actions.onMuteToggled(row.kind) },
            enabled = isEnabled,
            modifier = Modifier.size(MUTE_BUTTON_SIZE),
        ) {
            Icon(
                painter = painterResource(if (row.isMuted) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up),
                contentDescription = stringResource(if (row.isMuted) Res.string.instruments_unmute else Res.string.instruments_mute, name),
                modifier = Modifier.size(20.dp),
                tint = when {
                    !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                    row.isMuted -> MaterialTheme.colorScheme.onSurfaceVariant
                    row.isSilencedBySolo -> MaterialTheme.colorScheme.primary.copy(alpha = SILENT_ALPHA)
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
        SoloToggle(row, name, isEnabled, onToggled = { actions.onSoloToggled(row.kind) })
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 6.dp)
                .width(STEM_NAME_WIDTH),
        )
        ValueSlider(
            value = row.percent,
            max = MAX_PERCENT,
            onValueChange = { actions.onGainChanged(row.kind, it) },
            enabled = isEnabled,
            colors = if (row.isSilent) silentSliderColors() else SliderDefaults.colors(),
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
                .semantics { contentDescription = name },
        )
        Text(
            text = stringResource(Res.string.instruments_volume_format, row.percent),
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = textColor,
            textAlign = TextAlign.End,
            modifier = Modifier.width(PERCENT_WIDTH),
        )
    }
}

/** A small toggle that reads "Solo": filled with the primary color while the stem is soloed, outlined otherwise. */
@Composable
private fun SoloToggle(row: InstrumentRow, name: String, isEnabled: Boolean, onToggled: () -> Unit) {
    val description = stringResource(if (row.isSoloed) Res.string.instruments_unsolo else Res.string.instruments_solo_stem, name)
    Surface(
        checked = row.isSoloed,
        onCheckedChange = { onToggled() },
        enabled = isEnabled,
        shape = MaterialTheme.shapes.small,
        color = if (row.isSoloed) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (row.isSoloed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (row.isSoloed) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .padding(start = 2.dp)
            .height(SOLO_HEIGHT)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = stringResource(Res.string.instruments_solo),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun silentSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.outline,
    activeTrackColor = MaterialTheme.colorScheme.outline,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)

private val ROW_HEIGHT = 36.dp
private val MUTE_BUTTON_SIZE = 36.dp
private val SOLO_HEIGHT = 26.dp
private val STEM_NAME_WIDTH = 64.dp
private val PERCENT_WIDTH = 44.dp
private const val MAX_PERCENT = 100
private const val DISABLED_ALPHA = 0.38f
private const val SILENT_ALPHA = 0.5f

@Preview(showBackground = true)
@Composable
private fun InstrumentsCardPreview() {
    SlowDownerTheme {
        Surface {
            InstrumentsCard(
                state = InstrumentsUiState.Ready(
                    trackId = "1",
                    mode = TrackMode.STEMS,
                    isSwitching = false,
                    stems = listOf(
                        InstrumentRow(StemKind.VOCALS, Res.string.stem_vocals, 0.8f, isSoloed = true),
                        InstrumentRow(StemKind.DRUMS, Res.string.stem_drums, 0.5f, isMuted = true),
                        InstrumentRow(StemKind.BASS, Res.string.stem_bass, 1f, isSilencedBySolo = true),
                    ),
                ),
                actions = InstrumentsActions({}, { _, _ -> }, {}, {}),
            )
        }
    }
}
