package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportTrackUseCase
import com.damianogiusti.slowdowner.domain.export.SaveExportUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.practice.ApplyPresetUseCase
import com.damianogiusti.slowdowner.domain.practice.DeletePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.ObservePresetsUseCase
import com.damianogiusti.slowdowner.domain.practice.OpenTrackForPracticeUseCase
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.SavePresetResult
import com.damianogiusti.slowdowner.domain.practice.SavePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.SetLoopRegionUseCase
import com.damianogiusti.slowdowner.domain.practice.StepPitchUseCase
import com.damianogiusti.slowdowner.domain.practice.StepSpeedUseCase
import com.damianogiusti.slowdowner.ui.common.UiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The open track and its practice controls. Scoped to the activity: the mini player and the
 * player screen show the same state.
 */
class PlayerViewModel(
    private val engine: PlaybackEngine,
    private val openTrack: OpenTrackForPracticeUseCase,
    observeNowPlaying: ObserveNowPlayingUseCase,
    private val practice: PracticeUseCases,
    private val export: ExportUseCases,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val openMutex = Mutex()
    private var positionJob: Job? = null
    private var exportJob: Job? = null

    init {
        val nowPlaying = observeNowPlaying()
        viewModelScope.launch {
            nowPlaying.collect { track ->
                _state.update { it.copy(track = track) }
                refreshPlayer()
            }
        }
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            nowPlaying
                .flatMapLatest { track -> track?.let { practice.observePresets(it.id) } ?: flowOf(emptyList()) }
                .collect { presets -> _state.update { it.copy(presets = presets) } }
        }
        viewModelScope.launch {
            // End of track is handled by the playback service, which outlives the UI.
            engine.events.collect { refreshPlayer() }
        }
        refreshPlayer()
    }

    fun onTrackSelected(track: LibraryTrack) {
        if (_state.value.isExporting) return
        viewModelScope.launch {
            // One open at a time: each one saves the outgoing track first, so order matters.
            val opened = openMutex.withLock { openTrack(track) }
            if (!opened) {
                _messages.trySend(UiMessage(Res.string.error_cannot_open, listOf(track.title)))
            }
            refreshPlayer()
        }
    }

    fun onPlayStopClicked() = control(requiresOpen = true) { engine.togglePlayStop() }

    fun onSeek(position: Float) = control(requiresOpen = true) { engine.seekTo(position) }

    fun onNudge(seconds: Float) = control(requiresOpen = true) {
        val duration = engine.durationSeconds
        if (duration > 0f) engine.seekTo((engine.position + seconds / duration).coerceIn(0f, 1f))
    }

    /** Back to A when looping, else to the start. */
    fun onRewindClicked() = control(requiresOpen = true) {
        engine.seekTo(if (engine.isLoopOn) engine.loopStart else 0f)
    }

    fun onSpeedChanged(sliderPosition: Int) = control { engine.setSpeedFromSlider(sliderPosition) }

    fun onPitchChanged(sliderPosition: Int) = control { engine.setPitchFromSlider(sliderPosition) }

    fun onSpeedStep(steps: Int) = control { practice.stepSpeed(steps) }

    fun onPitchStep(steps: Int) = control { practice.stepPitch(steps) }

    fun onSpeedReset() = control { engine.setSpeedPercent(DEFAULT_SPEED_PERCENT) }

    fun onPitchReset() = control { engine.setPitchSemitones(0f) }

    fun onLoopToggled(enabled: Boolean) = control(requiresOpen = true) { engine.setLoopOn(enabled) }

    fun onMarkLoopStartClicked() = control(requiresOpen = true) { engine.markLoopStart() }

    fun onMarkLoopEndClicked() = control(requiresOpen = true) { engine.markLoopEnd() }

    fun onHalveLoopClicked() = loopAction(engine::halveLoop)

    fun onDoubleLoopClicked() = loopAction(engine::doubleLoop)

    fun onShiftLoopBackwardClicked() = loopAction(engine::shiftLoopBackward)

    fun onShiftLoopForwardClicked() = loopAction(engine::shiftLoopForward)

    /** Handles dragged on the timeline. */
    fun onLoopRegionChanged(start: Float, end: Float) = control(requiresOpen = true) { practice.setLoopRegion(start, end) }

    fun onSavePreset(name: String) {
        if (_state.value.isExporting) return
        viewModelScope.launch {
            val message = when (val result = practice.savePreset(name)) {
                is SavePresetResult.Saved -> UiMessage(Res.string.preset_saved, listOf(result.preset.name))
                SavePresetResult.InvalidName -> UiMessage(Res.string.error_preset_name)
                SavePresetResult.NameTaken -> UiMessage(Res.string.error_preset_name_taken, listOf(name.trim()))
                SavePresetResult.NothingOpen -> UiMessage(Res.string.error_preset_nothing_open)
            }
            _messages.send(message)
        }
    }

    fun onPresetSelected(preset: PracticePreset) = control(requiresOpen = true) { practice.applyPreset(preset) }

    fun onPresetDeleteConfirmed(preset: PracticePreset) {
        viewModelScope.launch { practice.deletePreset(preset) }
    }

    fun onExportClicked() {
        if (!_state.value.canExport) return
        val cancelled = exportJob
        _state.update { it.copy(export = ExportUiState.Running(0f)) }
        exportJob = viewModelScope.launch {
            // A cancelled render stops only between native chunks: let it close its file first.
            cancelled?.cancelAndJoin()
            val result = export.exportTrack(::onExportProgress)
            refreshPlayer()
            when (result) {
                is ExportResult.Success -> _state.update { it.copy(export = ExportUiState.Ready(result.file)) }
                ExportResult.NothingOpen -> exportFailed(UiMessage(Res.string.error_export_nothing_open))
                is ExportResult.Failure -> exportFailed(
                    UiMessage(Res.string.error_export, listOf(result.cause.message.orEmpty())),
                )
            }
        }
    }

    fun onExportCancelClicked() {
        exportJob?.cancel()
        _state.update { it.copy(export = ExportUiState.Idle) }
    }

    /** The share sheet took over, or the user closed the dialog. */
    fun onExportDismissed() {
        _state.update { it.copy(export = ExportUiState.Idle) }
    }

    /** The dialog stays up while the picker is open, so a cancelled pick returns to it. */
    fun onExportSaveTargetPicked(contentUri: String) {
        val file = (_state.value.export as? ExportUiState.Ready)?.file ?: return
        _state.update { it.copy(export = ExportUiState.Idle) }
        viewModelScope.launch {
            val message = if (export.saveExport(file, contentUri)) Res.string.export_saved else Res.string.error_export_save
            _messages.send(UiMessage(message, listOf(file.name)))
        }
    }

    /** The engine was driven from outside this ViewModel (Spotify). */
    fun onExternalSourceOpened() = refreshPlayer()

    // The render uses the live settings: every control is frozen while it runs.
    private inline fun control(requiresOpen: Boolean = false, action: () -> Unit) {
        if (_state.value.isExporting || (requiresOpen && !engine.isOpen)) return
        action()
        refreshPlayer()
    }

    private fun loopAction(action: () -> Unit) = control(requiresOpen = true, action)

    // Called once per rendered chunk on the IO thread: only publish whole-percent steps.
    private fun onExportProgress(fraction: Float) {
        val running = _state.value.export as? ExportUiState.Running ?: return
        if ((fraction * 100).toInt() == (running.fraction * 100).toInt()) return
        _state.update { it.copy(export = ExportUiState.Running(fraction)) }
    }

    private suspend fun exportFailed(message: UiMessage) {
        _state.update { it.copy(export = ExportUiState.Idle) }
        _messages.send(message)
    }

    private fun refreshPlayer() {
        _state.update { it.copy(controls = readControls()) }
        if (engine.isPlaying) startPositionUpdates()
    }

    // The engine has no position callback during normal playback: poll it. Only the position:
    // every native getter takes the engine lock, and frequent reads starve the audio feeder.
    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = viewModelScope.launch {
            while (isActive && engine.isPlaying) {
                val position = engine.position
                _state.update { it.copy(controls = it.controls.copy(position = position)) }
                delay(POSITION_UPDATE_MS)
            }
            _state.update { it.copy(controls = readControls()) }
        }
    }

    private fun readControls() = PlayerControlsState(
        isOpen = engine.isOpen,
        isPlaying = engine.isPlaying,
        position = engine.position,
        durationSeconds = engine.durationSeconds,
        speedSlider = engine.speedSliderPosition,
        speedPercent = engine.speedPercent,
        pitchSlider = engine.pitchSliderPosition,
        pitchSemitones = engine.pitchSemitones,
        isLoopOn = engine.isLoopOn,
        loopStart = engine.loopStart,
        loopEnd = engine.loopEnd,
    )

    private companion object {
        const val POSITION_UPDATE_MS = 100L
        const val DEFAULT_SPEED_PERCENT = 100f
    }
}

/** Practice use-cases of the player, grouped to keep the constructor readable. */
class PracticeUseCases(
    val stepSpeed: StepSpeedUseCase,
    val stepPitch: StepPitchUseCase,
    val setLoopRegion: SetLoopRegionUseCase,
    val observePresets: ObservePresetsUseCase,
    val savePreset: SavePresetUseCase,
    val applyPreset: ApplyPresetUseCase,
    val deletePreset: DeletePresetUseCase,
)

class ExportUseCases(
    val exportTrack: ExportTrackUseCase,
    val saveExport: SaveExportUseCase,
)
