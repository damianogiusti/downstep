package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.SLIDER_MAX
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.ui.common.ConfirmDialog
import com.damianogiusti.slowdowner.ui.common.EmptyState
import com.damianogiusti.slowdowner.ui.common.TextInputDialog
import com.damianogiusti.slowdowner.ui.common.TextInputDialogTexts
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.common.formatPitch
import com.damianogiusti.slowdowner.ui.effects.EffectsSheet
import com.damianogiusti.slowdowner.ui.effects.EffectsViewModel
import com.damianogiusti.slowdowner.ui.stems.InstrumentsActions
import com.damianogiusti.slowdowner.ui.stems.InstrumentsCard
import com.damianogiusti.slowdowner.ui.stems.InstrumentsUiState
import com.damianogiusti.slowdowner.ui.stems.InstrumentsViewModel
import com.damianogiusti.slowdowner.ui.player.components.ExportBanner
import com.damianogiusti.slowdowner.ui.player.components.LoopActions
import com.damianogiusti.slowdowner.ui.player.components.LoopCardState
import com.damianogiusti.slowdowner.ui.player.components.LoopCard
import com.damianogiusti.slowdowner.ui.player.components.LoopTimeline
import com.damianogiusti.slowdowner.ui.player.components.LoopTimelineState
import com.damianogiusti.slowdowner.ui.player.components.TimelineActions
import com.damianogiusti.slowdowner.ui.player.components.PitchCard
import com.damianogiusti.slowdowner.ui.player.components.PresetActions
import com.damianogiusti.slowdowner.ui.player.components.PresetChips
import com.damianogiusti.slowdowner.ui.player.components.SpeedCard
import com.damianogiusti.slowdowner.ui.player.components.TransportActions
import com.damianogiusti.slowdowner.ui.player.components.TransportRow
import com.damianogiusti.slowdowner.ui.player.components.ValueCardActions
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

/** Callbacks that leave the player screen or need the activity. */
class PlayerNavigation(
    /** Null when the player is a fixed pane beside the library (wide windows): nothing to collapse. */
    val onCollapse: (() -> Unit)?,
    val onPlayStop: () -> Unit,
    val onAddToPlaylist: (LibraryTrack) -> Unit,
)

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    effects: EffectsViewModel,
    navigation: PlayerNavigation,
    /** Null where the stems are not offered (Android): the Instruments card does not exist. */
    instruments: InstrumentsViewModel? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val instrumentsState = instruments?.state?.collectAsStateWithLifecycle()?.value
    // Only a track that has stems has the card.
    val readyInstruments = instrumentsState as? InstrumentsUiState.Ready
    var showEffects by rememberSaveable { mutableStateOf(false) }
    var showSavePreset by rememberSaveable { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<PracticePreset?>(null) }

    val timeline = TimelineActions(viewModel::onSeek, viewModel::onLoopRegionChanged)
    PlayerContent(
        state = state,
        actions = PlayerContentActions(
            transport = TransportActions(
                onPlayStop = navigation.onPlayStop,
                onRewind = viewModel::onRewindClicked,
                onNudge = viewModel::onNudge,
                onLoopToggled = viewModel::onLoopToggled,
            ),
            loop = LoopActions(
                onToggle = viewModel::onLoopToggled,
                onMarkStart = viewModel::onMarkLoopStartClicked,
                onMarkEnd = viewModel::onMarkLoopEndClicked,
                onShiftBackward = viewModel::onShiftLoopBackwardClicked,
                onHalve = viewModel::onHalveLoopClicked,
                onDouble = viewModel::onDoubleLoopClicked,
                onShiftForward = viewModel::onShiftLoopForwardClicked,
                onFineToggle = viewModel::onLoopFineToggled,
                timeline = timeline,
            ),
            speed = ValueCardActions(viewModel::onSpeedChanged, viewModel::onSpeedStep, viewModel::onSpeedReset),
            pitch = ValueCardActions(viewModel::onPitchChanged, viewModel::onPitchStep, viewModel::onPitchReset),
            onPitchFineToggle = viewModel::onPitchFineToggled,
            presets = PresetActions(
                onSave = { showSavePreset = true },
                onSelect = viewModel::onPresetSelected,
                onDelete = { presetToDelete = it },
            ),
            timeline = timeline,
            onCollapse = navigation.onCollapse,
            onShowEffects = { showEffects = true },
            instruments = instruments?.let { InstrumentsActions(it::onModeSelected, it::onGainChanged, it::onMuteToggled) },
            onExport = viewModel::onExportClicked,
            onCancelExport = viewModel::onExportCancelClicked,
            onAddToPlaylist = navigation.onAddToPlaylist,
        ),
        instruments = readyInstruments,
    )

    if (showEffects) EffectsSheet(onDismiss = { showEffects = false }, viewModel = effects)
    if (showSavePreset) {
        val settings = state.controls.practiceSettings
        TextInputDialog(
            texts = TextInputDialogTexts(
                title = stringResource(Res.string.preset_save_title),
                label = stringResource(Res.string.name),
                confirm = stringResource(Res.string.save),
                message = stringResource(Res.string.preset_save_message),
            ),
            initialText = suggestedPresetName(settings, stringResource(Res.string.decimal_separator)),
            onConfirm = {
                viewModel.onSavePreset(it)
                showSavePreset = false
            },
            onDismiss = { showSavePreset = false },
        )
    }
    presetToDelete?.let { preset ->
        ConfirmDialog(
            text = stringResource(Res.string.preset_delete_confirm, preset.name),
            confirm = stringResource(Res.string.delete),
            onConfirm = {
                viewModel.onPresetDeleteConfirmed(preset)
                presetToDelete = null
            },
            onDismiss = { presetToDelete = null },
        )
    }
}

private fun suggestedPresetName(settings: PracticeSettings, separator: String) =
    "${formatSpeed(settings.speedPercent)}, ${formatPitch(settings.pitchSemitones, separator)}"

/** Everything the player content can trigger. */
class PlayerContentActions(
    val transport: TransportActions,
    val loop: LoopActions,
    val speed: ValueCardActions,
    val pitch: ValueCardActions,
    val onPitchFineToggle: (Boolean) -> Unit,
    val presets: PresetActions,
    /** Seeking and moving the loop on the timeline. */
    val timeline: TimelineActions,
    val onCollapse: (() -> Unit)?,
    val onShowEffects: () -> Unit,
    /** Null hides the Instruments card with the platform that has no stems; see [PlayerContent]. */
    val instruments: InstrumentsActions? = null,
    val onExport: () -> Unit,
    val onCancelExport: () -> Unit,
    val onAddToPlaylist: (LibraryTrack) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContent(state: PlayerUiState, actions: PlayerContentActions, instruments: InstrumentsUiState.Ready? = null) {
    val controls = state.controls
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    actions.onCollapse?.let { onCollapse ->
                        IconButton(onClick = onCollapse) {
                            Icon(painterResource(Res.drawable.ic_expand_more), stringResource(Res.string.collapse_player))
                        }
                    }
                },
                title = {
                    Text(
                        stringResource(Res.string.player_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                actions = {
                    state.track?.let { track ->
                        IconButton(onClick = { actions.onAddToPlaylist(track) }) {
                            Icon(painterResource(Res.drawable.ic_playlist_add), stringResource(Res.string.add_to_playlist))
                        }
                    }
                    IconButton(onClick = actions.onShowEffects, enabled = !state.isExporting) {
                        Icon(painterResource(Res.drawable.ic_tune), stringResource(Res.string.effects))
                    }
                    IconButton(onClick = actions.onExport, enabled = state.canExport) {
                        Icon(painterResource(Res.drawable.ic_share), stringResource(Res.string.export))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        // Before the closed check: the outgoing file is already closed while the new one decodes.
        if (state.isOpening) {
            OpeningIndicator(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
            return@Scaffold
        }
        if (!controls.isOpen) {
            EmptyState(
                icon = Res.drawable.ic_music_note,
                title = stringResource(Res.string.no_track),
                message = stringResource(Res.string.no_track_hint),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                actions.onCollapse?.let { onCollapse ->
                    Button(onClick = onCollapse, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.go_to_library))
                    }
                }
            }
            return@Scaffold
        }
        // The render reads the live settings: freeze them until it is done.
        val enabled = !state.isExporting
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    state.track?.title ?: stringResource(Res.string.external_track),
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.track?.let { track ->
                    Text(
                        stringResource(if (track.sourceUrl != null) Res.string.source_youtube else Res.string.source_local),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            (state.export as? ExportUiState.Running)?.let {
                ExportBanner(it.fraction, actions.onCancelExport, Modifier.padding(horizontal = 16.dp))
            }
            LoopTimeline(
                state = LoopTimelineState(controls, state.waveform, isEnabled = enabled),
                actions = actions.timeline,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            // Between the timeline and the transport: a track with stems shows its mix with the practice controls.
            if (instruments != null && actions.instruments != null) {
                InstrumentsCard(instruments, actions.instruments, Modifier.padding(horizontal = 16.dp))
            }
            TransportRow(controls.isPlaying, controls.isLoopOn, enabled, actions.transport)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                LoopCard(LoopCardState(controls, state.loopZoom, isEnabled = enabled), actions.loop, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpeedCard(controls, actions.speed, enabled, Modifier.weight(1f))
                    PitchCard(controls, actions.pitch, actions.onPitchFineToggle, enabled, Modifier.weight(1f))
                }
            }
            PresetChips(state.presets, state.activePresetId, enabled && state.track != null, actions.presets)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OpeningIndicator(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(Res.string.opening_track),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(heightDp = 915)
@Composable
private fun PlayerContentPreview() {
    SlowDownerTheme(darkTheme = true) {
        PlayerContent(
            state = PlayerUiState(
                track = LibraryTrack("1", "Autumn Leaves", "/a", "https://youtu.be/x", 0),
                controls = PlayerControlsState(
                    isOpen = true,
                    position = 0.29f,
                    durationSeconds = 221f,
                    speedSlider = SLIDER_MAX / 3,
                    speedPercent = 75f,
                    pitchSlider = SLIDER_MAX * 2 / 5,
                    pitchSemitones = -2f,
                    isLoopOn = true,
                    loopStart = 0.19f,
                    loopEnd = 0.41f,
                ),
                presets = listOf(
                    PracticePreset(1, "1", "Slow, 60%", PracticeSettings(60f)),
                    PracticePreset(2, "1", "Solo, 75% −2", PracticeSettings(75f, -2f, LoopRegion(0.19f, 0.41f, true))),
                ),
            ),
            actions = PlayerContentActions(
                transport = TransportActions({}, {}, {}, {}),
                loop = LoopActions({}, {}, {}, {}, {}, {}, {}, {}, TimelineActions({}, { _, _ -> })),
                speed = ValueCardActions({}, {}, {}),
                pitch = ValueCardActions({}, {}, {}),
                onPitchFineToggle = {},
                presets = PresetActions({}, {}, {}),
                timeline = TimelineActions({}, { _, _ -> }),
                onCollapse = {},
                onShowEffects = {},
                onExport = {},
                onCancelExport = {},
                onAddToPlaylist = {},
            ),
        )
    }
}
