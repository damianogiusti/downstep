package com.damianogiusti.slowdowner.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import java.io.File

/** The desktop database file, with SQLite bundled in the app (same schema as Android). */
fun desktopDatabase(file: File): SlowDownerDatabase =
    Room.databaseBuilder<SlowDownerDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
