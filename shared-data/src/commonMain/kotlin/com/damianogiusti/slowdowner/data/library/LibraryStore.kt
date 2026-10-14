package com.damianogiusti.slowdowner.data.library

import kotlinx.coroutines.flow.Flow
import java.io.File

/** Local-only library: audio files plus their index in Room. File work blocks: callers pick the thread. */
interface LibraryStore {
    fun observe(): Flow<List<LibraryTrackDto>>
    suspend fun find(id: String): LibraryTrackDto?
    suspend fun add(track: LibraryTrackDto)
    suspend fun remove(id: String)
    fun fileFor(id: String, extension: String): File
}
