package com.damianogiusti.slowdowner.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.damianogiusti.slowdowner.data.loops.DefaultLoopRepository
import com.damianogiusti.slowdowner.data.practice.DefaultPracticeRepository
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds real version 1 and 2 databases from the exported schemas (shared-data/schemas, the Gradle
 * test working directory is the module) and migrates them like an app update would.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlowDownerMigrationsTest {

    private val directory = Files.createTempDirectory("slowdowner-migration")
    private val file = directory.resolve("migration.db")

    @get:Rule
    val helper = MigrationTestHelper(
        schemaDirectoryPath = Path.of("schemas"),
        databasePath = file,
        driver = BundledSQLiteDriver(),
        databaseClass = SlowDownerDatabase::class,
        databaseFactory = { SlowDownerDatabaseConstructor.initialize() },
    )

    @After
    fun tearDown() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `when a track has a loop region then it becomes its active Loop 1`() {
        helper.createDatabase(1).use { connection ->
            connection.insertTrack("looped")
            connection.insertState("looped", loopStart = 0.25f, loopEnd = 0.5f, isLoopOn = true, updatedAt = 77)
        }

        helper.runMigrationsAndValidate(2, SlowDownerMigrations.ALL.toList()).use { connection ->
            val loop = connection.query(
                "SELECT id, track_id, name, loop_start, loop_end, passes, notes, progression_enabled, " +
                    "progression_from_percent, progression_to_percent, progression_step_percent, " +
                    "progression_every_passes, created_at FROM practice_loops",
            ).single()
            assertEquals(
                listOf("looped", "Loop 1", 0.25, 0.5, 0L, "", 0L, 70.0, 100.0, 3.0, 2L, 77L),
                loop.drop(1),
            )
            val state = connection.query(
                "SELECT active_loop_id, bpm, snap_to_beat, loop_start, loop_end, is_loop_on FROM track_practice_state",
            ).single()
            // The region columns stay: they are what the engine plays, now a mirror of the loop.
            assertEquals(listOf(loop[0], null, 1L, 0.25, 0.5, 1L), state)
        }
    }

    @Test
    fun `when a track plays the whole file then it gets no loop`() {
        helper.createDatabase(1).use { connection ->
            connection.insertTrack("plain")
            connection.insertState("plain", loopStart = 0f, loopEnd = 1f, isLoopOn = false, updatedAt = 1)
            connection.insertTrack("broken")
            connection.insertState("broken", loopStart = 0.6f, loopEnd = 0.4f, isLoopOn = true, updatedAt = 1)
        }

        helper.runMigrationsAndValidate(2, SlowDownerMigrations.ALL.toList()).use { connection ->
            assertEquals(emptyList<List<Any?>>(), connection.query("SELECT id FROM practice_loops"))
            assertEquals(
                listOf(listOf(null, 1L), listOf(null, 1L)),
                connection.query("SELECT active_loop_id, snap_to_beat FROM track_practice_state"),
            )
        }
    }

    @Test
    fun `when migrated then presets and playlists are untouched`() {
        helper.createDatabase(1).use { connection ->
            connection.insertTrack("t")
            connection.execSQL(
                "INSERT INTO practice_presets (track_id, name, created_at, speed_percent, pitch_semitones, " +
                    "loop_start, loop_end, is_loop_on) VALUES ('t', 'Slow', 1, 70, 0, 0, 1, 0)",
            )
            connection.execSQL("INSERT INTO playlists (name, created_at, updated_at) VALUES ('Gig', 1, 1)")
            connection.execSQL("INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (1, 't', 0)")
        }

        helper.runMigrationsAndValidate(2, SlowDownerMigrations.ALL.toList()).use { connection ->
            assertEquals(listOf(listOf("Slow", 70.0)), connection.query("SELECT name, speed_percent FROM practice_presets"))
            assertEquals(listOf(listOf("t")), connection.query("SELECT track_id FROM playlist_tracks"))
        }
    }

    @Test
    fun `when the migrated database is opened by the app then the loop is there and active`() = runTest {
        helper.createDatabase(1).use { connection ->
            connection.insertTrack("looped")
            connection.insertState("looped", loopStart = 0.1f, loopEnd = 0.2f, isLoopOn = true, updatedAt = 5)
        }

        val database = desktopDatabase(file.toFile())
        try {
            val loops = DefaultLoopRepository(database.loopDao(), UnconfinedTestDispatcher()).trackLoops("looped")

            val loop = loops.activeLoop!!
            assertEquals("Loop 1", loop.name)
            assertEquals(0.1f, loop.start)
            assertEquals(Progression.DEFAULT, loop.progression)
            assertNull(loops.bpm)
        } finally {
            database.close()
        }
    }

    @Test
    fun `when migrated to 3 then Snap to beat is gone and the tempo, loops and settings survive`() {
        helper.createDatabase(2).use { connection ->
            connection.insertTrack("t")
            connection.execSQL(
                "INSERT INTO practice_loops (id, track_id, name, loop_start, loop_end, passes, notes, created_at, " +
                    "progression_enabled, progression_from_percent, progression_to_percent, progression_step_percent, " +
                    "progression_every_passes) VALUES (7, 't', 'Solo', 0.3, 0.4, 5, 'Am', 2, 1, 60, 90, 5, 3)",
            )
            connection.execSQL(
                "INSERT INTO track_practice_state (track_id, updated_at, active_loop_id, bpm, snap_to_beat, " +
                    "speed_percent, pitch_semitones, loop_start, loop_end, is_loop_on) " +
                    "VALUES ('t', 9, 7, 92.5, 0, 80, -2, 0.3, 0.4, 1)",
            )
            connection.insertTrack("u")
            connection.insertState("u", loopStart = 0f, loopEnd = 1f, isLoopOn = false, updatedAt = 3)
            connection.execSQL(
                "INSERT INTO practice_presets (track_id, name, created_at, speed_percent, pitch_semitones, " +
                    "loop_start, loop_end, is_loop_on) VALUES ('t', 'Slow', 1, 70, 0, 0, 1, 0)",
            )
        }

        helper.runMigrationsAndValidate(3, SlowDownerMigrations.ALL.toList()).use { connection ->
            val columns = connection.query("PRAGMA table_info(track_practice_state)").map { it[1] }
            assertFalse("snap_to_beat" in columns)
            assertTrue(columns.containsAll(listOf("eq_curve", "detected_bpm", "beat_offset_seconds", "tempo_analysis_version")))
            assertEquals(
                listOf(
                    listOf("t", 9L, 7L, 92.5, 80.0, -2.0, 0.3, 0.4, 1L, null, null, null, 0L),
                    listOf("u", 3L, null, null, 100.0, 0.0, 0.0, 1.0, 0L, null, null, null, 0L),
                ),
                connection.query(
                    "SELECT track_id, updated_at, active_loop_id, bpm, speed_percent, pitch_semitones, loop_start, " +
                        "loop_end, is_loop_on, eq_curve, detected_bpm, beat_offset_seconds, tempo_analysis_version " +
                        "FROM track_practice_state ORDER BY track_id",
                ),
            )
            assertEquals(
                listOf(listOf(7L, "t", "Solo", 0.3, 0.4, 5L, "Am")),
                connection.query("SELECT id, track_id, name, loop_start, loop_end, passes, notes FROM practice_loops"),
            )
            assertEquals(listOf(listOf("Slow", 70.0, null)), connection.query("SELECT name, speed_percent, eq_curve FROM practice_presets"))
        }
    }

    @Test
    fun `when migrated to 3 then a row added without the new columns gets their defaults`() {
        helper.createDatabase(2).close()

        helper.runMigrationsAndValidate(3, SlowDownerMigrations.ALL.toList()).use { connection ->
            connection.insertTrack("t")
            connection.insertState("t", loopStart = 0f, loopEnd = 1f, isLoopOn = false, updatedAt = 1)
            assertEquals(
                listOf(listOf(null, null, null, 0L)),
                connection.query("SELECT eq_curve, detected_bpm, beat_offset_seconds, tempo_analysis_version FROM track_practice_state"),
            )
        }
    }

    @Test
    fun `when a version 1 database is migrated to 3 then the region still becomes the active loop`() {
        helper.createDatabase(1).use { connection ->
            connection.insertTrack("looped")
            connection.insertState("looped", loopStart = 0.25f, loopEnd = 0.5f, isLoopOn = true, updatedAt = 77)
        }

        helper.runMigrationsAndValidate(3, SlowDownerMigrations.ALL.toList()).use { connection ->
            val loopId = connection.query("SELECT id FROM practice_loops WHERE name = 'Loop 1'").single().single()
            assertEquals(
                listOf(listOf(loopId, null, 0.25, 0.5, 0L)),
                connection.query("SELECT active_loop_id, bpm, loop_start, loop_end, tempo_analysis_version FROM track_practice_state"),
            )
        }
    }

    @Test
    fun `when a migrated database is auto-saved then the track EQ and the detected tempo are kept`() = runTest {
        helper.createDatabase(2).use { connection ->
            connection.insertTrack("t")
            connection.execSQL(
                "INSERT INTO track_practice_state (track_id, updated_at, active_loop_id, bpm, snap_to_beat, " +
                    "speed_percent, pitch_semitones, loop_start, loop_end, is_loop_on) " +
                    "VALUES ('t', 9, NULL, 92, 1, 80, 0, 0, 1, 0)",
            )
        }

        val database = desktopDatabase(file.toFile())
        try {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            database.practiceDao().setTrackEqCurve("t", EQ_JSON, now = 10)
            database.loopDao().setDetectedTempo("t", detectedBpm = 128f, beatOffsetSeconds = 0.5f, analysisVersion = 1)

            // The auto-save rewrites the whole row (INSERT OR REPLACE).
            DefaultPracticeRepository(database.practiceDao(), dispatcher, now = { 11 })
                .saveLastState("t", PracticeSettings(70f, 0f, LoopRegion(0.1f, 0.2f, isOn = true)))

            assertEquals(EQ_JSON, database.practiceDao().trackEqCurve("t"))
            val loops = DefaultLoopRepository(database.loopDao(), dispatcher).trackLoops("t")
            assertEquals(92f, loops.bpm)
            assertEquals(128f, loops.detectedBpm)
            assertEquals(0.5f, loops.beatOffsetSeconds)
            assertEquals(1, loops.tempoAnalysisVersion)
            assertEquals(70f, database.practiceDao().findState("t")!!.settings.speedPercent)
        } finally {
            database.close()
        }
    }

    private fun SQLiteConnection.insertTrack(id: String) =
        execSQL("INSERT INTO tracks (id, title, file_path, source_url, added_at) VALUES ('$id', 'Song', '/$id.m4a', NULL, 1)")

    private fun SQLiteConnection.insertState(trackId: String, loopStart: Float, loopEnd: Float, isLoopOn: Boolean, updatedAt: Long) =
        execSQL(
            "INSERT INTO track_practice_state (track_id, updated_at, speed_percent, pitch_semitones, loop_start, " +
                "loop_end, is_loop_on) VALUES ('$trackId', $updatedAt, 100, 0, $loopStart, $loopEnd, ${if (isLoopOn) 1 else 0})",
        )

    /** Every row as a list of column values: Long, Double, String or null. */
    private fun SQLiteConnection.query(sql: String): List<List<Any?>> = prepare(sql).use { statement ->
        buildList {
            while (statement.step()) {
                add(
                    (0 until statement.getColumnCount()).map { column ->
                        when {
                            statement.isNull(column) -> null
                            statement.getColumnType(column) == COLUMN_TYPE_INTEGER -> statement.getLong(column)
                            statement.getColumnType(column) == COLUMN_TYPE_FLOAT -> statement.getDouble(column)
                            else -> statement.getText(column)
                        }
                    },
                )
            }
        }
    }

    private companion object {
        const val EQ_JSON = """{"enabled":true,"preampDb":0.0,"bands":[]}"""

        // androidx.sqlite.SQLITE_DATA_INTEGER / SQLITE_DATA_FLOAT
        const val COLUMN_TYPE_INTEGER = 1
        const val COLUMN_TYPE_FLOAT = 2
    }
}
