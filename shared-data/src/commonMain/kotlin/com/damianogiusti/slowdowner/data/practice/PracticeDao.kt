package com.damianogiusti.slowdowner.data.practice

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
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

    // Room binds query arguments one by one; use upsertStateIfTrackExists instead. REPLACE rewrites
    // the whole row: every other column (loops, tempo, the track's EQ) is copied over from the old
    // one, they are not ours to reset. A new column on this table must be added here too.
    // (No ON CONFLICT DO UPDATE: Android's SQLite has it only from API 30.)
    @Query(
        "INSERT OR REPLACE INTO track_practice_state " +
            "(track_id, speed_percent, pitch_semitones, loop_start, loop_end, is_loop_on, updated_at, " +
            "active_loop_id, bpm, eq_curve, detected_bpm, beat_offset_seconds, tempo_analysis_version) " +
            "SELECT :trackId, :speedPercent, :pitchSemitones, :loopStart, :loopEnd, :isLoopOn, :now, " +
            "previous.active_loop_id, previous.bpm, previous.eq_curve, previous.detected_bpm, " +
            "previous.beat_offset_seconds, COALESCE(previous.tempo_analysis_version, 0) " +
            "FROM (SELECT 1) LEFT JOIN track_practice_state previous ON previous.track_id = :trackId " +
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

    /** The track's own EQ curve (JSON), or null when it uses the global EQ or has no state row. */
    @Query("SELECT eq_curve FROM track_practice_state WHERE track_id = :trackId")
    suspend fun trackEqCurve(trackId: String): String?

    /**
     * Saves the track's own EQ curve; null goes back to the global EQ. The state row is created with
     * the default settings first when the track has none yet. Does nothing if the track is missing.
     */
    // Two statements, no transaction: both are idempotent, and the auto-save's REPLACE in between
    // keeps eq_curve.
    suspend fun setTrackEqCurve(trackId: String, json: String?, now: Long) {
        val defaults = PracticeSettingsMapper.toColumns(PracticeSettings())
        insertDefaultStateIfAbsent(
            trackId = trackId,
            speedPercent = defaults.speedPercent,
            pitchSemitones = defaults.pitchSemitones,
            loopStart = defaults.loopStart,
            loopEnd = defaults.loopEnd,
            isLoopOn = defaults.isLoopOn,
            now = now,
        )
        updateTrackEqCurve(trackId, json)
    }

    // Use setTrackEqCurve instead: a row that does not exist yet cannot be updated.
    @Query(
        "INSERT OR IGNORE INTO track_practice_state " +
            "(track_id, speed_percent, pitch_semitones, loop_start, loop_end, is_loop_on, updated_at) " +
            "SELECT :trackId, :speedPercent, :pitchSemitones, :loopStart, :loopEnd, :isLoopOn, :now " +
            "WHERE EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)",
    )
    suspend fun insertDefaultStateIfAbsent(
        trackId: String,
        speedPercent: Float,
        pitchSemitones: Float,
        loopStart: Float,
        loopEnd: Float,
        isLoopOn: Boolean,
        now: Long,
    )

    // Use setTrackEqCurve instead.
    @Query("UPDATE track_practice_state SET eq_curve = :json WHERE track_id = :trackId")
    suspend fun updateTrackEqCurve(trackId: String, json: String?)

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
