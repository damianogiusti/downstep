package com.damianogiusti.slowdowner.data.database

import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsColumns
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real Room + bundled SQLite stack the desktop app uses: the SQL rules must match Android. */
class DesktopDatabaseTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val database by lazy { desktopDatabase(folder.root.resolve("test.db")) }

    @After
    fun tearDown() = database.close()

    @Test
    fun `when a preset name is taken then the insert is a constraint violation`() = runTest {
        val dao = database.practiceDao()
        database.trackDao().upsert(track("t1"))
        dao.insertPreset(preset("t1", "Slow"))
        try {
            dao.insertPreset(preset("t1", "Slow"))
            fail("duplicate preset inserted")
        } catch (e: Exception) {
            assertTrue("not recognised: $e", isConstraintViolation(e))
        }
    }

    @Test
    fun `when a track is deleted then its presets cascade`() = runTest {
        database.trackDao().upsert(track("t1"))
        database.practiceDao().insertPreset(preset("t1", "Slow"))
        database.trackDao().delete("t1")
        assertEquals(emptyList<PracticePresetEntity>(), database.practiceDao().observePresets("t1").first())
    }

    @Test
    fun `when a transaction fails then nothing is written`() = runTest {
        val transactions = RoomTransactionRunner(database)
        try {
            transactions.inTransaction {
                database.trackDao().upsert(track("t1"))
                error("boom")
            }
        } catch (e: IllegalStateException) {
            // expected
        }
        assertNull(database.trackDao().find("t1"))
    }

    private fun track(id: String) = TrackEntity(id, "Song", "/tmp/$id.m4a", null, addedAt = 1)

    private fun preset(trackId: String, name: String) = PracticePresetEntity(
        trackId = trackId,
        name = name,
        settings = PracticeSettingsColumns(75f, 0f, 0f, 1f, isLoopOn = false),
        createdAt = 1,
    )
}
