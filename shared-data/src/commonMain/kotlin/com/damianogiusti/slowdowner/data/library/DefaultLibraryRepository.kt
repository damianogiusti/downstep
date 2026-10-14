package com.damianogiusti.slowdowner.data.library

import com.damianogiusti.slowdowner.data.platform.platformLogWarning
import com.damianogiusti.slowdowner.domain.library.ImportProgress
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.library.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class DefaultLibraryRepository(
    private val store: LibraryStore,
    private val youTube: YouTubeStreamDataSource,
    private val http: HttpFileDataSource,
    private val documents: DocumentDataSource,
    private val containers: MediaContainerDataSource,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    // The user only sees a generic message: keep the real cause for debugging.
    private val logError: (String, Throwable) -> Unit = { message, error -> platformLogWarning(TAG, message, error) },
) : LibraryRepository {

    override fun observeTracks(): Flow<List<LibraryTrack>> =
        store.observe()
            .map { tracks -> tracks.map(LibraryTrackMapper::toDomain).sortedByDescending { it.addedAt } }
            .flowOn(dispatcher)

    override suspend fun remove(id: String) = withContext(dispatcher) { store.remove(id) }

    override fun findImportableUrl(text: String?): String? = youTube.findSupportedUrl(text)

    override suspend fun importFromUrl(
        url: String,
        onProgress: (ImportProgress) -> Unit,
    ): ImportResult = withContext(dispatcher) {
        mapErrors(onIoError = ImportResult.NetworkError) {
            onProgress(ImportProgress.Resolving)
            val audio = youTube.resolve(url)
            val id = "yt_${audio.id}"
            store.find(id)?.let { return@mapErrors it }

            // Stream URLs expire within hours: keep the file, never the URL.
            val target = store.fileFor(id, audio.extension)
            withPlayableFile(target) { download ->
                http.download(audio.streamUrl, download) { done, total ->
                    onProgress(ImportProgress.Downloading(audio.title, done, total))
                }
            }
            LibraryTrackDto(id, audio.title, target.path, url, now()).also { store.add(it) }
        }
    }

    override suspend fun importDocument(contentUri: String): ImportResult = withContext(dispatcher) {
        mapErrors(onIoError = ImportResult.Unavailable) {
            val name = documents.displayName(contentUri) ?: DEFAULT_NAME
            val id = "local_${newId()}"
            val target = store.fileFor(id, name.substringAfterLast('.', DEFAULT_EXTENSION))
            withPlayableFile(target) { copy -> documents.copy(contentUri, copy) }
            LibraryTrackDto(id, name.substringBeforeLast('.'), target.path, null, now()).also { store.add(it) }
        }
    }

    /**
     * Writes through [write] into a staging file, then moves it to [target] in a form the
     * engine seeks cleanly. YouTube serves MP4 audio as fragmented (DASH) files; the engine's
     * decoder needs about a second to seek in those, which is heard as a stutter on every seek
     * and loop restart. A plain MP4 with the same samples seeks instantly.
     */
    private inline fun withPlayableFile(target: File, write: (File) -> Unit) {
        val staging = File(target.path + ".staging")
        try {
            write(staging)
            if (target.extension.lowercase() in MP4_EXTENSIONS) {
                runCatching { containers.remuxToProgressiveMp4(staging, target) }
                    // Still playable as-is, only with slower seeks: better than failing the import.
                    .onFailure { if (!staging.renameTo(target)) throw IOException("Cannot move $staging") }
            } else if (!staging.renameTo(target)) {
                throw IOException("Cannot move $staging to $target")
            }
        } finally {
            staging.delete()
        }
    }

    private inline fun mapErrors(onIoError: ImportResult, block: () -> LibraryTrackDto): ImportResult = try {
        ImportResult.Success(LibraryTrackMapper.toDomain(block()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: StreamUnavailableException) {
        logError("Import failed: stream unavailable", e)
        ImportResult.Unavailable
    } catch (e: IOException) {
        logError("Import failed: I/O", e)
        onIoError
    } catch (e: Exception) {
        logError("Import failed", e)
        ImportResult.Failure(e)
    }

    private companion object {
        const val TAG = "SlowDowner"
        const val DEFAULT_NAME = "audio"
        const val DEFAULT_EXTENSION = "bin"
        val MP4_EXTENSIONS = setOf("m4a", "mp4")
    }
}
