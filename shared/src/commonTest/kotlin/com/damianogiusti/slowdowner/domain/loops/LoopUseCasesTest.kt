package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeLoopRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoopUseCasesTest {

    private val engine = FakeEngine()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val repository = FakeLoopRepository()
    private val setActive = SetActiveLoopUseCase(repository, engine, nowPlaying)
    private val createLoop = CreateLoopUseCase(repository, engine, nowPlaying, setActive)
    private val deleteLoop = DeleteLoopUseCase(repository, engine, nowPlaying)
    private val updateRegion = UpdateLoopRegionUseCase(repository, engine, nowPlaying)
    private val recordPass = RecordLoopPassUseCase(repository, engine, nowPlaying)
    private val resetPasses = ResetLoopPassesUseCase(repository, engine, nowPlaying)
    private val updateProgression = UpdateLoopProgressionUseCase(repository, engine, nowPlaying)
    private val song = LibraryTrack("a", "A", "/a.m4a", null, 0)

    @Test
    fun `when loops are created without a name then they are numbered after the highest one`() = runTest {
        nowPlaying.set(song)
        createLoop("a", LoopBounds(0.1f, 0.2f))
        createLoop("a", LoopBounds(0.3f, 0.4f), name = "Chorus")
        repository.rename(1, "Loop 7")

        val third = createLoop("a", LoopBounds(0.5f, 0.6f))!!

        assertEquals("Loop 8", third.name)
    }

    @Test
    fun `when the given name is not valid then the default name is used`() = runTest {
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f), name = "   ")!!

        assertEquals("Loop 1", loop.name)
    }

    @Test
    fun `when a loop is created then it becomes active and the engine loops it from its start`() = runTest {
        nowPlaying.set(song)

        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!

        assertEquals(loop.id, repository.trackLoops("a").activeLoopId)
        assertEquals(LoopRegion(0.1f, 0.2f, isOn = true), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
        assertEquals(0.1f, engine.position)
    }

    @Test
    fun `when the track is not the open one then the engine is left alone`() = runTest {
        nowPlaying.set(song)

        createLoop("b", LoopBounds(0.1f, 0.2f))

        assertTrue(engine.practiceCalls.isEmpty())
    }

    @Test
    fun `when the track is gone then nothing is created`() = runTest {
        assertNull(createLoop("deleted", LoopBounds(0.1f, 0.2f)))
    }

    @Test
    fun `when loops are observed then they come sorted by start with the active one`() = runTest {
        val late = createLoop("a", LoopBounds(0.5f, 0.6f))!!
        val early = createLoop("a", LoopBounds(0.1f, 0.2f), activate = false)!!

        val observed = ObserveLoopsUseCase(repository)("a").first()

        assertEquals(listOf(early.id, late.id), observed.loops.map { it.id })
        assertEquals(late, observed.activeLoop)
    }

    @Test
    fun `when the active loop is deleted then the first remaining loop takes over in the engine`() = runTest {
        nowPlaying.set(song)
        val first = createLoop("a", LoopBounds(0.1f, 0.2f), activate = false)!!
        val active = createLoop("a", LoopBounds(0.5f, 0.6f))!!

        deleteLoop(active.id)

        assertEquals(first.id, repository.trackLoops("a").activeLoopId)
        assertEquals(LoopRegion(0.1f, 0.2f, isOn = true), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
    }

    @Test
    fun `when the last loop is deleted then the engine plays the whole file`() = runTest {
        nowPlaying.set(song)
        val only = createLoop("a", LoopBounds(0.1f, 0.2f))!!

        deleteLoop(only.id)

        assertNull(repository.trackLoops("a").activeLoopId)
        assertEquals(LoopRegion.WHOLE_FILE, LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
    }

    @Test
    fun `when an inactive loop is deleted then the active one stays`() = runTest {
        val other = createLoop("a", LoopBounds(0.1f, 0.2f), activate = false)!!
        val active = createLoop("a", LoopBounds(0.5f, 0.6f))!!

        deleteLoop(other.id)

        assertEquals(active.id, repository.trackLoops("a").activeLoopId)
    }

    @Test
    fun `when the active loop moves then the engine follows and keeps looping off`() = runTest {
        nowPlaying.set(song)
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!
        engine.setLoopOn(false)

        updateRegion(loop.id, LoopBounds(0.3f, 0.4f))

        assertEquals(LoopRegion(0.3f, 0.4f, isOn = false), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
        assertEquals(0.3f, repository.loop(loop.id)!!.start)
    }

    @Test
    fun `when an inactive loop moves then the engine keeps the active region`() = runTest {
        nowPlaying.set(song)
        val other = createLoop("a", LoopBounds(0.1f, 0.2f), activate = false)!!
        createLoop("a", LoopBounds(0.5f, 0.6f))

        updateRegion(other.id, LoopBounds(0.3f, 0.4f))

        assertEquals(0.5f, engine.loopStart)
    }

    @Test
    fun `when a region is too short then it is widened to the minimum`() = runTest {
        nowPlaying.set(song)
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!

        val updated = updateRegion(loop.id, LoopBounds(0.5f, 0.5f))!!

        assertEquals(LoopMath.minLength(engine.durationSeconds), updated.end - updated.start, 1e-6f)
    }

    @Test
    fun `when a pass is recorded with progression on then the speed follows the ramp`() = runTest {
        nowPlaying.set(song)
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!
        updateProgression(loop.id, Progression(enabled = true))
        assertEquals(70f, engine.speedPercent)

        recordPass(loop.id)
        val afterTwo = recordPass(loop.id)!!

        assertEquals(2, afterTwo.passes)
        assertEquals(73f, engine.speedPercent)
    }

    @Test
    fun `when a pass is recorded with progression off then the speed is left alone`() = runTest {
        nowPlaying.set(song)
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!
        engine.setSpeedPercent(85f)

        recordPass(loop.id)
        recordPass(loop.id)

        assertEquals(85f, engine.speedPercent)
    }

    @Test
    fun `when passes are reset then the ramp starts again`() = runTest {
        nowPlaying.set(song)
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!
        updateProgression(loop.id, Progression(enabled = true))
        repeat(4) { recordPass(loop.id) }

        resetPasses(loop.id)

        assertEquals(0, repository.loop(loop.id)!!.passes)
        assertEquals(70f, engine.speedPercent)
    }

    @Test
    fun `when progression is turned on then the ramp starts from From`() = runTest {
        nowPlaying.set(song)
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!
        repeat(12) { recordPass(loop.id) }

        val updated = updateProgression(loop.id, Progression(enabled = true))!!

        assertEquals(0, updated.passes)
        assertEquals(0, repository.loop(loop.id)!!.passes)
        assertEquals(70f, engine.speedPercent)
    }

    @Test
    fun `when a stepper changes while progression is on then the passes are kept`() = runTest {
        nowPlaying.set(song)
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!
        updateProgression(loop.id, Progression(enabled = true))
        repeat(4) { recordPass(loop.id) }

        updateProgression(loop.id, Progression(enabled = true, stepPercent = 5f))

        assertEquals(4, repository.loop(loop.id)!!.passes)
        assertEquals(80f, engine.speedPercent)
    }

    @Test
    fun `when a progression is saved then it is brought into range`() = runTest {
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!

        updateProgression(loop.id, Progression(everyPasses = 0, stepPercent = 99f))

        assertEquals(Progression(everyPasses = 1, stepPercent = Progression.MAX_STEP_PERCENT), repository.loop(loop.id)!!.progression)
    }

    @Test
    fun `when a loop is activated then it loops from its start with its ramp speed`() = runTest {
        nowPlaying.set(song)
        val first = createLoop("a", LoopBounds(0.1f, 0.2f))!!
        updateProgression(first.id, Progression(enabled = true, fromPercent = 60f))
        createLoop("a", LoopBounds(0.5f, 0.6f))
        engine.setSpeedPercent(100f)

        setActive("a", first.id)

        assertEquals(0.1f, engine.position)
        assertEquals(60f, engine.speedPercent)
        assertTrue(engine.isLoopOn)
    }

    @Test
    fun `when no loop is activated then looping turns off`() = runTest {
        nowPlaying.set(song)
        createLoop("a", LoopBounds(0.1f, 0.2f))

        setActive("a", null)

        assertNull(repository.trackLoops("a").activeLoopId)
        assertFalse(engine.isLoopOn)
    }

    @Test
    fun `when a loop of another track is activated then nothing becomes active`() = runTest {
        val other = createLoop("b", LoopBounds(0.1f, 0.2f))!!

        setActive("a", other.id)

        assertNull(repository.trackLoops("a").activeLoopId)
    }

    @Test
    fun `when renamed with a blank or long name then the name is kept`() = runTest {
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!
        val rename = RenameLoopUseCase(repository)

        assertFalse(rename(loop.id, "  "))
        assertFalse(rename(loop.id, "x".repeat(Loop.MAX_NAME_LENGTH + 1)))
        assertTrue(rename(loop.id, "  Solo "))
        assertEquals("Solo", repository.loop(loop.id)!!.name)
    }

    @Test
    fun `when notes are saved then they are capped`() = runTest {
        val loop = createLoop("a", LoopBounds(0.1f, 0.2f))!!

        UpdateLoopNotesUseCase(repository)(loop.id, "x".repeat(Loop.MAX_NOTES_LENGTH + 5))

        assertEquals(Loop.MAX_NOTES_LENGTH, repository.loop(loop.id)!!.notes.length)
    }

    @Test
    fun `when the tempo is out of range then it is not saved`() = runTest {
        val setBpm = SetTrackBpmUseCase(repository)

        assertFalse(setBpm("a", 5f))
        assertFalse(setBpm("a", Float.NaN))
        assertTrue(setBpm("a", 92f))
        assertEquals(92f, repository.trackLoops("a").bpm)
        assertTrue(setBpm("a", null))
        assertNull(repository.trackLoops("a").bpm)
    }
}
