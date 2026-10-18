package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.tempo.TempoEstimate
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
    /**
     * The tempo the user typed or tapped; null: back to the detected one. A new one clears the beat
     * offset, which belonged to the old tempo: until its beats are found (DetectTrackTempoUseCase.alignBeats),
     * the track says so with a null offset, also across a restart.
     */
    suspend fun setBpm(trackId: String, bpm: Float?)
    /** The tempo analysis's result ([estimate] null: nothing found) and which analysis ran. */
    suspend fun setDetectedTempo(trackId: String, estimate: TempoEstimate?, analysisVersion: Int)
    /** Where the first beat of the effective tempo falls, in seconds; null when unknown. */
    suspend fun setBeatOffset(trackId: String, offsetSeconds: Float?)
}
