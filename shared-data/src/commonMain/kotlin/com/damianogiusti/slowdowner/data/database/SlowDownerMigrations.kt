package com.damianogiusti.slowdowner.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Hand-written migrations, one per version step. The CREATE statements are copied from the exported
 * schema of the target version (shared-data/schemas), so the migrated database is the one Room
 * validates against.
 */
object SlowDownerMigrations {

    /**
     * 2: named loops. A track's single A/B region becomes "Loop 1", active, so nothing the user set
     * up is lost; the region columns stay as the mirror of the active loop the engine plays.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `practice_loops` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`track_id` TEXT NOT NULL, `name` TEXT NOT NULL, `loop_start` REAL NOT NULL, " +
                    "`loop_end` REAL NOT NULL, `passes` INTEGER NOT NULL, `notes` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, `progression_enabled` INTEGER NOT NULL, " +
                    "`progression_from_percent` REAL NOT NULL, `progression_to_percent` REAL NOT NULL, " +
                    "`progression_step_percent` REAL NOT NULL, `progression_every_passes` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`track_id`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_practice_loops_track_id` ON `practice_loops` (`track_id`)",
            )
            connection.execSQL("ALTER TABLE `track_practice_state` ADD COLUMN `active_loop_id` INTEGER")
            connection.execSQL("ALTER TABLE `track_practice_state` ADD COLUMN `bpm` REAL")
            connection.execSQL(
                "ALTER TABLE `track_practice_state` ADD COLUMN `snap_to_beat` INTEGER NOT NULL DEFAULT 1",
            )
            // A region is worth keeping when it is not the whole file. Progression gets the design
            // defaults (70% → 100%, +3% every 2 passes), off.
            connection.execSQL(
                "INSERT INTO `practice_loops` (`track_id`, `name`, `loop_start`, `loop_end`, `passes`, `notes`, " +
                    "`progression_enabled`, `progression_from_percent`, `progression_to_percent`, " +
                    "`progression_step_percent`, `progression_every_passes`, `created_at`) " +
                    "SELECT `track_id`, 'Loop 1', `loop_start`, `loop_end`, 0, '', 0, 70.0, 100.0, 3.0, 2, `updated_at` " +
                    "FROM `track_practice_state` WHERE $HAS_REGION",
            )
            // One loop per track so far: the track's only loop is its active one.
            connection.execSQL(
                "UPDATE `track_practice_state` SET `active_loop_id` = " +
                    "(SELECT `id` FROM `practice_loops` WHERE `practice_loops`.`track_id` = `track_practice_state`.`track_id`) " +
                    "WHERE $HAS_REGION",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)

    /** In SQL what [hasRegion] says in Kotlin: keep the two in step. */
    private const val HAS_REGION = "`loop_end` > `loop_start` AND (`loop_start` > 0 OR `loop_end` < 1)"

    /**
     * A region worth a loop of its own: not the whole file. The 1→2 migration turns such a region into
     * "Loop 1", and so does the restore of a backup from before loops.
     */
    fun hasRegion(start: Float, end: Float): Boolean = end > start && (start > 0f || end < 1f)
}
