package com.damianogiusti.slowdowner.data.library

import com.damianogiusti.slowdowner.data.platform.platformLogError
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Library index in Room; audio files stay in `filesDir/library`. On first use it imports
 * the old JSON index. The import is idempotent: existing rows are kept, and a crash
 * before the rename only means the next launch imports the same rows again.
 */
class RoomLibraryStore(
    private val dao: TrackDao,
    private val legacyIndex: LegacyJsonLibraryIndex,
    private val libraryDir: File,
    private val logError: (String, Throwable) -> Unit = { message, error -> platformLogError(TAG, message, error) },
) : LibraryStore {

    @Volatile
    private var isLegacyChecked = false
    private val legacyImport = Mutex()

    override fun observe(): Flow<List<LibraryTrackDto>> = flow {
        ensureLegacyImported()
        emitAll(
            dao.observeAll().map { rows ->
                // A file deleted behind our back must not show up as a playable track.
                rows.filter { File(it.filePath).exists() }.map(TrackEntityMapper::toDto)
            },
        )
    }

    override suspend fun find(id: String): LibraryTrackDto? {
        ensureLegacyImported()
        return dao.find(id)?.let(TrackEntityMapper::toDto)
    }

    override suspend fun add(track: LibraryTrackDto) {
        ensureLegacyImported()
        dao.upsert(TrackEntityMapper.toEntity(track))
    }

    override suspend fun remove(id: String) {
        // Import first, or a later import would bring the removed track back.
        ensureLegacyImported()
        val track = dao.find(id)
        // Row first: the database cascades to playlists and presets. A crash in between
        // leaves an orphan file, never a row pointing at nothing.
        dao.delete(id)
        track?.let { File(it.filePath).delete() }
    }

    override fun fileFor(id: String, extension: String) =
        File(libraryDir.apply { mkdirs() }, "$id.$extension")

    private suspend fun ensureLegacyImported() {
        // Checked once outside the lock: after the first import every call is lock-free.
        if (isLegacyChecked) return
        legacyImport.withLock { importLegacyOnce() }
    }

    private suspend fun importLegacyOnce() {
        if (isLegacyChecked) return
        if (legacyIndex.exists()) {
            dao.insertIgnoringExisting(legacyIndex.read().map(TrackEntityMapper::toEntity))
            try {
                legacyIndex.retire()
            } catch (e: IOException) {
                // The rows are in; the next launch imports again and ignores them.
                logError("Cannot retire the legacy library index", e)
            }
        }
        isLegacyChecked = true
    }

    private companion object {
        const val TAG = "SlowDowner"
    }
}
