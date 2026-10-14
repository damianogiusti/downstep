package com.damianogiusti.slowdowner.data.playlists

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.damianogiusti.slowdowner.data.library.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, " +
            "(SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS track_count " +
            "FROM playlists p ORDER BY p.updated_at DESC",
    )
    fun observePlaylists(): Flow<List<PlaylistSummaryRow>>

    @Query(
        "SELECT p.id, p.name, " +
            "(SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) AS track_count " +
            "FROM playlists p WHERE p.id = :playlistId",
    )
    fun observePlaylist(playlistId: Long): Flow<PlaylistSummaryRow?>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON pt.track_id = t.id " +
            "WHERE pt.playlist_id = :playlistId ORDER BY pt.position",
    )
    fun observeTracksOf(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT playlist_id FROM playlist_tracks WHERE track_id = :trackId")
    fun observePlaylistIdsContaining(trackId: String): Flow<List<Long>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updated_at = :now WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun delete(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(link: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: String)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun setPosition(playlistId: Long, trackId: String, position: Int)

    @Query("UPDATE playlists SET updated_at = :now WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, now: Long)

    @Query("SELECT EXISTS (SELECT 1 FROM playlists WHERE id = :playlistId)")
    suspend fun playlistExists(playlistId: Long): Boolean

    @Query("SELECT EXISTS (SELECT 1 FROM tracks WHERE id = :trackId)")
    suspend fun trackExists(trackId: String): Boolean

    /**
     * Appends at the end; a track already in the playlist keeps its place. Returns false if
     * the playlist or the track is gone: IGNORE does not cover foreign key failures.
     */
    @Transaction
    suspend fun appendTrack(playlistId: Long, trackId: String, now: Long): Boolean {
        if (!playlistExists(playlistId) || !trackExists(trackId)) return false
        insertTrack(PlaylistTrackEntity(playlistId, trackId, maxPosition(playlistId) + 1))
        touch(playlistId, now)
        return true
    }

    /** Rewrites positions 0..n in the given order. */
    @Transaction
    suspend fun reorder(playlistId: Long, trackIds: List<String>, now: Long) {
        trackIds.forEachIndexed { index, trackId -> setPosition(playlistId, trackId, index) }
        touch(playlistId, now)
    }
}
