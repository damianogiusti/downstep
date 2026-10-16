package com.damianogiusti.slowdowner.data.loops

import com.damianogiusti.slowdowner.data.database.desktopDatabase
import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.practice.DefaultPracticeRepository
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.loops.TrackLoops
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        repository.setSnapToBeat("t", false)

        assertEquals(TrackLoops("t", bpm = 92f, snapToBeat = false), repository.trackLoops("t"))
        assertEquals(PracticeSettings(), practice.lastState("t"))
    }

    @Test
    fun `when the practice state is auto-saved then the loop settings are kept`() = runTest {
        addTrack("t")
        val loop = repository.create("t", "Loop 1", LoopBounds(0.1f, 0.2f))!!
        repository.setActiveLoop("t", loop.id)
        repository.setBpm("t", 120f)
        repository.setSnapToBeat("t", false)

        practice.saveLastState("t", PracticeSettings(80f, 1f, LoopRegion(0.1f, 0.2f, isOn = true)))

        val after = repository.trackLoops("t")
        assertEquals(loop.id, after.activeLoopId)
        assertEquals(120f, after.bpm)
        assertFalse(after.snapToBeat)
        assertEquals(80f, practice.lastState("t")!!.speedPercent)
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
}
