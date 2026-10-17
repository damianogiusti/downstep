package com.damianogiusti.slowdowner.data.loops

import com.damianogiusti.slowdowner.data.database.desktopDatabase
import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.practice.DefaultPracticeRepository
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsMapper
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.loops.TrackLoops
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.tempo.TempoEstimate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Against the real Room schema: the loop columns share a row with the auto-saved practice state.
 * Unconfined, not a test dispatcher: Room's flows resume on its own threads.
 */
class DefaultLoopRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val database by lazy { desktopDatabase(folder.root.resolve("test.db")) }
    private val repository by lazy { DefaultLoopRepository(database.loopDao(), Dispatchers.Unconfined, now = { 42 }) }
    private val practice by lazy { DefaultPracticeRepository(database.practiceDao(), Dispatchers.Unconfined, now = { 43 }) }

    @After
    fun tearDown() = database.close()

    @Test
    fun `when loops are created then they are observed sorted by start with defaults`() = runTest {
        addTrack("t")
        val late = repository.create("t", "Chorus", LoopBounds(0.5f, 0.6f))!!
        val early = repository.create("t", "Verse", LoopBounds(0.1f, 0.2f))!!

        val observed = repository.observe("t").first()

        assertEquals(listOf(early, late), observed.loops)
        assertEquals(Progression.DEFAULT, early.progression)
        assertEquals(0, early.passes)
        assertEquals(42L, early.createdAt)
    }

    @Test
    fun `when the track does not exist then no loop is created`() = runTest {
        assertNull(repository.create("missing", "Loop 1", LoopBounds(0.1f, 0.2f)))
    }

    @Test
    fun `when a loop is edited then every field is saved`() = runTest {
        addTrack("t")
        val loop = repository.create("t", "Loop 1", LoopBounds(0.1f, 0.2f))!!
        val progression = Progression(enabled = true, fromPercent = 60f, toPercent = 90f, stepPercent = 5f, everyPasses = 3)

        repository.rename(loop.id, "Solo")
        repository.updateNotes(loop.id, "Am – G – F")
        repository.updateBounds(loop.id, LoopBounds(0.3f, 0.35f))
        repository.updateProgression(loop.id, progression)
        repository.addPass(loop.id)
        val afterTwo = repository.addPass(loop.id)!!

        assertEquals(
            loop.copy(name = "Solo", notes = "Am – G – F", start = 0.3f, end = 0.35f, progression = progression, passes = 2),
            afterTwo,
        )
        repository.resetPasses(loop.id)
        assertEquals(0, repository.loop(loop.id)!!.passes)
    }

    @Test
    fun `when the tempo is set before any practice state then the track gets one with defaults`() = runTest {
        addTrack("t")

        repository.setBpm("t", 92f)

        assertEquals(TrackLoops("t", bpm = 92f), repository.trackLoops("t"))
        assertEquals(PracticeSettings(), practice.lastState("t"))
    }

    @Test
    fun `when the practice state is auto-saved then the loop settings are kept`() = runTest {
        addTrack("t")
        val loop = repository.create("t", "Loop 1", LoopBounds(0.1f, 0.2f))!!
        repository.setActiveLoop("t", loop.id)
        repository.setBpm("t", 120f)

        practice.saveLastState("t", PracticeSettings(80f, 1f, LoopRegion(0.1f, 0.2f, isOn = true)))

        val after = repository.trackLoops("t")
        assertEquals(loop.id, after.activeLoopId)
        assertEquals(120f, after.bpm)
        assertEquals(80f, practice.lastState("t")!!.speedPercent)
    }

    @Test
    fun `when the practice state is auto-saved then the track's EQ and detected tempo are kept`() = runTest {
        addTrack("t")
        database.practiceDao().setTrackEqCurve("t", EQ_JSON, now = 1)
        database.loopDao().setDetectedTempo("t", detectedBpm = 128f, beatOffsetSeconds = 0.25f, analysisVersion = 2)

        practice.saveLastState("t", PracticeSettings(80f, 1f, LoopRegion(0.1f, 0.2f, isOn = true)))
        practice.saveLastState("t", PracticeSettings(90f, 0f, LoopRegion(0f, 1f, isOn = false)))

        assertEquals(EQ_JSON, database.practiceDao().trackEqCurve("t"))
        assertEquals(
            TrackLoops("t", detectedBpm = 128f, beatOffsetSeconds = 0.25f, tempoAnalysisVersion = 2),
            repository.trackLoops("t"),
        )
        assertEquals(128f, repository.trackLoops("t").effectiveBpm)
        assertEquals(90f, practice.lastState("t")!!.speedPercent)
    }

    @Test
    fun `when the track EQ is set before any practice state then the row is created with defaults`() = runTest {
        addTrack("t")

        database.practiceDao().setTrackEqCurve("t", EQ_JSON, now = 1)
        database.practiceDao().setTrackEqCurve("missing", EQ_JSON, now = 1)

        assertEquals(EQ_JSON, database.practiceDao().trackEqCurve("t"))
        assertEquals(PracticeSettings(), practice.lastState("t"))
        assertNull(database.practiceDao().trackEqCurve("missing"))
        database.practiceDao().setTrackEqCurve("t", null, now = 2)
        assertNull(database.practiceDao().trackEqCurve("t"))
    }

    @Test
    fun `when the beat offset is moved then the detected tempo stays`() = runTest {
        addTrack("t")
        database.loopDao().ensureStateRow("t", PracticeSettingsMapper.toColumns(PracticeSettings()), now = 1)
        database.loopDao().setDetectedTempo("t", detectedBpm = 100f, beatOffsetSeconds = 0.1f, analysisVersion = 1)

        database.loopDao().setBeatOffset("t", 0.4f)

        val loops = repository.trackLoops("t")
        assertEquals(100f, loops.detectedBpm)
        assertEquals(0.4f, loops.beatOffsetSeconds)
        assertEquals(1, loops.tempoAnalysisVersion)
    }

    @Test
    fun `when a tempo analysis is saved before any practice state then it is kept, nothing found included`() = runTest {
        addTrack("t")
        addTrack("u")

        repository.setDetectedTempo("t", TempoEstimate(128f, 0.25f, 0.9f), analysisVersion = 1)
        repository.setDetectedTempo("u", null, analysisVersion = 1)
        repository.setBpm("t", 64f)
        repository.setBeatOffset("t", 0.5f)

        assertEquals(
            TrackLoops("t", bpm = 64f, detectedBpm = 128f, beatOffsetSeconds = 0.5f, tempoAnalysisVersion = 1),
            repository.trackLoops("t"),
        )
        assertEquals(TrackLoops("u", tempoAnalysisVersion = 1), repository.trackLoops("u"))
        assertEquals(PracticeSettings(), practice.lastState("u"))
    }

    @Test
    fun `when the active loop is deleted then the stale id reads as no active loop`() = runTest {
        addTrack("t")
        val loop = repository.create("t", "Loop 1", LoopBounds(0.1f, 0.2f))!!
        repository.setActiveLoop("t", loop.id)

        repository.delete(loop.id)

        assertNull(repository.trackLoops("t").activeLoopId)
    }

    @Test
    fun `when a track is deleted then its loops cascade`() = runTest {
        addTrack("t")
        val loop = repository.create("t", "Loop 1", LoopBounds(0.1f, 0.2f))!!

        database.trackDao().delete("t")

        assertNull(repository.loop(loop.id))
        assertEquals(TrackLoops("t"), repository.trackLoops("t"))
    }

    private suspend fun addTrack(id: String) = database.trackDao().upsert(TrackEntity(id, "Song", "/tmp/$id.m4a", null, addedAt = 1))

    private companion object {
        // The DAO stores the curve as it gets it: any JSON will do here.
        const val EQ_JSON = """{"enabled":true,"preampDb":-3.0,"bands":[{"frequencyHz":250.0,"gainDb":4.0,"q":1.0}]}"""
    }
}
