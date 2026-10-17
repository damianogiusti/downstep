package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.analysis.AnalyzeSectionsUseCase
import com.damianogiusti.slowdowner.domain.analysis.SectionAnalysisResult
import com.damianogiusti.slowdowner.domain.click.ClickSettings
import com.damianogiusti.slowdowner.domain.click.ObserveClickSettingsUseCase
import com.damianogiusti.slowdowner.domain.click.SetCountInUseCase
import com.damianogiusti.slowdowner.domain.click.SetMetronomeUseCase
import com.damianogiusti.slowdowner.domain.click.UpdateClickSettingsUseCase
import com.damianogiusti.slowdowner.domain.click.tempo
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportScope
import com.damianogiusti.slowdowner.domain.export.ExportTrackUseCase
import com.damianogiusti.slowdowner.domain.export.SaveExportUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.loops.BeatGrid
import com.damianogiusti.slowdowner.domain.loops.CreateLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.DeleteLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.Loop
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.LoopMath
import com.damianogiusti.slowdowner.domain.loops.ObserveLoopsUseCase
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.loops.RecordLoopPassUseCase
import com.damianogiusti.slowdowner.domain.loops.RenameLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.ResetLoopPassesUseCase
import com.damianogiusti.slowdowner.domain.loops.SetActiveLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.SetTrackBpmUseCase
import com.damianogiusti.slowdowner.domain.loops.TrackLoops
import com.damianogiusti.slowdowner.domain.loops.UpdateLoopNotesUseCase
import com.damianogiusti.slowdowner.domain.loops.UpdateLoopProgressionUseCase
import com.damianogiusti.slowdowner.domain.loops.UpdateLoopRegionUseCase
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.practice.ApplyPresetUseCase
import com.damianogiusti.slowdowner.domain.practice.DeletePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.ObservePresetsUseCase
import com.damianogiusti.slowdowner.domain.practice.OpenTrackForPracticeUseCase
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.practice.SavePresetResult
import com.damianogiusti.slowdowner.domain.practice.SavePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.StepPitchUseCase
import com.damianogiusti.slowdowner.domain.practice.StepSpeedUseCase
import com.damianogiusti.slowdowner.domain.tempo.DetectTrackTempoUseCase
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.common.UiPlural
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * The open track, its loops and its practice controls. Scoped to the window: it outlives the drawer,
 * the sheets and focus mode.
 *
 * The state is split into small flows, one per part of the screen (see PlayerUiState.kt), all derived
 * from a few sources: the now-playing track, its loops (Room), and [EngineSnapshot], which is refreshed
 * from one place only, the engine's events. The playhead is [position], the engine's own flow: it is
 * never copied into the other state, so playback recomposes nothing but draw code and the clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val engine: PlaybackEngine,
    private val openTrack: OpenTrackForPracticeUseCase,
    observeNowPlaying: ObserveNowPlayingUseCase,
    private val practice: PracticeUseCases,
    private val loops: LoopUseCases,
    private val seams: PracticeSeams,
    private val exports: ExportUseCases,
    private val clickTrack: ClickUseCases,
    /** Where waveform outlines are read: off the main thread. */
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val nowPlaying: StateFlow<LibraryTrack?> = observeNowPlaying()
    private val engineState = MutableStateFlow(readEngine())
    private val loadingTrack = MutableStateFlow<LibraryTrack?>(null)
    private val exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    private val overviewPeaks = MutableStateFlow<Peaks?>(null)
    private val zoomPeaks = MutableStateFlow<Peaks?>(null)
    private val layoutState = MutableStateFlow(PlayerLayout())
    private val analysisState = MutableStateFlow(AnalysisUiState())
    private val clickSettings: StateFlow<ClickSettings> = clickTrack.observeSettings().state(ClickSettings())
    /** The track whose tempo is being analysed, if any. */
    private val detectingTempo = MutableStateFlow<String?>(null)

    /** The loops of the open track; null with nothing open, and while a new track's loops load. */
    private val trackLoops: StateFlow<TrackLoops?> = nowPlaying
        .flatMapLatest { track ->
            if (track == null) flowOf(null) else flow<TrackLoops?> {
                emit(null)
                emitAll(loops.observe(track.id))
            }
        }
        .state(null)

    private val controlsEnabled: StateFlow<Boolean> =
        combine(engineState, loadingTrack, exportState) { engine, loading, export ->
            engine.isOpen && loading == null && export !is ExportUiState.Running
        }.state(false)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** The playhead, 0..1. Read it only in draw code or a tiny clock: never as a plain parameter. */
    val position: StateFlow<Float> = engine.positionFlow

    val features = PlayerFeatures(
        canAnalyze = seams.analyzeSections.isAvailable,
        hasMetronome = seams.setMetronome.isAvailable,
        hasCountIn = seams.setCountIn.isAvailable,
    )

    val header: StateFlow<HeaderState> = combine(nowPlaying, loadingTrack, engineState, combine(trackLoops, detectingTempo, ::Pair), exportState) {
            track, loading, engine, (trackLoops, detecting), export ->
        val shown = loading ?: track
        HeaderState(
            title = shown?.title,
            source = when {
                shown != null -> if (shown.sourceUrl != null) TrackSource.YouTube else TrackSource.Local
                engine.isOpen -> TrackSource.External
                else -> TrackSource.None
            },
            durationSeconds = if (loading == null) engine.durationSeconds else 0f,
            bpm = trackLoops?.effectiveBpm,
            typedBpm = trackLoops?.bpm,
            detectedBpm = trackLoops?.detectedBpm,
            isDetectingTempo = track != null && loading == null && detecting == track.id,
            loopCount = trackLoops?.loops?.size ?: 0,
            isOpen = engine.isOpen && loading == null,
            isLoading = loading != null,
            canExport = engine.isOpen && track != null && loading == null &&
                export !is ExportUiState.Running && export !is ExportUiState.Ready,
            canEditBpm = track != null && loading == null && export !is ExportUiState.Running,
        )
    }.state(HeaderState())

    val presets: StateFlow<PresetsState> = combine(
        nowPlaying.flatMapLatest { track -> track?.let { practice.observePresets(it.id) } ?: flowOf(emptyList()) },
        engineState,
        controlsEnabled,
        nowPlaying,
    ) { presets, engine, enabled, track ->
        PresetsState(
            presets = presets,
            activePresetId = presets.firstOrNull { it.settings.matches(engine.practiceSettings) }?.id,
            enabled = enabled && track != null,
        )
    }.state(PresetsState())

    val loopChips: StateFlow<LoopChipsState> = combine(trackLoops, engineState, controlsEnabled) { trackLoops, engine, enabled ->
        LoopChipsState(
            chips = trackLoops?.loops.orEmpty().map { loop ->
                LoopChipModel(
                    id = loop.id,
                    name = loop.name,
                    colorIndex = colorIndex(loop.id),
                    lengthSeconds = loop.length * engine.durationSeconds,
                    passes = loop.passes,
                    isActive = loop.id == trackLoops?.activeLoopId,
                )
            },
            enabled = enabled,
        )
    }.state(LoopChipsState())

    val analysis: StateFlow<AnalysisUiState> = analysisState.asStateFlow()

    val clicks: StateFlow<ClickUiState> = clickSettings.map { settings ->
        ClickUiState(
            isMetronomeOn = settings.isMetronomeOn && features.hasMetronome,
            isCountInOn = settings.isCountInOn && features.hasCountIn,
            volume = settings.volume,
            countInBeats = settings.countInBeats,
            countInEveryLoop = settings.countInEveryLoop,
        )
    }.state(ClickUiState())

    val overview: StateFlow<OverviewWaveformState> =
        combine(trackLoops, engineState, overviewPeaks, analysisState, controlsEnabled) { trackLoops, engine, peaks, analysis, enabled ->
            val duration = engine.durationSeconds
            val regions = trackLoops?.loops.orEmpty().map { it.toRegion(trackLoops?.activeLoopId) }
            val active = trackLoops?.activeLoop
            OverviewWaveformState(
                isOpen = engine.isOpen,
                durationSeconds = duration,
                peaks = peaks,
                regions = regions,
                active = regions.firstOrNull { it.isActive },
                isLoopOn = engine.isLoopOn,
                zoomWindow = active?.let { LoopMath.zoomWindow(it.bounds, trackLoops.beatGrid(duration)) },
                suggestions = analysis.suggestions,
                enabled = enabled,
            )
        }.state(OverviewWaveformState())

    val zoom: StateFlow<ZoomStripState?> = combine(trackLoops, engineState, zoomPeaks, controlsEnabled) { trackLoops, engine, peaks, enabled ->
        val active = trackLoops?.activeLoop ?: return@combine null
        val duration = engine.durationSeconds
        val tempo = trackLoops.beatGrid(duration)
        val window = LoopMath.zoomWindow(active.bounds, tempo)
        ZoomStripState(
            loopId = active.id,
            loopName = active.name,
            colorIndex = colorIndex(active.id),
            start = active.start,
            end = active.end,
            window = window,
            durationSeconds = duration,
            peaks = peaks,
            beats = tempo?.beatsBetween(active.start, active.end),
            enabled = enabled,
        )
    }.state(null)

    val transport: StateFlow<TransportState> =
        combine(engineState, trackLoops, loadingTrack, controlsEnabled) { engine, trackLoops, loading, enabled ->
            TransportState(
                isOpen = engine.isOpen,
                isLoading = loading != null,
                isPlaying = engine.isPlaying,
                isLoopOn = engine.isLoopOn && trackLoops?.activeLoop != null,
                activeLoopName = trackLoops?.activeLoop?.name,
                hasLoops = !trackLoops?.loops.isNullOrEmpty(),
                enabled = enabled,
            )
        }.state(TransportState())

    val loopTools: StateFlow<LoopToolsState> = combine(trackLoops, engineState, controlsEnabled) { trackLoops, engine, enabled ->
        val duration = engine.durationSeconds
        val bounds = trackLoops?.activeLoop?.bounds
        LoopToolsState(
            hasActiveLoop = bounds != null,
            canHalve = bounds != null && LoopMath.halve(bounds, duration) != null,
            canDouble = bounds != null && LoopMath.double(bounds) != null,
            canNudgeBack = bounds != null && LoopMath.nudge(bounds, -1, duration) != null,
            canNudgeForward = bounds != null && LoopMath.nudge(bounds, 1, duration) != null,
            enabled = enabled,
        )
    }.state(LoopToolsState())

    val passes: StateFlow<PassState?> = trackLoops
        .map { trackLoops -> trackLoops?.activeLoop?.let { PassState(it.passes, it.progression.status(it.passes)) } }
        .state(null)

    val speedPitch: StateFlow<SpeedPitchState> = combine(engineState, controlsEnabled) { engine, enabled ->
        SpeedPitchState(engine.speedPercent, engine.speedSlider, engine.pitchSemitones, engine.pitchSlider, enabled)
    }.state(SpeedPitchState())

    val progression: StateFlow<ProgressionCardState?> = combine(trackLoops, controlsEnabled) { trackLoops, enabled ->
        trackLoops?.activeLoop?.let { loop ->
            ProgressionCardState(
                loopId = loop.id,
                loopName = loop.name,
                progression = loop.progression,
                passes = loop.passes,
                status = loop.progression.status(loop.passes),
                progressFraction = if (loop.progression.enabled) loop.progression.progressFraction(loop.passes) else 0f,
                enabled = enabled,
            )
        }
    }.state(null)

    val notes: StateFlow<NotesCardState?> = trackLoops
        .map { trackLoops -> trackLoops?.activeLoop?.let { NotesCardState(it.id, it.name, it.notes) } }
        .state(null)

    val layout: StateFlow<PlayerLayout> = layoutState.asStateFlow()

    val export: StateFlow<ExportUiState> = exportState.asStateFlow()

    private val openMutex = Mutex()
    private val loopEdits = Mutex()
    private var exportJob: Job? = null
    /** The last export choice, offered again next time (not saved: the full track after a restart). */
    private var exportScope = ExportScope.FULL_TRACK
    private var analysisJob: Job? = null
    private var notesJob: Job? = null
    // Also read by flushPendingNotes() on the quit path, off the main thread.
    @Volatile
    private var pendingNotes: Pair<Long, String>? = null
    /** The previous playhead seen by the wrap detector; null right after a jump the user made. */
    private var lastTick: Float? = null

    init {
        viewModelScope.launch {
            // The one path that refreshes the controls: every change to the engine, from this screen,
            // a use case (progression) or the playback service, comes back as an event.
            engine.events.collect { refreshEngine() }
        }
        viewModelScope.launch {
            nowPlaying.collectLatest { track ->
                lastTick = null
                analysisJob?.cancel()
                analysisState.value = AnalysisUiState()
                overviewPeaks.value = null
                zoomPeaks.value = null
                if (track != null && engine.isOpen) {
                    overviewPeaks.value = withContext(background) { readPeaks(0f, 1f, OVERVIEW_BUCKETS) }
                    detectTempo(track)
                }
            }
        }
        viewModelScope.launch {
            // The engine clicks on the open track's grid: pushed again whenever the tempo (typed,
            // detected, another track) or a setting changes, never only when a toggle is pressed.
            combine(trackLoops.map { it?.tempo() }.distinctUntilChanged(), clickSettings) { tempo, settings -> tempo to settings }
                .collect { (tempo, settings) ->
                    seams.setMetronome(settings.isMetronomeOn, settings.volume, tempo)
                    seams.setCountIn(settings.isCountInOn, settings.countInBeats, settings.countInEveryLoop)
                }
        }
        viewModelScope.launch {
            combine(zoom.map { it?.window }.distinctUntilChanged(), overviewPeaks) { window, overview -> window.takeIf { overview != null } }
                .distinctUntilChanged()
                .mapLatest { window -> window?.let { withContext(background) { readPeaks(it.start, it.end, ZOOM_BUCKETS) } } }
                .collect { zoomPeaks.value = it }
        }
        viewModelScope.launch {
            // Passes: the engine has no wrap event, so a jump back to A while looping is one.
            engine.positionFlow.collect(::onPositionTick)
        }
    }

    // region Library

    fun onTrackSelected(track: LibraryTrack) {
        layoutState.update { it.copy(isDrawerOpen = false) }
        if (exportState.value is ExportUiState.Running) return
        flushNotes()
        viewModelScope.launch {
            // One open at a time: each one saves the outgoing track first, so order matters.
            val opened = openMutex.withLock {
                loadingTrack.value = track
                try {
                    openTrack(track)
                } finally {
                    loadingTrack.value = null
                }
            }
            if (!opened) _messages.send(UiMessage(Res.string.error_cannot_open, listOf(track.title)))
            refreshEngine()
        }
    }

    /** The open library track, for one-off actions (add to playlist). Not for composition. */
    fun currentTrack(): LibraryTrack? = nowPlaying.value

    /** The engine was driven from outside this ViewModel (Spotify). */
    fun onExternalSourceOpened() = refreshEngine()

    // endregion

    // region Transport

    fun onPlayStopClicked() = control {
        if (engine.isPlaying) onIntervention()
        engine.togglePlayStop()
    }

    /** A click on either waveform. */
    fun onSeek(position: Float) = control {
        onIntervention()
        engine.seekTo(position.coerceIn(0f, 1f))
    }

    /**
     * A click on the overview, off any loop's top bar. Inside the loop being looped it seeks there and
     * keeps looping; anywhere else (another loop's body included) it turns the loop off and seeks, so
     * the track plays on from there. Playing or paused stays as it was.
     */
    fun onOverviewClicked(position: Float) = control {
        val target = position.coerceIn(0f, 1f)
        onIntervention()
        // The engine's own region: the one being heard, even while a switch of loop is in flight.
        if (engine.isLoopOn && (target < engine.loopStart || target > engine.loopEnd)) engine.setLoopOn(false)
        engine.seekTo(target)
    }

    /** ↺ −5 s, ↻ +5 s and the arrow keys. */
    fun onSeekBy(seconds: Float) = control {
        val duration = engine.durationSeconds
        if (duration <= 0f) return@control
        onIntervention()
        engine.seekTo((engine.position + seconds / duration).coerceIn(0f, 1f))
    }

    /** ⏮: back to A when looping, else to the start of the file. */
    fun onToStartClicked() = control {
        onIntervention()
        val active = trackLoops.value?.activeLoop
        engine.seekTo(if (active != null && engine.isLoopOn) active.start else 0f)
    }

    /** ⏭ and `]`: the next loop in time order, round to the first. */
    fun onNextLoopClicked() = selectNeighbour(+1)

    /** `[`: the previous loop, round to the last. */
    fun onPreviousLoopClicked() = selectNeighbour(-1)

    /** The loop pill and Shift+L. Without an active loop there is nothing to loop. */
    fun onLoopToggled() = control {
        if (trackLoops.value?.activeLoop == null) return@control
        engine.setLoopOn(!engine.isLoopOn)
    }

    // endregion

    // region Speed and pitch

    fun onSpeedChanged(sliderPosition: Int) = control {
        onSpeedIntervention()
        engine.setSpeedFromSlider(sliderPosition)
    }

    fun onSpeedStep(steps: Int) = control {
        onSpeedIntervention()
        practice.stepSpeed(steps)
    }

    fun onSpeedReset() = control {
        onSpeedIntervention()
        engine.setSpeedPercent(DEFAULT_SPEED_PERCENT)
    }

    fun onPitchChanged(sliderPosition: Int) = control {
        onIntervention()
        engine.setPitchFromSlider(sliderPosition)
    }

    fun onPitchStep(steps: Int) = control {
        onIntervention()
        practice.stepPitch(steps)
    }

    fun onPitchReset() = control {
        onIntervention()
        engine.setPitchSemitones(0f)
    }

    // endregion

    // region Loops

    /** `L` and "+ New loop": 8 beats (or 8 s without a BPM) from exactly the playhead; it becomes active. */
    fun onNewLoopAtPlayhead() {
        val playhead = engine.position
        editLoops { track, trackLoops ->
            val duration = engine.durationSeconds
            val bounds = LoopMath.newLoopAt(playhead, duration, trackLoops.beatGrid(duration))
            lastTick = null
            loops.create(track.id, bounds)?.let { created -> awaitLoops { it.activeLoopId == created.id } }
        }
    }

    /** A click on a chip: it becomes active, looping, and the playhead jumps to its start. */
    fun onLoopSelected(loopId: Long) = editLoops { track, _ ->
        lastTick = null
        loops.setActive(track.id, loopId)
        awaitLoops { it.activeLoopId == loopId }
    }

    /** ✕ in the tool group: the first remaining loop takes over. */
    fun onDeleteActiveLoop() = editLoops { _, trackLoops ->
        trackLoops.activeLoop?.let { deleteLoop(it.id) }
    }

    /** Delete from a chip's menu; if it was the active one, the first remaining loop takes over. */
    fun onLoopDeleted(loopId: Long) = editLoops { _, _ -> deleteLoop(loopId) }

    fun onHalveLoop() = editActiveRegion { bounds, duration -> LoopMath.halve(bounds, duration) }

    fun onDoubleLoop() = editActiveRegion { bounds, _ -> LoopMath.double(bounds) }

    /** ◀ / ▶: [direction] −1 or +1, a fixed [LoopMath.NUDGE_SECONDS] of track time. */
    fun onNudgeLoop(direction: Int) = editActiveRegion { bounds, duration -> LoopMath.nudge(bounds, direction, duration) }

    /** `A`: the active loop starts at the playhead. Without a loop, a new one starts there. */
    fun onSetLoopStartAtPlayhead() {
        val playhead = engine.position
        editLoops { track, trackLoops ->
            val duration = engine.durationSeconds
            val active = trackLoops.activeLoop
            if (active == null) {
                val bounds = LoopMath.newLoopAt(playhead, duration, trackLoops.beatGrid(duration))
                loops.create(track.id, bounds)?.let { created -> awaitLoops { it.activeLoopId == created.id } }
            } else {
                onIntervention()
                updateRegion(active, LoopMath.moveStart(active.bounds, playhead, duration))
            }
        }
    }

    /** `B`: the active loop ends at the playhead. Without a loop, a new one ends there. */
    fun onSetLoopEndAtPlayhead() {
        val playhead = engine.position
        editLoops { track, trackLoops ->
            val duration = engine.durationSeconds
            val active = trackLoops.activeLoop
            if (active == null) {
                val length = LoopMath.newLoopAt(0f, duration, trackLoops.beatGrid(duration)).length
                val end = playhead.coerceIn(0f, 1f)
                loops.create(track.id, LoopMath.between(end - length, end, duration))
                    ?.let { created -> awaitLoops { it.activeLoopId == created.id } }
            } else {
                onIntervention()
                updateRegion(active, LoopMath.moveEnd(active.bounds, playhead, duration))
            }
        }
    }

    /**
     * The end of a drag on either waveform (A, B or the whole loop by its top bar), with the bounds the
     * drag showed (free: never rounded to a beat). Drags are drawn locally and committed once, here. A
     * dragged loop is the active one: if its grab ([onLoopGrabbed]) did not land, it is activated now.
     */
    fun onLoopBoundsChanged(loopId: Long, bounds: LoopBounds) = editLoops { track, trackLoops ->
        val loop = trackLoops.loops.firstOrNull { it.id == loopId } ?: return@editLoops
        if (loop.id == trackLoops.activeLoopId) {
            onIntervention()
        } else {
            activateInPlace(track, loop.id)
        }
        updateRegion(loop, bounds)
    }

    /**
     * A drag on the overview picked up a loop that is not the active one: it becomes active at once,
     * so the zoom strip follows the drag. The playhead stays and looping stays on or off; with
     * looping on, the engine then loops the grabbed loop (from its start if the playhead is outside).
     */
    fun onLoopGrabbed(loopId: Long) = editLoops { track, trackLoops ->
        if (trackLoops.activeLoopId == loopId || trackLoops.loops.none { it.id == loopId }) return@editLoops
        activateInPlace(track, loopId)
    }

    /**
     * A click (no drag) on a loop's top bar: "play this loop". It becomes active, looping, from its
     * start, and plays, also when paused. On the loop already being looped the click is just a click
     * inside it: it seeks to [position] there and, as any click inside the loop, leaves playing or
     * paused as it was (README §7.2: when paused, only another loop's bar starts playback).
     */
    fun onLoopBarClicked(loopId: Long, position: Float) = editLoops { track, trackLoops ->
        val loop = trackLoops.loops.firstOrNull { it.id == loopId } ?: return@editLoops
        if (loop.id == trackLoops.activeLoopId && engine.isLoopOn) {
            onIntervention()
            engine.seekTo(position.coerceIn(loop.start, loop.end))
            return@editLoops
        }
        lastTick = null
        loops.setActive(track.id, loop.id)
        if (!engine.isPlaying) engine.togglePlayStop()
        awaitLoops { it.activeLoopId == loop.id }
    }

    fun onLoopRenamed(loopId: Long, name: String) {
        viewModelScope.launch {
            if (!loops.rename(loopId, name)) _messages.send(UiMessage(Res.string.error_loop_name, listOf(Loop.MAX_NAME_LENGTH)))
        }
    }

    /** Every keystroke of the Notes card: saved after [NOTES_SAVE_DEBOUNCE_MS] of quiet. */
    fun onNotesChanged(loopId: Long, notes: String) {
        pendingNotes?.let { (pendingId, _) -> if (pendingId != loopId) flushNotes() }
        pendingNotes = loopId to notes
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            delay(NOTES_SAVE_DEBOUNCE_MS)
            flushNotes()
        }
    }

    /** The progression switch and steppers: the card sends the whole edited [Progression]. */
    fun onProgressionChanged(loopId: Long, progression: Progression) = editLoops { _, _ ->
        loops.updateProgression(loopId, progression)
    }

    /**
     * Typed or tapped by the user; null clears it, back to the detected tempo. Out of range: a message,
     * nothing saved. The beats of the new tempo are then found in the audio, for the metronome.
     */
    fun onBpmChanged(bpm: Float?) {
        val track = nowPlaying.value ?: return
        viewModelScope.launch {
            if (!loops.setBpm(track.id, bpm)) {
                _messages.send(UiMessage(Res.string.error_bpm_range, listOf(BeatGrid.MIN_BPM.toInt(), BeatGrid.MAX_BPM.toInt())))
            } else {
                withContext(background) { clickTrack.detectTempo.alignBeats(track.id) }
            }
        }
    }

    // endregion

    // region Analyze, metronome, count-in

    fun onAnalyzeClicked() {
        if (!features.canAnalyze || analysisState.value.status == AnalysisStatus.Running) return
        val track = nowPlaying.value ?: return
        analysisState.value = AnalysisUiState(AnalysisStatus.Running)
        analysisJob = viewModelScope.launch {
            when (val result = seams.analyzeSections(track)) {
                is SectionAnalysisResult.Success -> {
                    val duration = engine.durationSeconds
                    analysisState.value = AnalysisUiState(
                        status = AnalysisStatus.Done,
                        suggestions = result.sections.mapIndexed { index, section ->
                            SuggestionModel(index, section.name, section.start, section.end, (section.end - section.start) * duration)
                        },
                    )
                }
                SectionAnalysisResult.Unavailable -> analysisState.value = AnalysisUiState()
                is SectionAnalysisResult.Failure -> {
                    analysisState.value = AnalysisUiState()
                    _messages.send(UiMessage(Res.string.error_analyze, listOf(result.error.message.orEmpty())))
                }
            }
        }
    }

    /** ✓ on a suggestion: it becomes a real, active loop. Never done without this click. */
    fun onSuggestionAccepted(suggestionId: Int) {
        val suggestion = analysisState.value.suggestions.firstOrNull { it.id == suggestionId } ?: return
        onSuggestionDismissed(suggestionId)
        editLoops { track, _ ->
            lastTick = null
            loops.create(track.id, LoopBounds(suggestion.start, suggestion.end), suggestion.name)
                ?.let { created -> awaitLoops { it.activeLoopId == created.id } }
        }
    }

    fun onSuggestionDismissed(suggestionId: Int) {
        analysisState.update { state -> state.copy(suggestions = state.suggestions.filterNot { it.id == suggestionId }) }
    }

    /**
     * The settings are global and kept; the engine gets them through the push in init. Turning the
     * metronome on for a track with no tempo (and none being detected) asks for one instead.
     */
    fun onMetronomeToggled() {
        if (!features.hasMetronome) return
        val enabled = !clickSettings.value.isMetronomeOn
        val track = nowPlaying.value
        if (enabled && track != null && trackLoops.value?.tempo() == null && detectingTempo.value != track.id) {
            _messages.trySend(UiMessage(Res.string.error_click_needs_bpm))
            return
        }
        updateClicks { it.copy(isMetronomeOn = enabled) }
    }

    /** No tempo needed: without one the count-in counts at 120 BPM. */
    fun onCountInToggled() {
        if (!features.hasCountIn) return
        updateClicks { it.copy(isCountInOn = !it.isCountInOn) }
    }

    /** 0..1, at the end of a drag of the volume slider. */
    fun onClickVolumeChanged(volume: Float) = updateClicks { it.copy(volume = volume) }

    fun onCountInBeatsChanged(beats: Int) = updateClicks { it.copy(countInBeats = beats) }

    fun onCountInEveryLoopToggled() = updateClicks { it.copy(countInEveryLoop = !it.countInEveryLoop) }

    private fun updateClicks(change: (ClickSettings) -> ClickSettings) {
        viewModelScope.launch { clickTrack.updateSettings(change) }
    }

    /** The first time a track is opened: its tempo, from the audio; cached with the track afterwards. */
    private suspend fun detectTempo(track: LibraryTrack) {
        val detect = clickTrack.detectTempo
        if (!detect.isPending(track.id)) return
        detectingTempo.value = track.id
        try {
            withContext(background) { detect(track.id) }
        } finally {
            detectingTempo.compareAndSet(track.id, null)
        }
    }

    // endregion

    // region Layout

    fun onFocusToggled() = layoutState.update { it.copy(isFocusMode = !it.isFocusMode) }

    fun onDrawerOpened() = layoutState.update { it.copy(isDrawerOpen = true) }

    fun onDrawerClosed() = layoutState.update { it.copy(isDrawerOpen = false) }

    /**
     * False while another screen (a playlist) covers the player: the window's practice keys must not
     * edit loops or toggle focus mode out of sight. Transport keys still work.
     */
    var isShown: Boolean = true
        private set

    fun onShownChanged(shown: Boolean) {
        isShown = shown
    }

    /** Esc: closes the drawer, else leaves focus mode. False when there was nothing to close. */
    fun onEscape(): Boolean {
        val layout = layoutState.value
        layoutState.value = when {
            layout.isDrawerOpen -> layout.copy(isDrawerOpen = false)
            layout.isFocusMode -> layout.copy(isFocusMode = false)
            else -> return false
        }
        return true
    }

    // endregion

    // region Presets and export

    fun onSavePreset(name: String) {
        if (exportState.value is ExportUiState.Running) return
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

    /** Speed, pitch and the loop switch; the active loop keeps its bounds. A new speed breaks the pass. */
    fun onPresetSelected(preset: PracticePreset) = control {
        onIntervention()
        viewModelScope.launch { practice.applyPreset(preset) }
    }

    fun onPresetDeleteConfirmed(preset: PracticePreset) {
        viewModelScope.launch { practice.deletePreset(preset) }
    }

    /** A name for the save-preset dialog, from the current settings: "75%, −2 st". */
    fun currentPracticeSettings(): PracticeSettings = engineState.value.practiceSettings

    /** Opens the options: full track, loops or both. The last choice is kept while the app runs. */
    fun onExportClicked() {
        if (!header.value.canExport || exportState.value is ExportUiState.Choosing) return
        val loopCount = header.value.loopCount
        exportState.value = ExportUiState.Choosing(if (loopCount > 0) exportScope else ExportScope.FULL_TRACK, loopCount)
    }

    /** The loop options do nothing on a track without loops (the dialog disables them). */
    fun onExportScopeChanged(scope: ExportScope) {
        exportState.update { state ->
            if (state is ExportUiState.Choosing && (state.hasLoops || !scope.includesLoops)) state.copy(scope = scope) else state
        }
    }

    fun onExportConfirmed() {
        val choice = exportState.value as? ExportUiState.Choosing ?: return
        if (!header.value.canExport) return
        exportScope = choice.scope
        val cancelled = exportJob
        exportState.value = ExportUiState.Running(0f, choice.fileCount.coerceAtLeast(1))
        exportJob = viewModelScope.launch {
            // A cancelled render stops only between native chunks: let it close its files first.
            cancelled?.cancelAndJoin()
            val result = exports.exportTrack(choice.scope, ::onExportProgress)
            when (result) {
                is ExportResult.Success -> exportState.value = ExportUiState.Ready(result.files)
                ExportResult.NothingOpen -> exportFailed(UiMessage(Res.string.error_export_nothing_open))
                ExportResult.NoLoops -> exportFailed(UiMessage(Res.string.error_export_no_loops))
                is ExportResult.Failure -> exportFailed(
                    UiMessage(Res.string.error_export, listOf(result.cause.message.orEmpty())),
                )
            }
        }
    }

    /** Cancels the whole batch: the repository deletes every file it wrote. */
    fun onExportCancelClicked() {
        exportJob?.cancel()
        exportState.value = ExportUiState.Idle
    }

    /** The options or the share sheet took over, or the user closed a dialog. */
    fun onExportDismissed() {
        exportState.value = ExportUiState.Idle
    }

    /** One file: the dialog stays up while the picker is open, so a cancelled pick returns to it. */
    fun onExportSaveTargetPicked(contentUri: String) {
        val file = (exportState.value as? ExportUiState.Ready)?.files?.singleOrNull() ?: return
        exportState.value = ExportUiState.Idle
        viewModelScope.launch {
            val message = if (exports.saveExport(file, contentUri)) Res.string.export_saved else Res.string.error_export_save
            _messages.send(UiMessage(message, listOf(file.name)))
        }
    }

    /** A batch: every file goes into [folder], next to each other. */
    fun onExportSaveFolderPicked(folder: String) {
        val files = (exportState.value as? ExportUiState.Ready)?.files ?: return
        exportState.value = ExportUiState.Idle
        viewModelScope.launch {
            val message = if (exports.saveExport(files, folder)) {
                UiMessage(Res.string.export_saved_many, listOf(UiPlural(Res.plurals.export_files_count, files.size), folder))
            } else {
                UiMessage(Res.string.error_export_save_many, listOf(folder))
            }
            _messages.send(message)
        }
    }

    // endregion

    // The render uses the live settings and a decode leaves the engine closed: every control is
    // frozen meanwhile. No refresh here: the engine's event does it.
    private inline fun control(action: () -> Unit) {
        if (!canControl()) return
        action()
    }

    private fun canControl() = engine.isOpen && loadingTrack.value == null && exportState.value !is ExportUiState.Running

    /**
     * Loop edits run one at a time and each waits until the loops flow shows its result, so a burst
     * of nudges or key presses always starts from the previous one's outcome.
     */
    private fun editLoops(edit: suspend (track: LibraryTrack, trackLoops: TrackLoops) -> Unit) {
        if (!canControl()) return
        viewModelScope.launch {
            loopEdits.withLock {
                val track = nowPlaying.value ?: return@withLock
                val trackLoops = trackLoops.value?.takeIf { it.trackId == track.id } ?: return@withLock
                edit(track, trackLoops)
            }
        }
    }

    private fun editActiveRegion(change: (bounds: LoopBounds, durationSeconds: Float) -> LoopBounds?) =
        editLoops { _, trackLoops ->
            val active = trackLoops.activeLoop ?: return@editLoops
            val duration = engine.durationSeconds
            val bounds = change(active.bounds, duration) ?: return@editLoops
            onIntervention()
            updateRegion(active, bounds)
        }

    /** [loopId] becomes active where the playhead is, keeping looping on or off. */
    private suspend fun activateInPlace(track: LibraryTrack, loopId: Long) {
        lastTick = null
        loops.setActive(track.id, loopId, seekToStart = false, loopOn = engine.isLoopOn)
        awaitLoops { it.activeLoopId == loopId }
    }

    private suspend fun deleteLoop(loopId: Long) {
        loops.delete(loopId)
        awaitLoops { trackLoops -> trackLoops.loops.none { it.id == loopId } }
    }

    private suspend fun updateRegion(loop: Loop, bounds: LoopBounds) {
        val updated = loops.updateRegion(loop.id, bounds) ?: return
        awaitLoops { trackLoops ->
            trackLoops.loops.firstOrNull { it.id == updated.id }?.let { it.start == updated.start && it.end == updated.end } ?: true
        }
    }

    private suspend fun awaitLoops(condition: (TrackLoops) -> Boolean) {
        withTimeoutOrNull(LOOP_SYNC_TIMEOUT_MS) { trackLoops.first { it != null && condition(it) } }
        // The edit may have moved the playhead (a loop now behind it sends it back to A): not a wrap.
        lastTick = null
    }

    private fun selectNeighbour(step: Int) = editLoops { track, trackLoops ->
        val all = trackLoops.loops
        if (all.isEmpty()) return@editLoops
        val index = all.indexOfFirst { it.id == trackLoops.activeLoopId }
        val next = when {
            index < 0 -> if (step > 0) all.first() else all.last()
            else -> all[(index + step).mod(all.size)]
        }
        lastTick = null
        loops.setActive(track.id, next.id)
        awaitLoops { it.activeLoopId == next.id }
    }

    /**
     * The user broke the pass in progress (seek, A/B change, pitch or speed change, pause): passes
     * count uninterrupted repetitions (README §4.6), so the active loop starts again from zero.
     */
    private fun onIntervention() {
        lastTick = null
        val active = trackLoops.value?.activeLoop ?: return
        if (!engine.isPlaying || !engine.isLoopOn || active.passes == 0) return
        viewModelScope.launch { loops.resetPasses(active.id) }
    }

    // With progression on, the ramp owns the speed: a reset would put it back to "from" at once,
    // undoing the user's change. Their speed then lasts until the ramp's next step.
    private fun onSpeedIntervention() {
        if (trackLoops.value?.activeLoop?.progression?.enabled == true) lastTick = null else onIntervention()
    }

    private fun onPositionTick(position: Float) {
        val previous = lastTick
        lastTick = position
        if (previous == null || position >= previous) return
        val active = trackLoops.value?.activeLoop ?: return
        if (!engine.isPlaying || !engine.isLoopOn) return
        // A switch in flight: the engine already loops the new region (and jumped to its start), the
        // loops flow still shows the old one. That jump back is not a wrap of the old loop.
        if (abs(engine.loopStart - active.start) > REGION_EPSILON || abs(engine.loopEnd - active.end) > REGION_EPSILON) return
        // From the second half of the loop back into the first: the engine wrapped from B to A.
        val middle = active.start + active.length / 2
        if (previous >= middle && position <= middle) {
            viewModelScope.launch { loops.recordPass(active.id) }
        }
    }

    private fun flushNotes() {
        val (loopId, text) = takePendingNotes() ?: return
        viewModelScope.launch { loops.updateNotes(loopId, text) }
    }

    /**
     * Saves the notes still waiting for their debounce, and waits for the write. Called when the app
     * quits: the process exits before the pause ends, and before a cancelled scope could save them.
     */
    suspend fun flushPendingNotes() {
        val (loopId, text) = takePendingNotes() ?: return
        loops.updateNotes(loopId, text)
    }

    private fun takePendingNotes(): Pair<Long, String>? {
        notesJob?.cancel()
        return pendingNotes.also { pendingNotes = null }
    }

    // Called once per rendered chunk on the IO thread: only publish whole-percent steps. One atomic
    // update: a plain read-then-write could put Running back over a cancel (Idle) made in between,
    // and a stuck Running freezes every control.
    private fun onExportProgress(fraction: Float) {
        exportState.update { state ->
            if (state is ExportUiState.Running && (fraction * 100).toInt() != (state.fraction * 100).toInt()) {
                state.copy(fraction = fraction)
            } else {
                state
            }
        }
    }

    private suspend fun exportFailed(message: UiMessage) {
        exportState.value = ExportUiState.Idle
        _messages.send(message)
    }

    private fun refreshEngine() {
        engineState.value = readEngine()
    }

    private fun readEngine() = EngineSnapshot(
        isOpen = engine.isOpen,
        isPlaying = engine.isPlaying,
        durationSeconds = engine.durationSeconds,
        speedPercent = engine.speedPercent,
        speedSlider = engine.speedSliderPosition,
        pitchSemitones = engine.pitchSemitones,
        pitchSlider = engine.pitchSliderPosition,
        isLoopOn = engine.isLoopOn,
        loopStart = engine.loopStart,
        loopEnd = engine.loopEnd,
    )

    private fun readPeaks(from: Float, to: Float, buckets: Int): Peaks? =
        engine.peaks(from, to, buckets).takeIf { it.isNotEmpty() }?.let { Peaks(from, to, it) }

    private fun Loop.toRegion(activeLoopId: Long?) = LoopRegionModel(id, start, end, colorIndex(id), id == activeLoopId)

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    /** Everything the controls read from the engine, except the playhead. */
    private data class EngineSnapshot(
        val isOpen: Boolean,
        val isPlaying: Boolean,
        val durationSeconds: Float,
        val speedPercent: Float,
        val speedSlider: Int,
        val pitchSemitones: Float,
        val pitchSlider: Int,
        val isLoopOn: Boolean,
        val loopStart: Float,
        val loopEnd: Float,
    ) {
        val practiceSettings get() = PracticeSettings(speedPercent, pitchSemitones, LoopRegion(loopStart, loopEnd, isLoopOn))
    }

    companion object {
        const val DEFAULT_SPEED_PERCENT = 100f
        const val NOTES_SAVE_DEBOUNCE_MS = 500L
        /** The overview is at most a few thousand pixels wide: the waveform resamples to its bars. */
        const val OVERVIEW_BUCKETS = 2048
        const val ZOOM_BUCKETS = 1024
        private const val LOOP_SYNC_TIMEOUT_MS = 2_000L
        /** The engine's region and the active loop's bounds agree within this (both 0..1). */
        private const val REGION_EPSILON = 1e-4f

        /** Loop colours cycle by id: loop 1 is amber, 2 teal, 3 coral, 4 olive (README §1). */
        fun colorIndex(loopId: Long): Int = (loopId - 1).mod(4L).toInt()
    }
}

/** Practice use-cases of the player, grouped to keep the constructor readable. */
class PracticeUseCases(
    val stepSpeed: StepSpeedUseCase,
    val stepPitch: StepPitchUseCase,
    val observePresets: ObservePresetsUseCase,
    val savePreset: SavePresetUseCase,
    val applyPreset: ApplyPresetUseCase,
    val deletePreset: DeletePresetUseCase,
)

/** The named-loop use-cases (domain.loops). */
class LoopUseCases(
    val observe: ObserveLoopsUseCase,
    val create: CreateLoopUseCase,
    val setActive: SetActiveLoopUseCase,
    val rename: RenameLoopUseCase,
    val updateNotes: UpdateLoopNotesUseCase,
    val updateRegion: UpdateLoopRegionUseCase,
    val delete: DeleteLoopUseCase,
    val recordPass: RecordLoopPassUseCase,
    val resetPasses: ResetLoopPassesUseCase,
    val updateProgression: UpdateLoopProgressionUseCase,
    val setBpm: SetTrackBpmUseCase,
)

/** Features whose backend may be missing: each says whether it is available. */
class PracticeSeams(
    val analyzeSections: AnalyzeSectionsUseCase,
    val setMetronome: SetMetronomeUseCase,
    val setCountIn: SetCountInUseCase,
)

/** The metronome and count-in settings, and the tempo analysis they click by (domain.click, domain.tempo). */
class ClickUseCases(
    val observeSettings: ObserveClickSettingsUseCase,
    val updateSettings: UpdateClickSettingsUseCase,
    val detectTempo: DetectTrackTempoUseCase,
)

class ExportUseCases(
    val exportTrack: ExportTrackUseCase,
    val saveExport: SaveExportUseCase,
)


