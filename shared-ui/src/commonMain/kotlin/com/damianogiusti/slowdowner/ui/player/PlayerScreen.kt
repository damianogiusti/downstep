package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.ui.common.ConfirmDialog
import com.damianogiusti.slowdowner.ui.common.TextInputDialog
import com.damianogiusti.slowdowner.ui.common.TextInputDialogTexts
import com.damianogiusti.slowdowner.ui.common.clearsFocusOnPress
import com.damianogiusti.slowdowner.ui.common.formatPitch
import com.damianogiusti.slowdowner.ui.common.formatSpeed
import com.damianogiusti.slowdowner.ui.effects.EffectsSheet
import com.damianogiusti.slowdowner.ui.effects.EffectsViewModel
import com.damianogiusti.slowdowner.ui.library.LibraryDrawer
import com.damianogiusti.slowdowner.ui.library.LibraryNavigation
import com.damianogiusti.slowdowner.ui.library.LibraryViewModel
import com.damianogiusti.slowdowner.ui.player.components.BpmDialog
import com.damianogiusti.slowdowner.ui.player.components.ExportBanner
import com.damianogiusti.slowdowner.ui.player.components.FocusHeader
import com.damianogiusti.slowdowner.ui.player.components.FocusReadouts
import com.damianogiusti.slowdowner.ui.player.components.IsolateCard
import com.damianogiusti.slowdowner.ui.player.components.KeyboardHints
import com.damianogiusti.slowdowner.ui.player.components.LoopChipsStrip
import com.damianogiusti.slowdowner.ui.player.components.LoopToolGroup
import com.damianogiusti.slowdowner.ui.player.components.NotesCard
import com.damianogiusti.slowdowner.ui.player.components.OverviewWaveform
import com.damianogiusti.slowdowner.ui.player.components.PassPill
import com.damianogiusti.slowdowner.ui.player.components.PitchCard
import com.damianogiusti.slowdowner.ui.player.components.PlayerHeader
import com.damianogiusti.slowdowner.ui.player.components.ProgressionCard
import com.damianogiusti.slowdowner.ui.player.components.SpeedCard
import com.damianogiusti.slowdowner.ui.player.components.TransportRow
import com.damianogiusti.slowdowner.ui.player.components.ZoomStrip
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.ThemeViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** What the player needs from outside: the activity's play/stop, the playlist sheet, the drawer's navigation. */
@Stable
class PlayerNavigation(
    /** Wraps play/stop where the platform needs it (notification permission on Android). */
    val onPlayStop: () -> Unit,
    val onAddToPlaylist: (LibraryTrack) -> Unit,
    val library: LibraryNavigation,
)

/**
 * The root screen (README §2): header, loop chips, overview waveform + zoom strip, transport, Speed /
 * Pitch / Isolate cards, Progression / Notes cards; focus mode keeps only the practice core (§4.9); the
 * library is a drawer over it (§2.9).
 *
 * Every part collects its own flow in a small host below, so a change recomposes only that part. The
 * playhead is passed down as a function and read in draw code (or the clock) only.
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    navigation: PlayerNavigation,
    snackbarHostState: SnackbarHostState,
    // Scoped to the window like the player: the Isolate overlay outlives the effects sheet.
    effects: EffectsViewModel = koinViewModel(),
    theme: ThemeViewModel = koinViewModel(),
) {
    val dialogs = remember { PlayerDialogs() }
    val actions = remember(viewModel, effects, theme, navigation, dialogs) {
        playerActions(viewModel, effects, theme, navigation, dialogs)
    }
    val positionState = viewModel.position.collectAsStateWithLifecycle()
    val position = remember(positionState) { positionState.reader() }
    val layout by viewModel.layout.collectAsStateWithLifecycle()

    // A click outside Notes (or the drawer's search) ends typing: the shortcuts work again.
    Box(modifier = Modifier.fillMaxSize().background(SlowDownerTheme.colors.bg).clearsFocusOnPress()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (layout.isFocusMode) {
                FocusHeaderHost(viewModel, actions.onExitFocus)
            } else {
                HeaderHost(viewModel, actions.header)
                LoadingHost(viewModel)
                ExportBannerHost(viewModel)
                ChipsHost(viewModel, actions.chips)
            }
            OverviewHost(viewModel, position, actions.waveform, if (layout.isFocusMode) DownstepDimens.waveformFocus else DownstepDimens.waveform)
            ZoomHost(viewModel, position, actions.waveform)
            TransportHost(viewModel, actions, layout.isFocusMode)
            if (!layout.isFocusMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DownstepDimens.cardGap),
                    // Cards of a row share its tallest card's height, as in the design.
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .padding(start = DownstepDimens.gutter, end = DownstepDimens.gutter, top = 4.dp, bottom = 12.dp),
                ) {
                    SpeedHost(viewModel, actions.speedPitch, Modifier.weight(1.2f))
                    PitchHost(viewModel, actions.speedPitch, Modifier.weight(1.2f))
                    IsolateHost(effects, actions.isolate, Modifier.weight(1f))
                }
                LoopCardsHost(viewModel, actions)
            }
        }
        LibraryDrawer(
            isOpen = layout.isDrawerOpen,
            navigation = navigation.library,
            onClose = actions.onCloseDrawer,
            snackbarHostState = snackbarHostState,
        )
    }

    PlayerDialogsHost(viewModel, effects, dialogs)
}

/** Dialogs and sheets the header opens; plain Compose state, never saved. */
@Stable
private class PlayerDialogs {
    var showEffects by mutableStateOf(false)
    var showSavePreset by mutableStateOf(false)
    var showBpm by mutableStateOf(false)
    var presetToDelete by mutableStateOf<PracticePreset?>(null)
}

private fun playerActions(
    viewModel: PlayerViewModel,
    effects: EffectsViewModel,
    theme: ThemeViewModel,
    navigation: PlayerNavigation,
    dialogs: PlayerDialogs,
) = PlayerActions(
    header = HeaderActions(
        onOpenLibrary = viewModel::onDrawerOpened,
        onEditBpm = { dialogs.showBpm = true },
        onAnalyze = viewModel::onAnalyzeClicked,
        onMetronomeToggled = viewModel::onMetronomeToggled,
        onCountInToggled = viewModel::onCountInToggled,
        onFocusToggled = viewModel::onFocusToggled,
        onThemeToggled = theme::onToggle,
        onSavePreset = { dialogs.showSavePreset = true },
        onPresetSelected = viewModel::onPresetSelected,
        onPresetDelete = { dialogs.presetToDelete = it },
        onExport = viewModel::onExportClicked,
        onShowEffects = { dialogs.showEffects = true },
        onAddToPlaylist = { viewModel.currentTrack()?.let(navigation.onAddToPlaylist) },
    ),
    chips = LoopChipActions(
        onSelect = viewModel::onLoopSelected,
        onNewLoop = viewModel::onNewLoopAtPlayhead,
        onRename = viewModel::onLoopRenamed,
        onDelete = viewModel::onLoopDeleted,
        onSuggestionAccepted = viewModel::onSuggestionAccepted,
        onSuggestionDismissed = viewModel::onSuggestionDismissed,
    ),
    waveform = WaveformActions(onSeek = viewModel::onSeek, onLoopBoundsChanged = viewModel::onLoopBoundsChanged),
    transport = TransportActions(
        onPlayPause = navigation.onPlayStop,
        onToStart = viewModel::onToStartClicked,
        onSeekBy = viewModel::onSeekBy,
        onNextLoop = viewModel::onNextLoopClicked,
        onLoopToggled = viewModel::onLoopToggled,
    ),
    loopTools = LoopToolActions(
        onNudge = viewModel::onNudgeLoop,
        onHalve = viewModel::onHalveLoop,
        onDouble = viewModel::onDoubleLoop,
        onDelete = viewModel::onDeleteActiveLoop,
        onSnapToggled = viewModel::onSnapToggled,
    ),
    speedPitch = SpeedPitchActions(
        onSpeedSlider = viewModel::onSpeedChanged,
        onSpeedStep = viewModel::onSpeedStep,
        onSpeedReset = viewModel::onSpeedReset,
        onPitchSlider = viewModel::onPitchChanged,
        onPitchStep = viewModel::onPitchStep,
        onPitchReset = viewModel::onPitchReset,
    ),
    isolate = IsolateActions(effects::onIsolationChanged),
    progression = ProgressionActions(viewModel::onProgressionChanged),
    notes = NotesActions(viewModel::onNotesChanged),
    onExitFocus = viewModel::onFocusToggled,
    onCloseDrawer = viewModel::onDrawerClosed,
)

// A function over the State, not its value: whoever calls it (draw code, a clock) is what re-runs.
private fun State<Float>.reader(): () -> Float = { value }

@Composable
private fun HeaderHost(viewModel: PlayerViewModel, actions: HeaderActions) {
    val state by viewModel.header.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val clicks by viewModel.clicks.collectAsStateWithLifecycle()
    val analysis by remember(viewModel) { viewModel.analysis.map { it.status }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(AnalysisStatus.Idle)
    PlayerHeader(
        state = state,
        trackCount = rememberTrackCount(),
        features = viewModel.features,
        analysis = analysis,
        clicks = clicks,
        presets = presets,
        // The theme on screen, whatever the ThemeViewModel was asked last.
        isDarkTheme = SlowDownerTheme.colors.isDark,
        actions = actions,
    )
}

/** The Library pill's count. The drawer's own ViewModel, so the library is read once. */
@Composable
private fun rememberTrackCount(): Int {
    val library = koinViewModel<LibraryViewModel>()
    val count by remember(library) { library.state.map { it.totalTracks }.distinctUntilChanged() }.collectAsStateWithLifecycle(0)
    return count
}

/** A thin bar while a track decodes: every control is frozen meanwhile. */
@Composable
private fun LoadingHost(viewModel: PlayerViewModel) {
    val isLoading by remember(viewModel) { viewModel.header.map { it.isLoading }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(false)
    if (isLoading) {
        LinearProgressIndicator(
            color = SlowDownerTheme.colors.amber,
            trackColor = SlowDownerTheme.colors.surface3,
            modifier = Modifier.fillMaxWidth().padding(horizontal = DownstepDimens.gutter).height(2.dp),
        )
    }
}

@Composable
private fun ExportBannerHost(viewModel: PlayerViewModel) {
    val export by viewModel.export.collectAsStateWithLifecycle()
    (export as? ExportUiState.Running)?.let {
        ExportBanner(it.fraction, viewModel::onExportCancelClicked, Modifier.padding(horizontal = DownstepDimens.gutter, vertical = 8.dp))
    }
}

@Composable
private fun ChipsHost(viewModel: PlayerViewModel, actions: LoopChipActions) {
    val state by viewModel.loopChips.collectAsStateWithLifecycle()
    val suggestions by remember(viewModel) { viewModel.analysis.map { it.suggestions }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(emptyList())
    LoopChipsStrip(state, suggestions, actions)
}

@Composable
private fun FocusHeaderHost(viewModel: PlayerViewModel, onExitFocus: () -> Unit) {
    val title by remember(viewModel) { viewModel.header.map { it.title }.distinctUntilChanged() }.collectAsStateWithLifecycle(null)
    val loopName by remember(viewModel) { viewModel.transport.map { it.activeLoopName }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(null)
    FocusHeader(title, loopName, onExitFocus)
}

@Composable
private fun OverviewHost(viewModel: PlayerViewModel, position: () -> Float, actions: WaveformActions, height: androidx.compose.ui.unit.Dp) {
    val state by viewModel.overview.collectAsStateWithLifecycle()
    OverviewWaveform(state, position, actions, height = height)
}

@Composable
private fun ZoomHost(viewModel: PlayerViewModel, position: () -> Float, actions: WaveformActions) {
    val state by viewModel.zoom.collectAsStateWithLifecycle()
    state?.let { ZoomStrip(it, position, actions) }
}

@Composable
private fun TransportHost(viewModel: PlayerViewModel, actions: PlayerActions, isFocusMode: Boolean) {
    val state by viewModel.transport.collectAsStateWithLifecycle()
    TransportRow(
        state = state,
        actions = actions.transport,
        loopTools = { LoopToolsHost(viewModel, actions.loopTools) },
        trailing = { TransportTrailing(viewModel, isFocusMode) },
    )
}

@Composable
private fun LoopToolsHost(viewModel: PlayerViewModel, actions: LoopToolActions) {
    val state by viewModel.loopTools.collectAsStateWithLifecycle()
    LoopToolGroup(state, actions)
}

/** Focus: big speed and pitch, then the pass pill. Otherwise: the pass pill, then the key hints. */
@Composable
private fun TransportTrailing(viewModel: PlayerViewModel, isFocusMode: Boolean) {
    if (isFocusMode) {
        val speedPitch by viewModel.speedPitch.collectAsStateWithLifecycle()
        FocusReadouts(speedPitch.speedPercent, speedPitch.pitchSemitones)
    }
    val passes by viewModel.passes.collectAsStateWithLifecycle()
    passes?.let { PassPill(it) }
    if (!isFocusMode) KeyboardHints()
}

@Composable
private fun SpeedHost(viewModel: PlayerViewModel, actions: SpeedPitchActions, modifier: Modifier) {
    val state by viewModel.speedPitch.collectAsStateWithLifecycle()
    SpeedCard(state.speedPercent, state.speedSlider, state.enabled, actions, modifier)
}

@Composable
private fun PitchHost(viewModel: PlayerViewModel, actions: SpeedPitchActions, modifier: Modifier) {
    val state by viewModel.speedPitch.collectAsStateWithLifecycle()
    PitchCard(state.pitchSemitones, state.pitchSlider, state.enabled, actions, modifier)
}

@Composable
private fun IsolateHost(effects: EffectsViewModel, actions: IsolateActions, modifier: Modifier) {
    val isolation by effects.isolation.collectAsStateWithLifecycle()
    // Isolation edits the EQ: possible once the saved EQ is loaded.
    val isReady by remember(effects) { effects.state.map { it != null }.distinctUntilChanged() }.collectAsStateWithLifecycle(false)
    IsolateCard(isolation, isReady, actions, modifier)
}

/** Progression and Notes: only for an active loop (README §2.8). */
@Composable
private fun LoopCardsHost(viewModel: PlayerViewModel, actions: PlayerActions) {
    val progression by viewModel.progression.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    if (progression == null && notes == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(DownstepDimens.cardGap),
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .padding(start = DownstepDimens.gutter, end = DownstepDimens.gutter, bottom = DownstepDimens.gutter),
    ) {
        progression?.let { ProgressionCard(it, actions.progression, Modifier.weight(1.3f)) }
        notes?.let { NotesCard(it, actions.notes, Modifier.weight(1f)) }
    }
}

@Composable
private fun PlayerDialogsHost(viewModel: PlayerViewModel, effects: EffectsViewModel, dialogs: PlayerDialogs) {
    if (dialogs.showEffects) EffectsSheet(onDismiss = { dialogs.showEffects = false }, viewModel = effects)
    if (dialogs.showSavePreset) {
        val suggestedName = remember { suggestedPresetName(viewModel.currentPracticeSettings()) }
        TextInputDialog(
            texts = TextInputDialogTexts(
                title = stringResource(Res.string.preset_save_title),
                label = stringResource(Res.string.name),
                confirm = stringResource(Res.string.save),
                message = stringResource(Res.string.preset_save_message),
            ),
            initialText = suggestedName,
            onConfirm = {
                viewModel.onSavePreset(it)
                dialogs.showSavePreset = false
            },
            onDismiss = { dialogs.showSavePreset = false },
        )
    }
    if (dialogs.showBpm) {
        val currentBpm = remember { viewModel.header.value.bpm }
        BpmDialog(
            currentBpm = currentBpm,
            onConfirm = {
                viewModel.onBpmChanged(it)
                dialogs.showBpm = false
            },
            onDismiss = { dialogs.showBpm = false },
        )
    }
    dialogs.presetToDelete?.let { preset ->
        ConfirmDialog(
            text = stringResource(Res.string.preset_delete_confirm, preset.name),
            confirm = stringResource(Res.string.delete),
            onConfirm = {
                viewModel.onPresetDeleteConfirmed(preset)
                dialogs.presetToDelete = null
            },
            onDismiss = { dialogs.presetToDelete = null },
        )
    }
}

private fun suggestedPresetName(settings: PracticeSettings) =
    "${formatSpeed(settings.speedPercent)}, ${formatPitch(settings.pitchSemitones)}"
