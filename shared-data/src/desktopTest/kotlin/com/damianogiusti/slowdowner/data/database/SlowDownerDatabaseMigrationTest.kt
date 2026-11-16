package com.damianogiusti.slowdowner.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.damianogiusti.slowdowner.data.stems.TrackStemEntity
import com.damianogiusti.slowdowner.data.stems.TrackStemStateEntity
import androidx.room.testing.MigrationTestHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Version 1 to 2 adds the stem tables: the library of a user who updates must come through untouched. */
class SlowDownerDatabaseMigrationTest {

    // The helper rule needs its path when it is created, before a TemporaryFolder rule would be ready.
    private val folder = Files.createTempDirectory("slowdowner-migration").toFile()
    private val databaseFile = folder.resolve("migration.db")

    // Gradle runs the tests in the module folder, where the exported schemas are.
    @get:Rule
    val helper = MigrationTestHelper(
        schemaDirectoryPath = Path.of("schemas"),
        databasePath = databaseFile.toPath(),
        driver = BundledSQLiteDriver(),
        databaseClass = SlowDownerDatabase::class,
        databaseFactory = { SlowDownerDatabaseConstructor.initialize() },
    )

    @After
    fun deleteFolder() {
        // Flat folder: the database and its SQLite side files.
        folder.listFiles()?.forEach { it.delete() }
        folder.delete()
    }

    @Test
    fun `when version 1 is migrated then the existing rows survive`() {
        helper.createDatabase(1).use { connection ->
            connection.execSQL("INSERT INTO tracks (id, title, file_path, source_url, added_at) VALUES ('t1', 'Song', '/a.m4a', NULL, 7)")
            connection.execSQL(
                "INSERT INTO track_practice_state (track_id, speed_percent, pitch_semitones, loop_start, loop_end, is_loop_on, updated_at) " +
                    "VALUES ('t1', 80.0, -1.0, 0.1, 0.2, 1, 9)",
            )
        }

        helper.runMigrationsAndValidate(2, emptyList()).use { connection ->
            assertEquals("Song", connection.text("SELECT title FROM tracks WHERE id = 't1'"))
            assertEquals("80.0", connection.text("SELECT speed_percent FROM track_practice_state WHERE track_id = 't1'"))
            assertEquals("0", connection.text("SELECT COUNT(*) FROM track_stem_state"))
            assertEquals("0", connection.text("SELECT COUNT(*) FROM track_stems"))
        }
    }

    @Test
    fun `when a migrated database is opened then the stem tables work and cascade from the track`() = runTest {
        helper.createDatabase(1).use { connection ->
            connection.execSQL("INSERT INTO tracks (id, title, file_path, source_url, added_at) VALUES ('t1', 'Song', '/a.m4a', NULL, 7)")
            connection.execSQL("INSERT INTO tracks (id, title, file_path, source_url, added_at) VALUES ('t2', 'Other', '/b.m4a', NULL, 8)")
        }
        helper.runMigrationsAndValidate(2, emptyList()).close()

        val database = desktopDatabase(databaseFile)
        try {
            val dao = database.stemsDao()
            for (id in listOf("t1", "t2")) {
                dao.replace(
                    state = TrackStemStateEntity(id, "model", "STEMS"),
                    stems = listOf(
                        TrackStemEntity(id, "VOCALS", "vocals.wav", 1f),
                        TrackStemEntity(id, "DRUMS", "drums.wav", 0.5f),
                    ),
                )
            }
            assertEquals(listOf("t1", "t2"), dao.observeSeparatedTrackIds().first().sorted())

            // Track -> state -> stems.
            database.trackDao().delete("t1")

            assertNull(dao.observe("t1").first())
            assertEquals(emptyList<Any>(), dao.findStems("t1"))
            assertEquals(2, dao.findStems("t2").size)
            assertNotNull(dao.observe("t2").first())

            // State -> stems, without the track going away.
            dao.delete("t2")
            assertEquals(emptyList<Any>(), dao.findStems("t2"))
            assertNotNull(database.trackDao().find("t2"))
        } finally {
            database.close()
        }
    }

    private fun SQLiteConnection.text(sql: String): String = prepare(sql).use { statement ->
        check(statement.step()) { "no row for $sql" }
        statement.getText(0)
    }
}
