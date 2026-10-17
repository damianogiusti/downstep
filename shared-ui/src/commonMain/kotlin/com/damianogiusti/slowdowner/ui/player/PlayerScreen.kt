package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
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
import com.damianogiusti.slowdowner.ui.player.components.waveform.LoopDragState
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.ThemeViewModel
import kotlinx.coroutines.flow.combine
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
 * The root screen (README §2). The header sits on top, with the loading bar and the export banner
 * under it. The controls are anchored to the bottom of the window: loop chips, overview waveform,
 * zoom strip, transport. The cards fill the middle: Speed · Pitch · Isolate first, the settings of
 * every pass, then Progression · Notes, which belong to the active loop and so sit right above the
 * chips that name it. Focus mode keeps only the practice core, chips included, stuck to the bottom
 * (§4.9); the library is a drawer over it (§2.9).
 *
 * [PlayerLayoutFit] makes it all fit the window's height: the overview, the zoom strip and the cards
 * give way before anything scrolls, and then only the cards do. It changes on a resize, never while
 * playing.
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
    // The window's, which the root's theme follows: the menu's previews go through it.
    theme: ThemeViewModel = koinViewModel(),
) {
    val dialogs = remember { PlayerDialogs() }
    val actions = remember(viewModel, effects, theme, navigation, dialogs) {
        playerActions(viewModel, effects, theme, navigation, dialogs)
    }
    val positionState = viewModel.position.collectAsStateWithLifecycle()
    val position = remember(positionState) { positionState.reader() }
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    // Only whether they show: the fit needs nothing else from the loop cards.
    val hasLoopCards by remember(viewModel) {
        combine(viewModel.progression, viewModel.notes) { progression, notes -> progression != null || notes != null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(viewModel.progression.value != null || viewModel.notes.value != null)
    // One loop drag for both waveforms: the zoom strip draws a drag on the overview live, and back.
    val loopDrag = remember { LoopDragState() }
    // The loading bar and the export banner above the cards, measured: the fit leaves them room. Set
    // only when one appears or goes, never as the export's fraction moves.
    var extraTop by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    // A click outside Notes (or the drawer's search) ends typing: the shortcuts work again.
    Box(modifier = Modifier.fillMaxSize().background(SlowDownerTheme.colors.bg).clearsFocusOnPress()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isFocusMode = layout.isFocusMode
            val height = maxHeight
            // Focus mode shows neither: a banner measured before switching does not count there.
            val top = if (isFocusMode) 0.dp else extraTop
            val fit = remember(maxWidth, height, isFocusMode, hasLoopCards, top) {
                PlayerLayoutFit.of(maxWidth, height, isFocusMode, hasLoopCards, top)
            }
            if (isFocusMode) {
                // Stuck to the bottom, the space above left empty; scrolls only if even that cannot fit.
                Box(modifier = Modifier.fillMaxSize().testTag(PlayerTags.FOCUS).verticalScroll(rememberScrollState())) {
                    Column(
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxWidth().heightIn(min = height),
                    ) {
                        FocusHeaderHost(viewModel, actions.onExitFocus)
                        PracticeControls(viewModel, position, actions, fit, loopDrag, isFocusMode = true)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    HeaderHost(viewModel, theme, actions.header)
                    Column(modifier = Modifier.fillMaxWidth().onSizeChanged { extraTop = with(density) { it.height.toDp() } }) {
                        LoadingHost(viewModel)
                        ExportBannerHost(viewModel)
                    }
                    Column(modifier = Modifier.weight(1f).testTag(PlayerTags.CARDS).verticalScroll(rememberScrollState())) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(DownstepDimens.cardGap),
                            // Cards of a row share its tallest card's height, as in the design.
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                                .padding(horizontal = DownstepDimens.gutter, vertical = cardRowPadding(fit.compactCards)),
                        ) {
                            SpeedHost(viewModel, actions.speedPitch, fit.compactCards, Modifier.weight(1.2f))
                            PitchHost(viewModel, actions.speedPitch, fit.compactCards, Modifier.weight(1.2f))
                            IsolateHost(effects, actions.isolate, fit.compactCards, Modifier.weight(1f))
                        }
                        LoopCardsHost(viewModel, actions, fit.compactLoopCards)
                    }
                    PracticeControls(viewModel, position, actions, fit, loopDrag, isFocusMode = false)
                }
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

/** Test tags of the player's areas, for the layout tests. */
object PlayerTags {
    /** The middle area with the cards: the only part that scrolls, as a last resort. */
    const val CARDS = "player-cards"
    /** The bottom block: chips, overview, zoom strip, transport. */
    const val CONTROLS = "player-controls"
    const val TRANSPORT = "player-transport"
    /** Focus mode's whole content. */
    const val FOCUS = "player-focus"
}

/** Loop chips, overview, zoom strip and transport: the block at the bottom, in both modes. */
@Composable
private fun PracticeControls(
    viewModel: PlayerViewModel,
    position: () -> Float,
    actions: PlayerActions,
    fit: PlayerLayoutFit,
    loopDrag: LoopDragState,
    isFocusMode: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(PlayerTags.CONTROLS)) {
        ChipsHost(viewModel, actions.chips, fit.compactChips)
        OverviewHost(viewModel, position, actions.waveform, fit.overviewHeight, loopDrag)
        ZoomHost(viewModel, position, actions.waveform, fit.zoomHeight, loopDrag)
        TransportHost(viewModel, actions, isFocusMode, fit.compactTransport, fit.showKeyboardHints)
    }
}

/** Above and below the Speed · Pitch · Isolate row. */
private fun cardRowPadding(compact: Boolean) = if (compact) 6.dp else 10.dp

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
        onThemePreview = theme::onPreview,
        onThemePreviewEnd = theme::onPreviewEnd,
        onThemeApplied = theme::onApply,
        onSavePreset = { dialogs.showSavePreset = true },
        onPresetSelected = viewModel::onPresetSelected,
        onPresetDelete = { dialogs.presetToDelete = it },
        onExport = viewModel::onExportClicked,
        onShowEffects = { dialogs.showEffects = true },
        onAddToPlaylist = { viewModel.currentTrack()?.let(navigation.onAddToPlaylist) },
        onClickVolumeChanged = viewModel::onClickVolumeChanged,
        onCountInBeatsChanged = viewModel::onCountInBeatsChanged,
        onCountInEveryLoopToggled = viewModel::onCountInEveryLoopToggled,
    ),
    chips = LoopChipActions(
        onSelect = viewModel::onLoopSelected,
        onNewLoop = viewModel::onNewLoopAtPlayhead,
        onRename = viewModel::onLoopRenamed,
        onDelete = viewModel::onLoopDeleted,
        onSuggestionAccepted = viewModel::onSuggestionAccepted,
        onSuggestionDismissed = viewModel::onSuggestionDismissed,
    ),
    waveform = WaveformActions(
        onSeek = viewModel::onSeek,
        onLoopBoundsChanged = viewModel::onLoopBoundsChanged,
        onOverviewClick = viewModel::onOverviewClicked,
        onLoopBarClick = viewModel::onLoopBarClicked,
        onLoopGrabbed = viewModel::onLoopGrabbed,
    ),
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
private fun HeaderHost(viewModel: PlayerViewModel, theme: ThemeViewModel, actions: HeaderActions) {
    val state by viewModel.header.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val clicks by viewModel.clicks.collectAsStateWithLifecycle()
    val analysis by remember(viewModel) { viewModel.analysis.map { it.status }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(AnalysisStatus.Idle)
    val themeSelection by theme.selection.collectAsStateWithLifecycle()
    PlayerHeader(
        state = state,
        trackCount = rememberTrackCount(),
        features = viewModel.features,
        analysis = analysis,
        clicks = clicks,
        presets = presets,
        // The chosen theme, for the menu's checks: changes on a pick only, never on a preview.
        theme = themeSelection,
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
        ExportBanner(
            it.fraction,
            viewModel::onExportCancelClicked,
            Modifier.padding(horizontal = DownstepDimens.gutter, vertical = 8.dp),
            fileCount = it.fileCount,
        )
    }
}

@Composable
private fun ChipsHost(viewModel: PlayerViewModel, actions: LoopChipActions, compact: Boolean) {
    val state by viewModel.loopChips.collectAsStateWithLifecycle()
    val suggestions by remember(viewModel) { viewModel.analysis.map { it.suggestions }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(emptyList())
    LoopChipsStrip(state, suggestions, actions, compact = compact)
}

@Composable
private fun FocusHeaderHost(viewModel: PlayerViewModel, onExitFocus: () -> Unit) {
    val title by remember(viewModel) { viewModel.header.map { it.title }.distinctUntilChanged() }.collectAsStateWithLifecycle(null)
    val loopName by remember(viewModel) { viewModel.transport.map { it.activeLoopName }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(null)
    FocusHeader(title, loopName, onExitFocus)
}

@Composable
private fun OverviewHost(
    viewModel: PlayerViewModel,
    position: () -> Float,
    actions: WaveformActions,
    height: Dp,
    drag: LoopDragState,
) {
    val state by viewModel.overview.collectAsStateWithLifecycle()
    OverviewWaveform(state, position, actions, height = height, drag = drag)
}

@Composable
private fun ZoomHost(viewModel: PlayerViewModel, position: () -> Float, actions: WaveformActions, height: Dp, drag: LoopDragState) {
    val state by viewModel.zoom.collectAsStateWithLifecycle()
    state?.let { ZoomStrip(it, position, actions, height = height, drag = drag) }
}

@Composable
private fun TransportHost(viewModel: PlayerViewModel, actions: PlayerActions, isFocusMode: Boolean, compact: Boolean, showKeyboardHints: Boolean) {
    val state by viewModel.transport.collectAsStateWithLifecycle()
    TransportRow(
        state = state,
        actions = actions.transport,
        loopTools = { LoopToolsHost(viewModel, actions.loopTools) },
        trailing = { TransportTrailing(viewModel, isFocusMode, showKeyboardHints) },
        modifier = Modifier.testTag(PlayerTags.TRANSPORT),
        compact = compact,
    )
}

@Composable
private fun LoopToolsHost(viewModel: PlayerViewModel, actions: LoopToolActions) {
    val state by viewModel.loopTools.collectAsStateWithLifecycle()
    LoopToolGroup(state, actions)
}

/** Focus: big speed and pitch, then the pass pill. Otherwise: the pass pill, then the key hints where they fit. */
@Composable
private fun TransportTrailing(viewModel: PlayerViewModel, isFocusMode: Boolean, showKeyboardHints: Boolean) {
    if (isFocusMode) {
        val speedPitch by viewModel.speedPitch.collectAsStateWithLifecycle()
        FocusReadouts(speedPitch.speedPercent, speedPitch.pitchSemitones)
    }
    val passes by viewModel.passes.collectAsStateWithLifecycle()
    passes?.let { PassPill(it) }
    if (!isFocusMode && showKeyboardHints) KeyboardHints()
}

@Composable
private fun SpeedHost(viewModel: PlayerViewModel, actions: SpeedPitchActions, compact: Boolean, modifier: Modifier) {
    val state by viewModel.speedPitch.collectAsStateWithLifecycle()
    SpeedCard(state.speedPercent, state.speedSlider, state.enabled, actions, modifier, compact)
}

@Composable
private fun PitchHost(viewModel: PlayerViewModel, actions: SpeedPitchActions, compact: Boolean, modifier: Modifier) {
    val state by viewModel.speedPitch.collectAsStateWithLifecycle()
    PitchCard(state.pitchSemitones, state.pitchSlider, state.enabled, actions, modifier, compact)
}

@Composable
private fun IsolateHost(effects: EffectsViewModel, actions: IsolateActions, compact: Boolean, modifier: Modifier) {
    val isolation by effects.isolation.collectAsStateWithLifecycle()
    // Isolation edits the EQ: possible once the saved EQ is loaded.
    val isReady by remember(effects) { effects.state.map { it != null }.distinctUntilChanged() }.collectAsStateWithLifecycle(false)
    IsolateCard(isolation, isReady, actions, modifier, compact)
}

/** Progression and Notes: only for an active loop (README §2.8). */
@Composable
private fun LoopCardsHost(viewModel: PlayerViewModel, actions: PlayerActions, compact: Boolean) {
    val progression by viewModel.progression.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    if (progression == null && notes == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(DownstepDimens.cardGap),
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .padding(start = DownstepDimens.gutter, end = DownstepDimens.gutter, bottom = if (compact) 6.dp else 12.dp),
    ) {
        progression?.let { ProgressionCard(it, actions.progression, Modifier.weight(1.3f), compact) }
        notes?.let { NotesCard(it, actions.notes, Modifier.weight(1f), compact) }
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
        val header = remember { viewModel.header.value }
        BpmDialog(
            currentBpm = header.typedBpm,
            detectedBpm = header.detectedBpm,
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
