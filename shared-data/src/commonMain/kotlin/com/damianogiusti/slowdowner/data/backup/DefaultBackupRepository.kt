package com.damianogiusti.slowdowner.data.backup

import com.damianogiusti.slowdowner.data.database.DatabaseTransactionRunner
import com.damianogiusti.slowdowner.data.library.DocumentDataSource
import com.damianogiusti.slowdowner.data.logging.failure
import com.damianogiusti.slowdowner.data.library.LibraryStore
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistTrackEntity
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.domain.backup.BackupExportResult
import com.damianogiusti.slowdowner.domain.backup.BackupFile
import com.damianogiusti.slowdowner.domain.backup.BackupProgress
import com.damianogiusti.slowdowner.domain.backup.BackupRepository
import com.damianogiusti.slowdowner.domain.backup.BackupScope
import com.damianogiusti.slowdowner.domain.backup.PlaylistConflictChoice
import com.damianogiusti.slowdowner.domain.backup.RestoreResult
import com.damianogiusti.slowdowner.domain.backup.RestoreRules
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipException

class DefaultBackupRepository(
    private val dao: BackupDao,
    private val transactions: DatabaseTransactionRunner,
    private val store: LibraryStore,
    private val documents: DocumentDataSource,
    private val archive: BackupArchive,
    /** App-private and shared through a FileProvider: it holds only the latest backup. */
    private val backupDir: File,
    /** Audio of a restore in progress. Same volume as the library, so a rename moves it. */
    private val stagingDir: File,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val logger: Logger,
    private val now: () -> Long = System::currentTimeMillis,
) : BackupRepository {

    override suspend fun export(
        scope: BackupScope,
        fileName: String,
        onProgress: (BackupProgress) -> Unit,
    ): BackupExportResult = withContext(dispatcher) {
        val job = coroutineContext.job
        try {
            // One snapshot: an auto-save or a delete in the middle cannot mix two states.
            val contents = transactions.inTransaction { collect(scope) }
                ?: return@withContext BackupExportResult.NothingToExport
            val total = contents.audioFiles.size
            onProgress(BackupProgress(0, total))
            backupDir.mkdirs()
            val target = File(backupDir, fileName)
            val partial = File(backupDir, fileName + PARTIAL_SUFFIX)
            try {
                archive.write(partial, contents) { done ->
                    // Checked between files: one long copy is not interrupted.
                    job.ensureActive()
                    onProgress(BackupProgress(done, total))
                }
                if (!partial.renameTo(target)) throw IOException("Cannot move $partial to $target")
            } finally {
                partial.delete()
            }
            // Old backups were already shared or saved: do not let the cache grow. Only now,
            // so a failed export keeps the previous one.
            backupDir.listFiles()?.filter { it != target }?.forEach(File::delete)
            logger.i("Backup exported", mapOf("scope" to scope.logName()))
            BackupExportResult.Success(BackupFile(target.path, target.name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.failure("Backup export failed", e, mapOf("scope" to scope.logName()))
            BackupExportResult.Failure(e)
        }
    }

    override suspend fun saveTo(file: BackupFile, contentUri: String): Boolean = withContext(dispatcher) {
        try {
            documents.write(File(file.path), contentUri)
            true
        } catch (e: IOException) {
            logger.w("Cannot save the backup", e)
            false
        }
    }

    override suspend fun findPlaylistConflicts(contentUri: String): List<String> = withContext(dispatcher) {
        try {
            val manifest = documents.open(contentUri).use(archive::readManifest)
            // Every backup track is in the library after the restore, new or kept.
            val restoredIds = dao.allTrackIds().toSet() + manifest.tracks.map { it.id }
            // Same rule as insertRows: same name and same tracks in the same order is no conflict.
            manifest.playlists
                .filter { playlist ->
                    val existing = dao.findPlaylistByName(RestoreRules.playlistName(playlist.name))
                    existing != null && dao.trackIdsOf(existing.id) != playlist.trackIds.distinct().filter { it in restoredIds }
                }
                .map { RestoreRules.playlistName(it.name) }
                .distinct()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.failure("Cannot read the backup playlists", e)
            emptyList()
        }
    }

    override suspend fun restore(
        contentUri: String,
        onConflict: PlaylistConflictChoice,
        onProgress: (BackupProgress) -> Unit,
    ): RestoreResult = withContext(dispatcher) {
        val job = coroutineContext.job
        val incoming = mutableListOf<IncomingTrack>()
        try {
            // Left over by a restore that died with the process.
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            val existingIds = dao.allTrackIds().toSet()
            val manifest = documents.open(contentUri).use { input ->
                archive.read(
                    input = input,
                    plan = { manifest ->
                        incoming += planNewTracks(manifest, existingIds)
                        onProgress(BackupProgress(0, incoming.size))
                        incoming.associate { it.entry to it.staging }
                    },
                    onAudioExtracted = { done ->
                        job.ensureActive()
                        onProgress(BackupProgress(done, incoming.size))
                    },
                )
            }
            // Files first, like an import: a file without a row is invisible, a row without a
            // file is a broken track.
            incoming.forEach(::moveIntoLibrary)
            // Not cancellable from here: a cancel after the commit would delete files of rows
            // that are already saved.
            withContext(NonCancellable) { transactions.inTransaction { insertRows(manifest, incoming, onConflict) } }
                .also { result ->
                    logger.i(
                        "Backup restored",
                        mapOf(
                            "tracksAdded" to result.tracksAdded.toString(),
                            "tracksSkipped" to result.tracksSkipped.toString(),
                            "playlists" to result.playlistsRestored.toString(),
                        ),
                    )
                }
        } catch (e: CancellationException) {
            deleteFiles(incoming)
            throw e
        } catch (e: InvalidBackupException) {
            deleteFiles(incoming)
            // Usually not a bug: a file that is not a backup.
            logger.w("Invalid backup", e)
            RestoreResult.InvalidFile
        } catch (e: ZipException) {
            deleteFiles(incoming)
            logger.w("Damaged backup", e)
            RestoreResult.InvalidFile
        } catch (e: UnsupportedBackupVersionException) {
            deleteFiles(incoming)
            // A backup from a newer app: tells how many people run old versions.
            logger.w("Unsupported backup version", properties = mapOf("version" to e.version.toString()))
            RestoreResult.UnsupportedVersion(e.version)
        } catch (e: Exception) {
            deleteFiles(incoming)
            logger.failure("Restore failed", e)
            RestoreResult.Failure(e)
        }
    }

    private suspend fun collect(scope: BackupScope): BackupContents? {
        val (tracks, playlists) = when (scope) {
            is BackupScope.Track -> listOfNotNull(dao.findTrack(scope.trackId)) to emptyList()
            BackupScope.Library -> dao.allTracks() to dao.allPlaylists()
            is BackupScope.Playlist -> {
                val playlist = dao.findPlaylist(scope.playlistId) ?: return null
                dao.tracksOf(playlist.id) to listOf(playlist)
            }
        }
        // Same rule as the library: a track whose file is gone is not there.
        val included = tracks.filter { File(it.filePath).isFile && BackupEntryNames.isSafeId(it.id) }
        if (included.isEmpty() && playlists.isEmpty()) return null

        val audioFiles = LinkedHashMap<String, File>()
        val trackDtos = included.map { track ->
            val file = File(track.filePath)
            val entry = BackupEntryNames.audioEntry(track.id, BackupEntryNames.safeExtension(file.extension))
            audioFiles[entry] = file
            BackupManifestMapper.toDto(
                TrackBackupRows(track, dao.findState(track.id), dao.presetsOf(track.id), dao.loopsOf(track.id)),
                entry,
            )
        }
        val includedIds = included.mapTo(HashSet()) { it.id }
        val playlistDtos = playlists.map { playlist ->
            BackupManifestMapper.toDto(playlist, dao.trackIdsOf(playlist.id).filter { it in includedIds })
        }
        val manifest = BackupManifestDto(
            format = BackupManifestDto.FORMAT,
            version = BackupManifestDto.VERSION,
            createdAt = now(),
            kind = BackupManifestMapper.toKind(scope),
            tracks = trackDtos,
            playlists = playlistDtos,
        )
        return BackupContents(manifest, audioFiles)
    }

    private fun planNewTracks(manifest: BackupManifestDto, existingIds: Set<String>): List<IncomingTrack> =
        manifest.tracks
            .filter { it.id !in existingIds }
            .map { track ->
                val extension = BackupEntryNames.audioExtension(track.id, track.audio)
                    ?: throw InvalidBackupException("Unexpected audio entry: ${track.audio}")
                val target = store.fileFor(track.id, extension)
                IncomingTrack(track.id, track.audio, File(stagingDir, target.name), target)
            }

    private fun moveIntoLibrary(track: IncomingTrack) {
        if (!track.staging.renameTo(track.target)) throw IOException("Cannot move ${track.staging} to ${track.target}")
    }

    /**
     * Merge: every track, state and preset insert keeps the row that is already there. Only a
     * playlist with a taken name follows [onConflict].
     */
    private suspend fun insertRows(
        manifest: BackupManifestDto,
        incoming: List<IncomingTrack>,
        onConflict: PlaylistConflictChoice,
    ): RestoreResult.Success {
        val time = now()
        val incomingById = incoming.associateBy { it.id }
        var added = 0
        manifest.tracks.forEach { track ->
            val newTrack = incomingById[track.id]
            if (newTrack != null && dao.insertTrackIfAbsent(BackupManifestMapper.toTrackEntity(track, newTrack.target.path)) != NOT_INSERTED) {
                added++
            }
            // Deleted since the restore started: its state and presets have nowhere to go.
            if (!dao.trackExists(track.id)) return@forEach
            val stateRestored = track.practiceState?.let { state ->
                dao.insertStateIfAbsent(BackupManifestMapper.toStateEntity(track, state, time)) != NOT_INSERTED
            } ?: false
            restoreLoops(track, stateRestored, time)
            track.presets.forEach { preset ->
                val name = RestoreRules.presetName(preset.name) ?: return@forEach
                dao.insertPresetIfNameFree(
                    PracticePresetEntity(
                        trackId = track.id,
                        name = name,
                        settings = BackupManifestMapper.toColumns(preset.settings),
                        createdAt = time,
                    ),
                )
            }
        }

        val takenNames = dao.playlistNames().toMutableSet()
        // A backup can hold two playlists with one name: the second must not overwrite the first.
        val restoredPlaylistIds = mutableSetOf<Long>()
        var playlistsRestored = 0
        manifest.playlists.forEach { playlist ->
            val name = RestoreRules.playlistName(playlist.name)
            val trackIds = playlist.trackIds.distinct().filter { dao.trackExists(it) }
            val existing = dao.findPlaylistByName(name)
            val playlistId = when {
                existing == null -> null
                // Already there as it is: a copy would only be a duplicate. Same rule as
                // findPlaylistConflicts. Marked as restored, so a same-name playlist later in
                // the backup becomes a copy instead of replacing it.
                dao.trackIdsOf(existing.id) == trackIds -> {
                    restoredPlaylistIds += existing.id
                    return@forEach
                }
                onConflict == PlaylistConflictChoice.Overwrite && existing.id !in restoredPlaylistIds -> {
                    dao.clearPlaylist(existing.id)
                    dao.setPlaylistUpdatedAt(existing.id, time)
                    existing.id
                }
                else -> null
            } ?: run {
                val freeName = RestoreRules.uniquePlaylistName(name, takenNames).also { takenNames += it }
                dao.insertPlaylist(PlaylistEntity(name = freeName, createdAt = time, updatedAt = time))
            }
            trackIds.forEachIndexed { position, trackId ->
                dao.insertPlaylistTrack(PlaylistTrackEntity(playlistId, trackId, position))
            }
            restoredPlaylistIds += playlistId
            playlistsRestored++
        }
        return RestoreResult.Success(
            tracksAdded = added,
            tracksSkipped = manifest.tracks.size - added,
            playlistsRestored = playlistsRestored,
        )
    }

    /**
     * A track that already has loops keeps them: merging two sets would duplicate every loop on a
     * second restore. The active loop comes from the backup only with its state row, whose region
     * mirrors it.
     */
    private suspend fun restoreLoops(track: BackupTrackDto, stateRestored: Boolean, time: Long) {
        if (track.loops.isEmpty() || dao.hasLoops(track.id)) return
        val ids = track.loops.mapIndexed { position, loop ->
            BackupManifestMapper.toLoopEntity(loop, track.id, position, time)?.let { dao.insertLoop(it) }
        }
        val activeId = track.activeLoop?.let(ids::getOrNull) ?: return
        if (stateRestored) dao.setActiveLoop(track.id, activeId)
    }

    private fun deleteFiles(tracks: List<IncomingTrack>) {
        tracks.forEach { track ->
            track.staging.delete()
            track.target.delete()
        }
    }

    /** A track that is not in the library yet: its audio goes to [staging], then to [target]. */
    private data class IncomingTrack(val id: String, val entry: String, val staging: File, val target: File)

    private fun BackupScope.logName() = when (this) {
        is BackupScope.Track -> "track"
        BackupScope.Library -> "library"
        is BackupScope.Playlist -> "playlist"
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".part"
        const val NOT_INSERTED = -1L
    }
}
