package com.damianogiusti.slowdowner.data.playlists

import com.damianogiusti.slowdowner.data.library.LibraryTrackMapper
import com.damianogiusti.slowdowner.data.library.TrackEntityMapper
import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.domain.playlists.PlaylistRepository
import com.damianogiusti.slowdowner.domain.playlists.PlaylistWithTracks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class DefaultPlaylistRepository(
    private val dao: PlaylistDao,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val now: () -> Long = System::currentTimeMillis,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        dao.observePlaylists().map { rows -> rows.map(PlaylistMapper::toDomain) }.flowOn(dispatcher)

    override fun observePlaylist(playlistId: Long): Flow<PlaylistWithTracks?> =
        combine(dao.observePlaylist(playlistId), dao.observeTracksOf(playlistId)) { row, tracks ->
            row?.let {
                PlaylistWithTracks(
                    playlist = PlaylistMapper.toDomain(it),
                    // Same rule as the library: a track whose file is gone is not playable.
                    tracks = tracks
                        .filter { track -> File(track.filePath).exists() }
                        .map { track -> LibraryTrackMapper.toDomain(TrackEntityMapper.toDto(track)) },
                )
            }
        }.flowOn(dispatcher)

    override fun observePlaylistIdsContaining(trackId: String): Flow<Set<Long>> =
        dao.observePlaylistIdsContaining(trackId).map { it.toSet() }.flowOn(dispatcher)

    override suspend fun create(name: String): Long = withContext(dispatcher) {
        val time = now()
        dao.insert(PlaylistEntity(name = name, createdAt = time, updatedAt = time))
    }

    override suspend fun rename(playlistId: Long, name: String) = withContext(dispatcher) {
        dao.rename(playlistId, name, now())
    }

    override suspend fun delete(playlistId: Long) = withContext(dispatcher) { dao.delete(playlistId) }

    override suspend fun addTrack(playlistId: Long, trackId: String): Boolean = withContext(dispatcher) {
        dao.appendTrack(playlistId, trackId, now())
    }

    override suspend fun removeTrack(playlistId: Long, trackId: String) = withContext(dispatcher) {
        dao.removeTrack(playlistId, trackId)
        dao.touch(playlistId, now())
    }

    override suspend fun reorder(playlistId: Long, trackIds: List<String>) = withContext(dispatcher) {
        dao.reorder(playlistId, trackIds, now())
    }
}
