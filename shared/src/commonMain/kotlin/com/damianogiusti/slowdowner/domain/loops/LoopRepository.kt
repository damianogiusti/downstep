package com.damianogiusti.slowdowner.domain.loops

import kotlinx.coroutines.flow.Flow

/**
 * The named loops of each track, and the per-track settings that go with them (active loop,
 * tempo). Writes to a loop id that no longer exists do nothing.
 */
interface LoopRepository {
    fun observe(trackId: String): Flow<TrackLoops>
    suspend fun trackLoops(trackId: String): TrackLoops
    suspend fun loop(loopId: Long): Loop?
    /** Null when the track is gone (deleted while the user was editing). */
    suspend fun create(trackId: String, name: String, bounds: LoopBounds): Loop?
    suspend fun rename(loopId: Long, name: String)
    suspend fun updateNotes(loopId: Long, notes: String)
    suspend fun updateBounds(loopId: Long, bounds: LoopBounds)
    /** Adds one pass in a single write, so quick wraps cannot lose a count. Returns the updated loop. */
    suspend fun addPass(loopId: Long): Loop?
    suspend fun resetPasses(loopId: Long)
    suspend fun updateProgression(loopId: Long, progression: Progression)
    suspend fun delete(loopId: Long)
    suspend fun setActiveLoop(trackId: String, loopId: Long?)
    suspend fun setBpm(trackId: String, bpm: Float?)
}
