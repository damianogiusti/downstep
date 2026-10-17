package com.damianogiusti.slowdowner.ui.player

import androidx.lifecycle.ViewModelStore
import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.analysis.AnalyzeSectionsUseCase
import com.damianogiusti.slowdowner.domain.click.SetCountInUseCase
import com.damianogiusti.slowdowner.domain.click.SetMetronomeUseCase
import com.damianogiusti.slowdowner.domain.export.ExportItem
import com.damianogiusti.slowdowner.domain.export.ExportRepository
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportScope
import com.damianogiusti.slowdowner.domain.export.ExportTrackUseCase
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.export.SaveExportUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.loops.CreateLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.DeleteLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.LoopRepository
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
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import com.damianogiusti.slowdowner.domain.practice.ApplyPresetUseCase
import com.damianogiusti.slowdowner.domain.practice.DeletePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.ObservePresetsUseCase
import com.damianogiusti.slowdowner.domain.practice.OpenTrackForPracticeUseCase
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.practice.SavePracticeStateUseCase
import com.damianogiusti.slowdowner.domain.practice.SavePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.StepPitchUseCase
import com.damianogiusti.slowdowner.domain.practice.StepSpeedUseCase
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeLoopRepository
import com.damianogiusti.slowdowner.fake.FakePracticeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val main = StandardTestDispatcher()
    private val engine = EventfulEngine()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val loopRepository = FakeLoopRepository(existingTrackIds = setOf(TRACK.id, OTHER.id))
    /** What the ViewModel observes: [HeldLoops.hold] delays Room's re-emission, as a slow write does. */
    private val observedLoops = HeldLoops(loopRepository)
    private val practiceRepository = FakePracticeRepository()
    /** The decode runs here: nothing advances it until a test does. */
    private val decodeScheduler = TestCoroutineScheduler()
    private val store = ViewModelStore()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `a wrap of the active loop counts one pass`() = runTest(main) {
        val viewModel = openWithLoop()
        engine.isPlaying = true

        tick(0.25f, 0.32f, 0.39f, 0.21f, 0.30f)

        assertEquals(1, loopRepository.loops.value.single().passes)
        assertEquals(1, viewModel.passes.value?.passes)
    }

    @Test
    fun `no pass while paused or with the loop off`() = runTest(main) {
        openWithLoop()
        tick(0.39f, 0.21f)
        engine.isPlaying = true
        engine.setLoopOn(false)
        runCurrent()
        tick(0.39f, 0.21f)

        assertEquals(0, loopRepository.loops.value.single().passes)
    }

    @Test
    fun `switching to an earlier loop mid-pass is not a wrap of the loop being left`() = runTest(main) {
        val viewModel = openWithLoop()
        val solo = loopRepository.loops.value.single()
        val intro = assertNotNull(loopRepository.create(TRACK.id, "Intro", LoopBounds(0.05f, 0.15f)))
        runCurrent()
        engine.isPlaying = true
        tick(0.27f, 0.35f)

        // The write of the active id takes a while, and playback goes on meanwhile. Then the engine
        // jumps to Intro's start before the loops flow shows Intro as active.
        observedLoops.hold()
        val written = observedLoops.slowActiveWrite()
        viewModel.onLoopSelected(intro.id)
        runCurrent()
        tick(0.36f)
        written.complete(Unit)
        runCurrent()
        assertEquals(0.05f, engine.position)
        observedLoops.release()
        runCurrent()

        assertEquals(0, loopRepository.loops.value.first { it.id == solo.id }.passes)
        assertEquals(intro.id, viewModel.loopChips.value.chips.firstOrNull { it.isActive }?.id)
    }

    @Test
    fun `a preset sets speed and pitch but the active loop keeps its bounds`() = runTest(main) {
        val viewModel = openWithLoop()
        val elsewhere = PracticePreset(1, TRACK.id, "Old", PracticeSettings(70f, -1f, LoopRegion(0.6f, 0.8f, isOn = true)))

        viewModel.onPresetSelected(elsewhere)
        runCurrent()

        assertEquals(70f, engine.speedPercent)
        assertEquals(-1f, engine.pitchSemitones)
        assertEquals(LoopRegion(0.2f, 0.4f, isOn = true), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
    }

    @Test
    fun `a seek mid-pass resets the passes and is not a wrap`() = runTest(main) {
        val viewModel = openWithLoop(passes = 3)
        engine.isPlaying = true
        tick(0.38f)

        viewModel.onSeek(0.22f)
        runCurrent()
        tick(0.25f)

        assertEquals(0, loopRepository.loops.value.single().passes)
    }

    @Test
    fun `pausing mid-pass resets the passes, playing on does not`() = runTest(main) {
        val viewModel = openWithLoop(passes = 2)

        viewModel.onPlayStopClicked()
        runCurrent()
        assertEquals(2, loopRepository.loops.value.single().passes, "starting is not an interruption")

        viewModel.onPlayStopClicked()
        runCurrent()
        assertEquals(0, loopRepository.loops.value.single().passes)
    }

    @Test
    fun `progression ramps the speed every few passes`() = runTest(main) {
        val viewModel = openWithLoop(progression = Progression(enabled = true, fromPercent = 70f, toPercent = 100f, stepPercent = 3f, everyPasses = 2))
        assertEquals(70f, engine.speedPercent, "selecting the loop starts the ramp")
        engine.isPlaying = true

        repeat(2) { tick(0.39f, 0.21f) }

        assertEquals(73f, engine.speedPercent)
        assertEquals(73f, viewModel.speedPitch.value.speedPercent)
        assertEquals(2, viewModel.progression.value?.passes)
    }

    @Test
    fun `controls are frozen while a track decodes`() = runTest(main) {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onTrackSelected(TRACK)
        runCurrent()
        assertTrue(viewModel.header.value.isLoading)
        assertEquals(TRACK.title, viewModel.header.value.title)
        assertFalse(viewModel.transport.value.enabled)
        viewModel.onPlayStopClicked()
        viewModel.onSpeedStep(1)
        assertFalse(engine.isPlaying)
        assertEquals(100f, engine.speedPercent)

        decodeScheduler.runCurrent()
        runCurrent()

        assertFalse(viewModel.header.value.isLoading)
        assertTrue(viewModel.header.value.isOpen)
        assertTrue(viewModel.transport.value.enabled)
    }

    @Test
    fun `speed and pitch are read once per engine event, never after the call itself`() = runTest(main) {
        val viewModel = openWithLoop()
        val before = engine.controlReads

        viewModel.onSpeedStep(1)
        runCurrent()

        assertEquals(105f, viewModel.speedPitch.value.speedPercent)
        assertEquals(1, engine.controlReads - before)

        // The same value again: the engine sends nothing, and nothing is re-read.
        engine.quiet = true
        viewModel.onSpeedReset()
        viewModel.onSpeedStep(0)
        runCurrent()
        assertEquals(1, engine.controlReads - before)
    }

    @Test
    fun `playback moves only the position flow`() = runTest(main) {
        val viewModel = openWithLoop()
        engine.isPlaying = true
        val emissions = mutableListOf<Any?>()
        val jobs = listOf(viewModel.header, viewModel.transport, viewModel.loopChips, viewModel.overview, viewModel.speedPitch)
            .map { flow -> backgroundScope.launch { flow.collect { emissions += it } } }
        runCurrent()
        val initial = emissions.size

        tick(0.25f, 0.26f, 0.27f, 0.28f)

        assertEquals(initial, emissions.size)
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `A without a loop starts one at the playhead, B then ends it there`() = runTest(main) {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onTrackSelected(TRACK)
        runCurrent()
        decodeScheduler.runCurrent()
        runCurrent()
        engine.position = 0.2f

        viewModel.onSetLoopStartAtPlayhead()
        runCurrent()
        val created = loopRepository.loops.value.single()
        assertEquals(0.2f, created.start)
        assertEquals(created.id, viewModel.loopChips.value.chips.single { it.isActive }.id)
        assertTrue(engine.isLoopOn)

        engine.position = 0.5f
        viewModel.onSetLoopEndAtPlayhead()
        runCurrent()
        assertEquals(LoopBounds(0.2f, 0.5f), loopRepository.loops.value.single().bounds)
    }

    @Test
    fun `notes are saved once, after a pause in typing`() = runTest(main) {
        val viewModel = openWithLoop()
        val loopId = loopRepository.loops.value.single().id

        listOf("C", "Cm", "Cm7").forEach {
            viewModel.onNotesChanged(loopId, it)
            advanceTimeBy(100)
        }
        assertEquals("", loopRepository.loops.value.single().notes)

        advanceTimeBy(PlayerViewModel.NOTES_SAVE_DEBOUNCE_MS)
        runCurrent()
        assertEquals("Cm7", loopRepository.loops.value.single().notes)
        assertEquals("Cm7", viewModel.notes.value?.notes)
    }

    @Test
    fun `notes typed right before quitting are saved by the quit flush`() = runTest(main) {
        val viewModel = openWithLoop()
        val loopId = loopRepository.loops.value.single().id
        viewModel.onNotesChanged(loopId, "Cm7")
        advanceTimeBy(100)

        // The app quits: nothing advances the debounce or cancels the scope any more.
        viewModel.flushPendingNotes()

        assertEquals("Cm7", loopRepository.loops.value.single().notes)
        advanceTimeBy(PlayerViewModel.NOTES_SAVE_DEBOUNCE_MS * 2)
        runCurrent()
        assertEquals("Cm7", loopRepository.loops.value.single().notes)
    }

    @Test
    fun `progress reported after a cancel never brings the export back`() = runTest(main) {
        val exports = ParkedExports()
        val viewModel = openWithLoop(exports = exports)

        viewModel.onExportClicked()
        viewModel.onExportConfirmed()
        runCurrent()
        val onProgress = assertNotNull(exports.onProgress)
        onProgress(0.1f)
        assertEquals(ExportUiState.Running(0.1f, fileCount = 1), viewModel.export.value)

        viewModel.onExportCancelClicked()
        // The render thread's next chunk, already past its own check.
        onProgress(0.2f)
        runCurrent()

        assertEquals(ExportUiState.Idle, viewModel.export.value)
        assertTrue(viewModel.header.value.canExport)
    }

    @Test
    fun `when export is clicked then the options open on the full track with the track's loops`() = runTest(main) {
        val viewModel = openWithLoop()

        viewModel.onExportClicked()

        assertEquals(ExportUiState.Choosing(ExportScope.FULL_TRACK, loopCount = 1), viewModel.export.value)
        // Nothing renders yet: the controls stay usable.
        assertTrue(viewModel.header.value.canExport)
    }

    @Test
    fun `when both is confirmed then one batch renders the full track and every loop`() = runTest(main) {
        val exports = ParkedExports()
        val viewModel = openWithLoop(exports = exports)

        viewModel.onExportClicked()
        viewModel.onExportScopeChanged(ExportScope.BOTH)
        viewModel.onExportConfirmed()
        runCurrent()

        assertEquals(listOf("Easy Blues 100%.mp3", "Easy Blues - 01 Solo.mp3"), exports.items?.map { it.fileName })
        assertEquals(listOf(0f to 1f, 0.2f to 0.4f), exports.items?.map { it.start to it.end })
        assertEquals(ExportUiState.Running(0f, fileCount = 2), viewModel.export.value)
        assertFalse(viewModel.header.value.canExport)
    }

    @Test
    fun `when a batch is done then every file is ready and the folder save gets them all`() = runTest(main) {
        val exports = RecordingExports()
        val viewModel = openWithLoop(exports = exports)

        viewModel.onExportClicked()
        viewModel.onExportScopeChanged(ExportScope.BOTH)
        viewModel.onExportConfirmed()
        runCurrent()
        val ready = viewModel.export.value as ExportUiState.Ready
        assertEquals(2, ready.files.size)

        viewModel.onExportSaveFolderPicked("/home/me/Exports")
        runCurrent()

        assertEquals(ExportUiState.Idle, viewModel.export.value)
        assertEquals(ready.files to "/home/me/Exports", exports.savedToFolder)
    }

    @Test
    fun `the last export choice is offered again`() = runTest(main) {
        val viewModel = openWithLoop(exports = RecordingExports())

        viewModel.onExportClicked()
        viewModel.onExportScopeChanged(ExportScope.LOOPS)
        viewModel.onExportConfirmed()
        runCurrent()
        viewModel.onExportDismissed()
        runCurrent()
        viewModel.onExportClicked()

        assertEquals(ExportUiState.Choosing(ExportScope.LOOPS, loopCount = 1), viewModel.export.value)
    }

    @Test
    fun `when the track has no loops then only the full track can be exported`() = runTest(main) {
        val viewModel = openWithLoop()
        loopRepository.delete(loopRepository.loops.value.single().id)
        runCurrent()

        viewModel.onExportClicked()
        viewModel.onExportScopeChanged(ExportScope.LOOPS)
        viewModel.onExportScopeChanged(ExportScope.BOTH)

        assertEquals(ExportUiState.Choosing(ExportScope.FULL_TRACK, loopCount = 0), viewModel.export.value)
    }

    @Test
    fun `when the options are dismissed then nothing is exported`() = runTest(main) {
        val exports = ParkedExports()
        val viewModel = openWithLoop(exports = exports)

        viewModel.onExportClicked()
        viewModel.onExportDismissed()
        viewModel.onExportConfirmed()
        runCurrent()

        assertEquals(ExportUiState.Idle, viewModel.export.value)
        assertNull(exports.items)
    }

    @Test
    fun `without the seams Analyze, Metronome and Count-in do nothing`() = runTest(main) {
        val viewModel = openWithLoop()

        assertEquals(PlayerFeatures(canAnalyze = false, hasMetronome = false, hasCountIn = false), viewModel.features)
        viewModel.onAnalyzeClicked()
        viewModel.onMetronomeToggled()
        viewModel.onCountInToggled()
        runCurrent()

        assertEquals(AnalysisUiState(), viewModel.analysis.value)
        assertEquals(ClickUiState(), viewModel.clicks.value)
    }

    @Test
    fun `next and previous loop go round in time order`() = runTest(main) {
        val viewModel = openWithLoop()
        val second = assertNotNull(loopRepository.create(TRACK.id, "Later", LoopBounds(0.6f, 0.7f)))
        runCurrent()

        viewModel.onNextLoopClicked()
        runCurrent()
        assertEquals(second.id, loopRepository.trackLoops(TRACK.id).activeLoopId)
        assertEquals(0.6f, engine.position)

        viewModel.onNextLoopClicked()
        runCurrent()
        assertEquals(loopRepository.loops.value.first().id, loopRepository.trackLoops(TRACK.id).activeLoopId)
    }

    @Test
    fun `a click on the overview outside the active loop turns the loop off and plays on from there`() = runTest(main) {
        val viewModel = openWithLoop()
        val solo = loopRepository.loops.value.single()
        engine.isPlaying = true

        viewModel.onOverviewClicked(0.7f)
        runCurrent()

        assertFalse(engine.isLoopOn)
        assertEquals(0.7f, engine.position)
        assertTrue(engine.isPlaying)
        assertFalse(viewModel.transport.value.isLoopOn)
        assertEquals(solo.id, loopRepository.trackLoops(TRACK.id).activeLoopId, "the loop stays selected, just off")
    }

    @Test
    fun `a click in another loop's body while paused turns the loop off and stays paused`() = runTest(main) {
        val viewModel = openWithLoop()
        val solo = loopRepository.loops.value.single()
        assertNotNull(loopRepository.create(TRACK.id, "Later", LoopBounds(0.6f, 0.8f)))
        runCurrent()

        viewModel.onOverviewClicked(0.7f)
        runCurrent()

        assertFalse(engine.isLoopOn)
        assertEquals(0.7f, engine.position)
        assertFalse(engine.isPlaying)
        assertEquals(solo.id, loopRepository.trackLoops(TRACK.id).activeLoopId)
    }

    @Test
    fun `a click inside the active loop seeks there and keeps looping`() = runTest(main) {
        val viewModel = openWithLoop()
        engine.isPlaying = true

        viewModel.onOverviewClicked(0.33f)
        runCurrent()

        assertTrue(engine.isLoopOn)
        assertEquals(0.33f, engine.position)
        assertTrue(engine.isPlaying)
    }

    @Test
    fun `a click on another loop's top bar plays that loop from its start, also when paused`() = runTest(main) {
        val viewModel = openWithLoop()
        val later = assertNotNull(loopRepository.create(TRACK.id, "Later", LoopBounds(0.6f, 0.8f)))
        runCurrent()
        assertFalse(engine.isPlaying)

        viewModel.onLoopBarClicked(later.id, 0.75f)
        runCurrent()

        assertEquals(later.id, loopRepository.trackLoops(TRACK.id).activeLoopId)
        assertEquals(later.id, viewModel.loopChips.value.chips.single { it.isActive }.id)
        assertEquals(LoopRegion(0.6f, 0.8f, isOn = true), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
        assertEquals(0.6f, engine.position)
        assertTrue(engine.isPlaying)
        assertTrue(viewModel.transport.value.isPlaying)
    }

    @Test
    fun `a click on the bar of the loop playing in a loop seeks within it`() = runTest(main) {
        val viewModel = openWithLoop()
        engine.isPlaying = true
        tick(0.25f)

        viewModel.onLoopBarClicked(loopRepository.loops.value.single().id, 0.35f)
        runCurrent()

        assertEquals(0.35f, engine.position)
        assertTrue(engine.isLoopOn)
        assertTrue(engine.isPlaying)
    }

    @Test
    fun `a click on the bar of the looped loop while paused seeks within it and stays paused`() = runTest(main) {
        val viewModel = openWithLoop()
        assertTrue(engine.isLoopOn)
        assertFalse(engine.isPlaying)

        viewModel.onLoopBarClicked(loopRepository.loops.value.single().id, 0.35f)
        runCurrent()

        assertEquals(0.35f, engine.position)
        assertTrue(engine.isLoopOn)
        assertFalse(engine.isPlaying, "when paused only another loop's bar starts playback")
        assertFalse(viewModel.transport.value.isPlaying)
    }

    @Test
    fun `a click on the active loop's bar with the loop off plays it again from A`() = runTest(main) {
        val viewModel = openWithLoop()
        engine.isPlaying = true
        viewModel.onOverviewClicked(0.7f)
        runCurrent()
        assertFalse(engine.isLoopOn)

        viewModel.onLoopBarClicked(loopRepository.loops.value.single().id, 0.3f)
        runCurrent()

        assertTrue(engine.isLoopOn)
        assertEquals(0.2f, engine.position)
        assertTrue(engine.isPlaying)
    }

    @Test
    fun `grabbing another loop makes it active in place, then its drag is saved and looped`() = runTest(main) {
        val viewModel = openWithLoop()
        val later = assertNotNull(loopRepository.create(TRACK.id, "Later", LoopBounds(0.6f, 0.8f)))
        runCurrent()
        engine.isPlaying = true
        tick(0.3f)

        viewModel.onLoopGrabbed(later.id)
        viewModel.onLoopBoundsChanged(later.id, LoopBounds(0.65f, 0.85f))
        runCurrent()

        assertEquals(later.id, loopRepository.trackLoops(TRACK.id).activeLoopId)
        assertEquals(LoopBounds(0.65f, 0.85f), loopRepository.loops.value.first { it.id == later.id }.bounds)
        assertEquals(LoopRegion(0.65f, 0.85f, isOn = true), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
        assertTrue(engine.isPlaying)
        assertEquals(LoopBounds(0.65f, 0.85f), viewModel.zoom.value?.let { LoopBounds(it.start, it.end) }, "the zoom strip follows")
    }

    @Test
    fun `a dragged loop becomes active even without its grab, and looping stays off`() = runTest(main) {
        val viewModel = openWithLoop()
        val later = assertNotNull(loopRepository.create(TRACK.id, "Later", LoopBounds(0.6f, 0.8f)))
        viewModel.onLoopToggled()
        runCurrent()
        assertFalse(engine.isLoopOn)
        engine.position = 0.1f

        viewModel.onLoopBoundsChanged(later.id, LoopBounds(0.5f, 0.7f))
        runCurrent()

        assertEquals(later.id, loopRepository.trackLoops(TRACK.id).activeLoopId)
        assertEquals(LoopRegion(0.5f, 0.7f, isOn = false), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
        assertEquals(0.1f, engine.position, "a drag never moves the playhead")
    }

    @Test
    fun `Esc closes the drawer first, then focus mode`() = runTest(main) {
        val viewModel = viewModel()
        viewModel.onFocusToggled()
        viewModel.onDrawerOpened()

        assertTrue(viewModel.onEscape())
        assertEquals(PlayerLayout(isFocusMode = true, isDrawerOpen = false), viewModel.layout.value)
        assertTrue(viewModel.onEscape())
        assertEquals(PlayerLayout(), viewModel.layout.value)
        assertFalse(viewModel.onEscape())
    }

    @Test
    fun `an out of range BPM is refused`() = runTest(main) {
        val viewModel = openWithLoop()

        viewModel.onBpmChanged(1000f)
        runCurrent()
        assertNull(loopRepository.trackLoops(TRACK.id).bpm)

        viewModel.onBpmChanged(92f)
        runCurrent()
        assertEquals(92f, viewModel.header.value.bpm)
    }

    @Test
    fun `with a BPM, A and B still land exactly on the playhead`() = runTest(main) {
        val viewModel = openWithLoop()
        viewModel.onBpmChanged(120f)
        runCurrent()

        engine.position = 0.2137f
        viewModel.onSetLoopStartAtPlayhead()
        runCurrent()
        engine.position = 0.3561f
        viewModel.onSetLoopEndAtPlayhead()
        runCurrent()

        assertEquals(LoopBounds(0.2137f, 0.3561f), loopRepository.loops.value.single().bounds)
    }

    @Test
    fun `a nudge moves the loop by one second, with or without a BPM`() = runTest(main) {
        val viewModel = openWithLoop()

        viewModel.onNudgeLoop(1)
        runCurrent()
        // The fake track is 100 s long: one second is 1% of it.
        val once = loopRepository.loops.value.single().bounds
        assertEquals(0.21f, once.start, 1e-5f)
        assertEquals(0.41f, once.end, 1e-5f)

        viewModel.onBpmChanged(92f)
        runCurrent()
        viewModel.onNudgeLoop(-1)
        runCurrent()
        val back = loopRepository.loops.value.single().bounds
        assertEquals(0.2f, back.start, 1e-5f)
        assertEquals(0.4f, back.end, 1e-5f)
    }

    /** Opens [TRACK] with one loop 0.2..0.4, active and looping. */
    private suspend fun TestScope.openWithLoop(
        passes: Int = 0,
        progression: Progression = Progression(),
        exports: ExportRepository = NoExports(),
    ): PlayerViewModel {
        val loop = assertNotNull(loopRepository.create(TRACK.id, "Solo", LoopBounds(0.2f, 0.4f)))
        repeat(passes) { loopRepository.addPass(loop.id) }
        loopRepository.updateProgression(loop.id, progression)
        val viewModel = viewModel(exports)
        runCurrent()
        viewModel.onTrackSelected(TRACK)
        runCurrent()
        decodeScheduler.runCurrent()
        runCurrent()
        viewModel.onLoopSelected(loop.id)
        runCurrent()
        assertTrue(engine.isLoopOn)
        return viewModel
    }

    private fun TestScope.tick(vararg positions: Float) = positions.forEach {
        engine.position = it
        runCurrent()
    }

    private fun viewModel(exports: ExportRepository = NoExports()): PlayerViewModel {
        val decode = StandardTestDispatcher(decodeScheduler)
        return PlayerViewModel(
            engine = engine,
            openTrack = OpenTrackForPracticeUseCase(
                OpenTrackUseCase(FakeAudioSourceRepository(), nowPlaying, decode),
                SavePracticeStateUseCase(engine, nowPlaying, practiceRepository),
                engine,
                practiceRepository,
                loopRepository,
            ),
            observeNowPlaying = ObserveNowPlayingUseCase(nowPlaying),
            practice = PracticeUseCases(
                StepSpeedUseCase(engine),
                StepPitchUseCase(engine),
                ObservePresetsUseCase(practiceRepository),
                SavePresetUseCase(engine, nowPlaying, practiceRepository),
                ApplyPresetUseCase(engine, loopRepository, nowPlaying),
                DeletePresetUseCase(practiceRepository),
            ),
            loops = LoopUseCases(
                observe = ObserveLoopsUseCase(observedLoops),
                create = CreateLoopUseCase(loopRepository, engine, nowPlaying, SetActiveLoopUseCase(observedLoops, engine, nowPlaying)),
                setActive = SetActiveLoopUseCase(observedLoops, engine, nowPlaying),
                rename = RenameLoopUseCase(loopRepository),
                updateNotes = UpdateLoopNotesUseCase(loopRepository),
                updateRegion = UpdateLoopRegionUseCase(loopRepository, engine, nowPlaying),
                delete = DeleteLoopUseCase(loopRepository, engine, nowPlaying),
                recordPass = RecordLoopPassUseCase(loopRepository, engine, nowPlaying),
                resetPasses = ResetLoopPassesUseCase(loopRepository, engine, nowPlaying),
                updateProgression = UpdateLoopProgressionUseCase(loopRepository, engine, nowPlaying),
                setBpm = SetTrackBpmUseCase(loopRepository),
            ),
            seams = PracticeSeams(AnalyzeSectionsUseCase(null), SetMetronomeUseCase(null), SetCountInUseCase(null)),
            exports = ExportUseCases(ExportTrackUseCase(engine, nowPlaying, loopRepository, exports), SaveExportUseCase(exports)),
            background = main,
        ).also { store.put("player", it) }
    }

    /**
     * [FakeEngine] that, like the real one, reports every change that happened as an event, and counts
     * how often the controls are read.
     */
    private class EventfulEngine(private val fake: FakeEngine = FakeEngine()) : PlaybackEngine by fake {
        var controlReads = 0
        /** The next setters change nothing, like the real engine given its current value. */
        var quiet = false

        override var isPlaying: Boolean
            get() = fake.isPlaying
            set(value) { fake.isPlaying = value }
        override var position: Float
            get() = fake.position
            set(value) { fake.position = value }
        // Only a refresh of the controls reads the slider position (use cases read the percent).
        override val speedSliderPosition: Int get() = fake.speedSliderPosition.also { controlReads++ }

        override fun togglePlayStop() = changed(PlaybackEvent.PlayStateChanged) { fake.togglePlayStop() }
        override fun seekTo(position: Float) = changed(PlaybackEvent.PositionChanged) { fake.seekTo(position) }
        override fun setSpeedPercent(percent: Float) = changed(PlaybackEvent.PracticeSettingsChanged) { fake.setSpeedPercent(percent) }
        override fun setPitchSemitones(semitones: Float) = changed(PlaybackEvent.PracticeSettingsChanged) { fake.setPitchSemitones(semitones) }
        override fun setLoop(region: LoopRegion) = changed(PlaybackEvent.LoopChanged) { fake.setLoop(region) }
        override fun setLoopOn(enabled: Boolean) = changed(PlaybackEvent.LoopChanged) { fake.setLoopOn(enabled) }

        private inline fun changed(event: PlaybackEvent, change: () -> Unit) {
            if (quiet) return
            change()
            fake.events.tryEmit(event)
        }
    }

    private class NoExports : ExportRepository {
        override suspend fun exportOpenTrack(items: List<ExportItem>, onProgress: (Float) -> Unit) =
            ExportResult.Failure(UnsupportedOperationException())
        override suspend fun saveTo(file: ExportedFile, contentUri: String) = false
        override suspend fun saveAllTo(files: List<ExportedFile>, folder: String) = false
    }

    /** Every batch succeeds at once; remembers the last folder save. */
    private class RecordingExports : ExportRepository {
        var savedToFolder: Pair<List<ExportedFile>, String>? = null
        override suspend fun exportOpenTrack(items: List<ExportItem>, onProgress: (Float) -> Unit) =
            ExportResult.Success(items.map { ExportedFile("/exports/${it.fileName}", it.fileName) })
        override suspend fun saveTo(file: ExportedFile, contentUri: String) = true
        override suspend fun saveAllTo(files: List<ExportedFile>, folder: String): Boolean {
            savedToFolder = files to folder
            return true
        }
    }

    /** Passes [observe] through, except that [hold] freezes it at what it showed last until [release]. */
    private class HeldLoops(private val live: FakeLoopRepository) : LoopRepository by live {
        private val held = MutableStateFlow<TrackLoops?>(null)
        private var last: TrackLoops? = null

        private var activeWrite: CompletableDeferred<Unit>? = null

        fun hold() { held.value = last }
        fun release() { held.value = null }

        /** The next [setActiveLoop] waits until the returned deferred completes. */
        fun slowActiveWrite() = CompletableDeferred<Unit>().also { activeWrite = it }

        override suspend fun setActiveLoop(trackId: String, loopId: Long?) {
            activeWrite?.await()
            activeWrite = null
            live.setActiveLoop(trackId, loopId)
        }

        override fun observe(trackId: String): Flow<TrackLoops> =
            combine(live.observe(trackId), held) { current, frozen -> frozen ?: current }
                .onEach { last = it }
    }

    /** Keeps the render's progress callback and never finishes on its own. */
    private class ParkedExports : ExportRepository {
        var onProgress: ((Float) -> Unit)? = null
        var items: List<ExportItem>? = null
        override suspend fun exportOpenTrack(items: List<ExportItem>, onProgress: (Float) -> Unit): ExportResult {
            this.items = items
            this.onProgress = onProgress
            awaitCancellation()
        }
        override suspend fun saveTo(file: ExportedFile, contentUri: String) = false
        override suspend fun saveAllTo(files: List<ExportedFile>, folder: String) = false
    }

    private companion object {
        val TRACK = LibraryTrack("a", "Easy Blues", "/music/easy-blues.mp3", null, 0)
        val OTHER = LibraryTrack("b", "Little Wing", "/music/little-wing.mp3", "https://youtu.be/x", 0)
    }
}
