package com.damianogiusti.slowdowner.data.practice

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeDao {
    @Query("SELECT * FROM track_practice_state WHERE track_id = :trackId")
    suspend fun findState(trackId: String): PracticeStateEntity?

    @Query("SELECT * FROM track_practice_state")
    fun observeStates(): Flow<List<PracticeStateEntity>>

    /**
     * Saves only while the track exists. The auto-save is debounced, so it can land just
     * after the track was deleted; a plain insert would then fail on the foreign key.
     */
    suspend fun upsertStateIfTrackExists(trackId: String, settings: PracticeSettingsColumns, now: Long) =
        insertOrReplaceStateWhereTrackExists(
            trackId = trackId,
            speedPercent = settings.speedPercent,
            pitchSemitones = settings.pitchSemitones,
            loopStart = settings.loopStart,
            loopEnd = settings.loopEnd,
            isLoopOn = settings.isLoopOn,
            now = now,
        )

    // Room binds query arguments one by one; use upsertStateIfTrackExists instead.
    @Query(
        "INSERT OR REPLACE INTO track_practice_state " +
            "(track_id, speed_percent, pitch_semitones, loop_start, loop_end, is_loop_on, updated_at) " +
            "SELECT :trackId, :speedPercent, :pitchSemitones, :loopStart, :loopEnd, :isLoopOn, :now " +
            "WHERE EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)",
    )
    suspend fun insertOrReplaceStateWhereTrackExists(
        trackId: String,
        speedPercent: Float,
        pitchSemitones: Float,
        loopStart: Float,
        loopEnd: Float,
        isLoopOn: Boolean,
        now: Long,
    )

    @Query("SELECT * FROM practice_presets WHERE track_id = :trackId ORDER BY speed_percent, created_at")
    fun observePresets(trackId: String): Flow<List<PracticePresetEntity>>

    @Query("SELECT EXISTS (SELECT 1 FROM practice_presets WHERE track_id = :trackId AND name = :name)")
    suspend fun presetNameExists(trackId: String, name: String): Boolean

    /** Throws on a duplicate name for the same track (unique index). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPreset(preset: PracticePresetEntity): Long

    @Query("DELETE FROM practice_presets WHERE id = :presetId")
    suspend fun deletePreset(presetId: Long)
}
