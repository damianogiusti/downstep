package com.damianogiusti.slowdowner.data.stems

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Suspend: Room allows only suspend DAO calls outside Android. */
@Dao
interface StemsDao {
    @Transaction
    @Query("SELECT * FROM track_stem_state WHERE track_id = :trackId")
    fun observe(trackId: String): Flow<TrackStemsRelation?>

    @Query("SELECT track_id FROM track_stem_state")
    fun observeSeparatedTrackIds(): Flow<List<String>>

    @Query("SELECT * FROM track_stems WHERE track_id = :trackId")
    suspend fun findStems(trackId: String): List<TrackStemEntity>

    /** Does nothing when the track has no state row. */
    @Query("UPDATE track_stem_state SET mode = :mode WHERE track_id = :trackId")
    suspend fun updateMode(trackId: String, mode: String)

    /** Does nothing when the track does not have the stem. */
    @Query("UPDATE track_stems SET gain = :gain WHERE track_id = :trackId AND stem = :stem")
    suspend fun updateGain(trackId: String, stem: String, gain: Float)

    /** Does nothing when the track does not have the stem. */
    @Query("UPDATE track_stems SET is_muted = :isMuted WHERE track_id = :trackId AND stem = :stem")
    suspend fun updateMuted(trackId: String, stem: String, isMuted: Boolean)

    /** Does nothing when the track does not have the stem. */
    @Query("UPDATE track_stems SET is_soloed = :isSoloed WHERE track_id = :trackId AND stem = :stem")
    suspend fun updateSoloed(trackId: String, stem: String, isSoloed: Boolean)

    /** The stems go with it (cascade). */
    @Query("DELETE FROM track_stem_state WHERE track_id = :trackId")
    suspend fun delete(trackId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertState(state: TrackStemStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStems(stems: List<TrackStemEntity>)

    /**
     * Saves a whole separation, replacing an earlier one of the same track. Fails on the foreign
     * key when the track is not in the library; the rows are all in or all out.
     */
    @Transaction
    suspend fun replace(state: TrackStemStateEntity, stems: List<TrackStemEntity>) {
        delete(state.trackId)
        insertState(state)
        insertStems(stems)
    }
}
