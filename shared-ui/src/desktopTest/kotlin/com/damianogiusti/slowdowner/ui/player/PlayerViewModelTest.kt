package com.damianogiusti.slowdowner.ui.player

import androidx.lifecycle.ViewModelStore
import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.analysis.AnalyzeSectionsUseCase
import com.damianogiusti.slowdowner.domain.click.SetCountInUseCase
import com.damianogiusti.slowdowner.domain.click.SetMetronomeUseCase
import com.damianogiusti.slowdowner.domain.export.ExportRepository
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportTrackUseCase
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.export.SaveExportUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.loops.CreateLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.DeleteLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.ObserveLoopsUseCase
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.loops.RecordLoopPassUseCase
import com.damianogiusti.slowdowner.domain.loops.RenameLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.ResetLoopPassesUseCase
import com.damianogiusti.slowdowner.domain.loops.SetActiveLoopUseCase
import com.damianogiusti.slowdowner.domain.loops.SetSnapToBeatUseCase
import com.damianogiusti.slowdowner.domain.loops.SetTrackBpmUseCase
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
import com.damianogiusti.slowdowner.domain.practice.SavePracticeStateUseCase
import com.damianogiusti.slowdowner.domain.practice.SavePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.StepPitchUseCase
import com.damianogiusti.slowdowner.domain.practice.StepSpeedUseCase
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeLoopRepository
import com.damianogiusti.slowdowner.fake.FakePracticeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        assertTrue(viewModel.loopTools.value.canSnap)
    }

    /** Opens [TRACK] with one loop 0.2..0.4, active and looping. */
    private suspend fun TestScope.openWithLoop(passes: Int = 0, progression: Progression = Progression()): PlayerViewModel {
        val loop = assertNotNull(loopRepository.create(TRACK.id, "Solo", LoopBounds(0.2f, 0.4f)))
        repeat(passes) { loopRepository.addPass(loop.id) }
        loopRepository.updateProgression(loop.id, progression)
        val viewModel = viewModel()
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

    private fun viewModel(): PlayerViewModel {
        val decode = StandardTestDispatcher(decodeScheduler)
        val exports = NoExports()
        return PlayerViewModel(
            engine = engine,
            openTrack = OpenTrackForPracticeUseCase(
                OpenTrackUseCase(FakeAudioSourceRepository(), nowPlaying, decode),
                SavePracticeStateUseCase(engine, nowPlaying, practiceRepository),
                engine,
                practiceRepository,
            ),
            observeNowPlaying = ObserveNowPlayingUseCase(nowPlaying),
            practice = PracticeUseCases(
                StepSpeedUseCase(engine),
                StepPitchUseCase(engine),
                ObservePresetsUseCase(practiceRepository),
                SavePresetUseCase(engine, nowPlaying, practiceRepository),
                ApplyPresetUseCase(engine),
                DeletePresetUseCase(practiceRepository),
            ),
            loops = LoopUseCases(
                observe = ObserveLoopsUseCase(loopRepository),
                create = CreateLoopUseCase(loopRepository, engine, nowPlaying, SetActiveLoopUseCase(loopRepository, engine, nowPlaying)),
                setActive = SetActiveLoopUseCase(loopRepository, engine, nowPlaying),
                rename = RenameLoopUseCase(loopRepository),
                updateNotes = UpdateLoopNotesUseCase(loopRepository),
                updateRegion = UpdateLoopRegionUseCase(loopRepository, engine, nowPlaying),
                delete = DeleteLoopUseCase(loopRepository, engine, nowPlaying),
                recordPass = RecordLoopPassUseCase(loopRepository, engine, nowPlaying),
                resetPasses = ResetLoopPassesUseCase(loopRepository, engine, nowPlaying),
                updateProgression = UpdateLoopProgressionUseCase(loopRepository, engine, nowPlaying),
                setBpm = SetTrackBpmUseCase(loopRepository),
                setSnapToBeat = SetSnapToBeatUseCase(loopRepository),
            ),
            seams = PracticeSeams(AnalyzeSectionsUseCase(null), SetMetronomeUseCase(null), SetCountInUseCase(null)),
            exports = ExportUseCases(ExportTrackUseCase(engine, nowPlaying, exports), SaveExportUseCase(exports)),
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
        override suspend fun exportOpenTrack(fileName: String, onProgress: (Float) -> Unit) = ExportResult.Failure(UnsupportedOperationException())
        override suspend fun saveTo(file: ExportedFile, contentUri: String) = false
    }

    private companion object {
        val TRACK = LibraryTrack("a", "Easy Blues", "/music/easy-blues.mp3", null, 0)
        val OTHER = LibraryTrack("b", "Little Wing", "/music/little-wing.mp3", "https://youtu.be/x", 0)
    }
}
