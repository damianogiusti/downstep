package com.damianogiusti.slowdowner.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.damianogiusti.slowdowner.data.backup.BackupDao
import com.damianogiusti.slowdowner.data.library.TrackDao
import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.loops.LoopDao
import com.damianogiusti.slowdowner.data.loops.LoopEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistDao
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistTrackEntity
import com.damianogiusti.slowdowner.data.practice.PracticeDao
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity

/**
 * Single app database. The first launch moves the JSON library in here, so a destructive
 * migration would lose the user's library: every version change has a Migration in
 * [SlowDownerMigrations], tested against the exported schemas.
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
        LoopEntity::class,
    ],
)
@ConstructedBy(SlowDownerDatabaseConstructor::class)
abstract class SlowDownerDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun practiceDao(): PracticeDao
    abstract fun backupDao(): BackupDao
    abstract fun loopDao(): LoopDao

    companion object {
        const val NAME = "slowdowner.db"
    }
}

// Room generates the actual implementations (KSP), one per target.
@Suppress("KotlinNoActualForExpect")
expect object SlowDownerDatabaseConstructor : RoomDatabaseConstructor<SlowDownerDatabase> {
    override fun initialize(): SlowDownerDatabase
}
