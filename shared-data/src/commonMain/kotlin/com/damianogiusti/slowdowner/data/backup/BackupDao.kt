package com.damianogiusti.slowdowner.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistTrackEntity
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity

/**
 * Reads and merge-inserts across all tables for backups. Every method suspends, so it joins
 * the transaction opened by [com.damianogiusti.slowdowner.data.database.DatabaseTransactionRunner].
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun findTrack(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY added_at")
    suspend fun allTracks(): List<TrackEntity>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON pt.track_id = t.id " +
            "WHERE pt.playlist_id = :playlistId ORDER BY pt.position",
    )
    suspend fun tracksOf(playlistId: Long): List<TrackEntity>

    @Query("SELECT id FROM tracks")
    suspend fun allTrackIds(): List<String>

    @Query("SELECT EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)")
    suspend fun trackExists(trackId: String): Boolean

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun findPlaylist(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY created_at")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Query("SELECT name FROM playlists")
    suspend fun playlistNames(): List<String>

    /** The oldest one, if the user gave the same name to more than one. */
    @Query("SELECT * FROM playlists WHERE name = :name ORDER BY created_at, id LIMIT 1")
    suspend fun findPlaylistByName(name: String): PlaylistEntity?

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("UPDATE playlists SET updated_at = :updatedAt WHERE id = :playlistId")
    suspend fun setPlaylistUpdatedAt(playlistId: Long, updatedAt: Long)

    @Query("SELECT track_id FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun trackIdsOf(playlistId: Long): List<String>

    @Query("SELECT * FROM track_practice_state WHERE track_id = :trackId")
    suspend fun findState(trackId: String): PracticeStateEntity?

    @Query("SELECT * FROM practice_presets WHERE track_id = :trackId ORDER BY created_at")
    suspend fun presetsOf(trackId: String): List<PracticePresetEntity>

    /** Returns -1 when the id exists: the existing row wins. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackIfAbsent(track: TrackEntity): Long

    /** Keeps the existing state: the primary key is the track id. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStateIfAbsent(state: PracticeStateEntity): Long

    /** Keeps the existing preset with the same name: the (track, name) index is unique. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPresetIfNameFree(preset: PracticePresetEntity): Long

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrack(link: PlaylistTrackEntity): Long
}
