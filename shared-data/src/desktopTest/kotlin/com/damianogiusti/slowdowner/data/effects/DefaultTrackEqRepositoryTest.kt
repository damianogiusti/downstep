package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.data.database.desktopDatabase
import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.practice.DefaultPracticeRepository
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.practice.SavePresetResult
import com.damianogiusti.slowdowner.fake.FakePracticeDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTrackEqRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val curve = EqCurve(enabled = true, preampDb = -1f, bands = listOf(EqBand(80f, 5f, 0.7f), EqBand(2_500f, -3f, 1.5f)))
    private val settings = PracticeSettings(80f, 1f, LoopRegion(0.1f, 0.3f, isOn = true))

    @Test
    fun `when a track has no EQ of its own then none is read`() = runTest {
        val repository = DefaultTrackEqRepository(FakePracticeDao(setOf("a")), UnconfinedTestDispatcher())

        assertNull(repository.trackEq("a"))
    }

    @Test
    fun `when a track's EQ is saved then it is read back, and null goes back to the global one`() = runTest {
        val repository = DefaultTrackEqRepository(FakePracticeDao(setOf("a")), UnconfinedTestDispatcher())

        assertTrue(repository.saveTrackEq("a", curve))
        assertEquals(curve, repository.trackEq("a"))

        assertTrue(repository.saveTrackEq("a", null))
        assertNull(repository.trackEq("a"))
    }

    @Test
    fun `when the track is not in the library then nothing is saved`() = runTest {
        val repository = DefaultTrackEqRepository(FakePracticeDao(setOf("a")), UnconfinedTestDispatcher())

        assertFalse(repository.saveTrackEq("spotify:1", curve))
        assertNull(repository.trackEq("spotify:1"))
    }

    // The real database: the practice state's auto-save rewrites the whole row.
    @Test
    fun `when the practice state is saved after the EQ then the track keeps its EQ`() = runTest {
        val database = desktopDatabase(folder.root.resolve("eq.db"))
        try {
            database.trackDao().upsert(TrackEntity("t1", "Song", "/tmp/t1.m4a", null, addedAt = 1))
            val trackEq = DefaultTrackEqRepository(database.practiceDao(), UnconfinedTestDispatcher())
            val practice = DefaultPracticeRepository(database.practiceDao(), UnconfinedTestDispatcher())

            trackEq.saveTrackEq("t1", curve)
            practice.saveLastState("t1", settings)
            practice.saveLastState("t1", settings.copy(speedPercent = 60f))

            assertEquals(curve, trackEq.trackEq("t1"))
            assertEquals(60f, practice.lastState("t1")!!.speedPercent)
        } finally {
            database.close()
        }
    }

    @Test
    fun `when a preset is saved with an EQ then it comes back with it, and one without has none`() = runTest {
        val database = desktopDatabase(folder.root.resolve("presets.db"))
        try {
            database.trackDao().upsert(TrackEntity("t1", "Song", "/tmp/t1.m4a", null, addedAt = 1))
            val practice = DefaultPracticeRepository(database.practiceDao(), UnconfinedTestDispatcher())

            val bright = practice.savePreset("t1", "Bright", settings, curve) as SavePresetResult.Saved
            practice.savePreset("t1", "Plain", settings)

            assertEquals(curve, bright.preset.eq)
            val presets = practice.observePresets("t1").first().associateBy { it.name }
            assertEquals(curve, presets.getValue("Bright").eq)
            assertNull(presets.getValue("Plain").eq)
        } finally {
            database.close()
        }
    }
}
