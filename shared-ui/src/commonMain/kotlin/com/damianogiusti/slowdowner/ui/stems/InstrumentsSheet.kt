package com.damianogiusti.slowdowner.ui.stems

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
)

/** The sheet of the player: what the open track plays and the level of each instrument. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentsSheet(state: InstrumentsUiState.Ready, actions: InstrumentsActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        InstrumentsSheetContent(state, actions)
    }
}

@Composable
fun InstrumentsSheetContent(state: InstrumentsUiState.Ready, actions: InstrumentsActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(stringResource(Res.string.instruments), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(Res.string.instruments_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        ModeChoice(
            mode = state.mode,
            isEnabled = !state.isSwitching,
            onSelected = actions.onModeSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isSwitching) {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(Res.string.instruments_switching),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (state.mode == TrackMode.FULL_TRACK) {
            Text(
                stringResource(Res.string.instruments_full_track_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(
            checked = row.isMuted,
            onCheckedChange = { actions.onMuteToggled(row.kind) },
            enabled = isEnabled,
        ) {
            Icon(
                painter = painterResource(if (row.isMuted) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up),
                contentDescription = stringResource(if (row.isMuted) Res.string.instruments_unmute else Res.string.instruments_mute, name),
                tint = when {
                    !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
                    row.isMuted -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(STEM_NAME_WIDTH),
        )
        ValueSlider(
            value = row.percent,
            max = MAX_PERCENT,
            onValueChange = { actions.onGainChanged(row.kind, it) },
            enabled = isEnabled,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = name },
        )
        Text(
            text = stringResource(Res.string.instruments_volume_format, row.percent),
            style = MaterialTheme.typography.labelMedium.tabular(),
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(PERCENT_WIDTH)
                .padding(end = 8.dp),
        )
    }
}

private val STEM_NAME_WIDTH = 72.dp
private val PERCENT_WIDTH = 48.dp
private const val MAX_PERCENT = 100
private const val DISABLED_ALPHA = 0.38f

@Preview(showBackground = true)
@Composable
private fun InstrumentsSheetContentPreview() {
    SlowDownerTheme {
        Surface {
            InstrumentsSheetContent(
                state = InstrumentsUiState.Ready(
                    trackId = "1",
                    mode = TrackMode.STEMS,
                    isSwitching = false,
                    stems = listOf(
                        InstrumentRow(StemKind.VOCALS, Res.string.stem_vocals, 0.8f),
                        InstrumentRow(StemKind.DRUMS, Res.string.stem_drums, 0f),
                        InstrumentRow(StemKind.BASS, Res.string.stem_bass, 1f),
                    ),
                ),
                actions = InstrumentsActions({}, { _, _ -> }, {}),
            )
        }
    }
}
