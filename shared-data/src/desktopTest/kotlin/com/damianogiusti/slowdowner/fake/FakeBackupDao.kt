package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.backup.BackupDao
import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.loops.LoopEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistTrackEntity
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity

/** In-memory [BackupDao] with the conflict and foreign key rules of the Room schema. */
class FakeBackupDao : BackupDao {

    val tracks = mutableListOf<TrackEntity>()
    val states = mutableListOf<PracticeStateEntity>()
    val presets = mutableListOf<PracticePresetEntity>()
    val loops = mutableListOf<LoopEntity>()
    val playlists = mutableListOf<PlaylistEntity>()
    val links = mutableListOf<PlaylistTrackEntity>()

    /** Thrown by [insertPlaylist], to fail a restore after its tracks are in. */
    var playlistInsertError: Exception? = null

    private var nextPresetId = 1L
    private var nextPlaylistId = 1L
    private var nextLoopId = 1L

    fun addPlaylist(name: String, trackIds: List<String>): Long {
        val id = nextPlaylistId++
        playlists += PlaylistEntity(id, name, createdAt = id, updatedAt = id)
        trackIds.forEachIndexed { position, trackId -> links += PlaylistTrackEntity(id, trackId, position) }
        return id
    }

    fun addLoop(loop: LoopEntity): LoopEntity = loop.copy(id = nextLoopId++).also { loops += it }

    fun addPreset(preset: PracticePresetEntity) {
        presets += preset.copy(id = nextPresetId++)
    }

    override suspend fun findTrack(trackId: String) = tracks.firstOrNull { it.id == trackId }

    override suspend fun allTracks() = tracks.sortedBy { it.addedAt }

    override suspend fun tracksOf(playlistId: Long) =
        trackIdsOf(playlistId).mapNotNull { id -> tracks.firstOrNull { it.id == id } }

    override suspend fun allTrackIds() = tracks.map { it.id }

    override suspend fun trackExists(trackId: String) = tracks.any { it.id == trackId }

    override suspend fun findPlaylist(playlistId: Long) = playlists.firstOrNull { it.id == playlistId }

    override suspend fun allPlaylists() = playlists.sortedBy { it.createdAt }

    override suspend fun playlistNames() = playlists.map { it.name }

    override suspend fun findPlaylistByName(name: String) =
        playlists.filter { it.name == name }.minWithOrNull(compareBy({ it.createdAt }, { it.id }))

    override suspend fun clearPlaylist(playlistId: Long) {
        links.removeAll { it.playlistId == playlistId }
    }

    override suspend fun setPlaylistUpdatedAt(playlistId: Long, updatedAt: Long) {
        playlists.replaceAll { if (it.id == playlistId) it.copy(updatedAt = updatedAt) else it }
    }

    override suspend fun trackIdsOf(playlistId: Long) =
        links.filter { it.playlistId == playlistId }.sortedBy { it.position }.map { it.trackId }

    override suspend fun findState(trackId: String) = states.firstOrNull { it.trackId == trackId }

    override suspend fun presetsOf(trackId: String) = presets.filter { it.trackId == trackId }.sortedBy { it.createdAt }

    override suspend fun loopsOf(trackId: String) =
        loops.filter { it.trackId == trackId }.sortedWith(compareBy({ it.start }, { it.id }))

    override suspend fun hasLoops(trackId: String) = loops.any { it.trackId == trackId }

    override suspend fun insertLoop(loop: LoopEntity): Long {
        requireTrack(loop.trackId)
        return addLoop(loop).id
    }

    override suspend fun setActiveLoop(trackId: String, loopId: Long) {
        states.replaceAll { if (it.trackId == trackId) it.copy(activeLoopId = loopId) else it }
    }

    override suspend fun clearRegion(trackId: String) {
        states.replaceAll {
            if (it.trackId == trackId) it.copy(settings = it.settings.copy(loopStart = 0f, loopEnd = 1f, isLoopOn = false)) else it
        }
    }

    override suspend fun insertTrackIfAbsent(track: TrackEntity): Long {
        if (trackExists(track.id)) return -1
        tracks += track
        return tracks.size.toLong()
    }

    override suspend fun insertStateIfAbsent(state: PracticeStateEntity): Long {
        requireTrack(state.trackId)
        if (states.any { it.trackId == state.trackId }) return -1
        states += state
        return states.size.toLong()
    }

    override suspend fun insertPresetIfNameFree(preset: PracticePresetEntity): Long {
        requireTrack(preset.trackId)
        if (presets.any { it.trackId == preset.trackId && it.name == preset.name }) return -1
        val id = nextPresetId++
        presets += preset.copy(id = id)
        return id
    }

    override suspend fun insertPlaylist(playlist: PlaylistEntity): Long {
        playlistInsertError?.let { throw it }
        val id = nextPlaylistId++
        playlists += playlist.copy(id = id)
        return id
    }

    override suspend fun insertPlaylistTrack(link: PlaylistTrackEntity): Long {
        requireTrack(link.trackId)
        if (links.any { it.playlistId == link.playlistId && it.trackId == link.trackId }) return -1
        links += link
        return links.size.toLong()
    }

    // IGNORE does not cover foreign keys: Room throws, and so does the fake.
    private fun requireTrack(trackId: String) =
        check(tracks.any { it.id == trackId }) { "FOREIGN KEY constraint failed: $trackId" }
}
