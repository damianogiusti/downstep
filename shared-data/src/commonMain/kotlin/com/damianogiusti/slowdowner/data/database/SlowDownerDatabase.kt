package com.damianogiusti.slowdowner.data.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.damianogiusti.slowdowner.data.backup.BackupDao
import com.damianogiusti.slowdowner.data.library.TrackDao
import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistDao
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistTrackEntity
import com.damianogiusti.slowdowner.data.practice.PracticeDao
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity
import com.damianogiusti.slowdowner.data.stems.StemsDao
import com.damianogiusti.slowdowner.data.stems.TrackStemEntity
import com.damianogiusti.slowdowner.data.stems.TrackStemStateEntity

/**
 * Single app database. Every library table is in version 1 on purpose: the first launch moves the
 * JSON library in here, so a later destructive migration would lose the user's library.
 * Version 2 only adds the two stem tables, so its automatic migration never touches user data.
 */
@Database(
    version = 2,
    exportSchema = true,
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PracticeStateEntity::class,
        PracticePresetEntity::class,
        TrackStemStateEntity::class,
        TrackStemEntity::class,
    ],
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ConstructedBy(SlowDownerDatabaseConstructor::class)
abstract class SlowDownerDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun practiceDao(): PracticeDao
    abstract fun backupDao(): BackupDao
    abstract fun stemsDao(): StemsDao

    companion object {
        const val NAME = "slowdowner.db"
    }
}

// Room generates the actual implementations (KSP), one per target.
@Suppress("KotlinNoActualForExpect")
expect object SlowDownerDatabaseConstructor : RoomDatabaseConstructor<SlowDownerDatabase> {
    override fun initialize(): SlowDownerDatabase
}
