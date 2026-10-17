package com.damianogiusti.slowdowner.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.damianogiusti.slowdowner.data.loops.DefaultLoopRepository
import com.damianogiusti.slowdowner.domain.loops.Progression
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds real version 1 databases from the exported schema (shared-data/schemas, the Gradle test
 * working directory is the module) and migrates them like an app update would.
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
        // androidx.sqlite.SQLITE_DATA_INTEGER / SQLITE_DATA_FLOAT
        const val COLUMN_TYPE_INTEGER = 1
        const val COLUMN_TYPE_FLOAT = 2
    }
}
