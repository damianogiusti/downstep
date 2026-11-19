package com.damianogiusti.slowdowner.data.stems

import kotlinx.coroutines.flow.Flow
import java.io.File

/** Local-only stems: their rows in Room plus the files in the stems folder. File work blocks: callers pick the thread. */
interface StemsStore {
    fun observe(trackId: String): Flow<TrackStemsDto?>
    fun observeSeparatedTrackIds(): Flow<List<String>>
    suspend fun setMode(trackId: String, mode: String)
    suspend fun setGain(trackId: String, stem: String, gain: Float)

    /** Saves the state and the stems of a track together, replacing an earlier separation. */
    suspend fun add(stems: TrackStemsDto)

    /** Removes the rows, then the files. */
    suspend fun delete(trackId: String)

    /** The folder of a track's stem files, created if missing. Throws if [trackId] could leave the stems folder. */
    fun dirFor(trackId: String): File
}
