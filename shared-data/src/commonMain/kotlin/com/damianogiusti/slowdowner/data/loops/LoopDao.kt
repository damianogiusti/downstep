package com.damianogiusti.slowdowner.data.loops

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsColumns
import kotlinx.coroutines.flow.Flow

@Dao
interface LoopDao {
    @Query("SELECT * FROM practice_loops WHERE track_id = :trackId ORDER BY loop_start, id")
    fun observeLoops(trackId: String): Flow<List<LoopEntity>>

    @Query("SELECT * FROM practice_loops WHERE track_id = :trackId ORDER BY loop_start, id")
    suspend fun loopsOf(trackId: String): List<LoopEntity>

    @Query("SELECT * FROM practice_loops WHERE id = :loopId")
    suspend fun findLoop(loopId: Long): LoopEntity?

    /** Throws on a missing track (foreign key). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLoop(loop: LoopEntity): Long

    @Query("UPDATE practice_loops SET name = :name WHERE id = :loopId")
    suspend fun rename(loopId: Long, name: String)

    @Query("UPDATE practice_loops SET notes = :notes WHERE id = :loopId")
    suspend fun updateNotes(loopId: Long, notes: String)

    @Query("UPDATE practice_loops SET loop_start = :start, loop_end = :end WHERE id = :loopId")
    suspend fun updateBounds(loopId: Long, start: Float, end: Float)

    // One statement: a read-modify-write could lose a pass to a quick second wrap.
    @Query("UPDATE practice_loops SET passes = passes + 1 WHERE id = :loopId")
    suspend fun addPass(loopId: Long)

    @Query("UPDATE practice_loops SET passes = 0 WHERE id = :loopId")
    suspend fun resetPasses(loopId: Long)

    suspend fun updateProgression(loopId: Long, progression: ProgressionColumns) = updateProgressionColumns(
        loopId = loopId,
        enabled = progression.enabled,
        fromPercent = progression.fromPercent,
        toPercent = progression.toPercent,
        stepPercent = progression.stepPercent,
        everyPasses = progression.everyPasses,
    )

    // Room binds query arguments one by one; use updateProgression instead.
    @Query(
        "UPDATE practice_loops SET progression_enabled = :enabled, progression_from_percent = :fromPercent, " +
            "progression_to_percent = :toPercent, progression_step_percent = :stepPercent, " +
            "progression_every_passes = :everyPasses WHERE id = :loopId",
    )
    suspend fun updateProgressionColumns(
        loopId: Long,
        enabled: Boolean,
        fromPercent: Float,
        toPercent: Float,
        stepPercent: Float,
        everyPasses: Int,
    )

    @Query("DELETE FROM practice_loops WHERE id = :loopId")
    suspend fun deleteLoop(loopId: Long)

    @Query(
        "SELECT active_loop_id, bpm, detected_bpm, beat_offset_seconds, tempo_analysis_version " +
            "FROM track_practice_state WHERE track_id = :trackId",
    )
    fun observeSettings(trackId: String): Flow<TrackLoopSettingsRow?>

    @Query(
        "SELECT active_loop_id, bpm, detected_bpm, beat_offset_seconds, tempo_analysis_version " +
            "FROM track_practice_state WHERE track_id = :trackId",
    )
    suspend fun findSettings(trackId: String): TrackLoopSettingsRow?

    /**
     * The loop settings live in the practice state row, which only the auto-save creates: a tempo
     * set before any save needs a row with the [defaults] first. Does nothing if the row or the
     * track is missing.
     */
    suspend fun ensureStateRow(trackId: String, defaults: PracticeSettingsColumns, now: Long) =
        insertStateIfAbsent(
            trackId = trackId,
            speedPercent = defaults.speedPercent,
            pitchSemitones = defaults.pitchSemitones,
            loopStart = defaults.loopStart,
            loopEnd = defaults.loopEnd,
            isLoopOn = defaults.isLoopOn,
            now = now,
        )

    // Room binds query arguments one by one; use ensureStateRow instead.
    @Query(
        "INSERT OR IGNORE INTO track_practice_state " +
            "(track_id, speed_percent, pitch_semitones, loop_start, loop_end, is_loop_on, updated_at) " +
            "SELECT :trackId, :speedPercent, :pitchSemitones, :loopStart, :loopEnd, :isLoopOn, :now " +
            "WHERE EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)",
    )
    suspend fun insertStateIfAbsent(
        trackId: String,
        speedPercent: Float,
        pitchSemitones: Float,
        loopStart: Float,
        loopEnd: Float,
        isLoopOn: Boolean,
        now: Long,
    )

    @Query("UPDATE track_practice_state SET active_loop_id = :loopId WHERE track_id = :trackId")
    suspend fun setActiveLoop(trackId: String, loopId: Long?)

    @Query("UPDATE track_practice_state SET bpm = :bpm WHERE track_id = :trackId")
    suspend fun setBpm(trackId: String, bpm: Float?)

    /**
     * The result of a tempo analysis: [detectedBpm] and [beatOffsetSeconds] null when nothing was
     * found; [analysisVersion] says which analysis ran, so it is not repeated. Needs the state row
     * ([ensureStateRow]).
     */
    @Query(
        "UPDATE track_practice_state SET detected_bpm = :detectedBpm, beat_offset_seconds = :beatOffsetSeconds, " +
            "tempo_analysis_version = :analysisVersion WHERE track_id = :trackId",
    )
    suspend fun setDetectedTempo(trackId: String, detectedBpm: Float?, beatOffsetSeconds: Float?, analysisVersion: Int)

    /** Moves the first beat (for example after tap tempo); null when unknown. Needs the state row. */
    @Query("UPDATE track_practice_state SET beat_offset_seconds = :offset WHERE track_id = :trackId")
    suspend fun setBeatOffset(trackId: String, offset: Float?)
}
