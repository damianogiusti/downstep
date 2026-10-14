package com.damianogiusti.slowdowner.data.library

import com.damianogiusti.slowdowner.data.logging.failure
import com.damianogiusti.slowdowner.domain.library.ImportProgress
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.library.LibraryRepository
import com.damianogiusti.slowdowner.logging.Logger
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
    // The user only sees a generic message: the log keeps the real cause.
    private val logger: Logger,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
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
        mapErrors(onIoError = ImportResult.NetworkError, source = SOURCE_YOUTUBE, url = url) {
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
        mapErrors(onIoError = ImportResult.Unavailable, source = SOURCE_FILE, url = null) {
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
                    .onFailure { error ->
                        logger.w("Remux failed, original kept", error, mapOf("extension" to target.extension.lowercase()))
                        if (!staging.renameTo(target)) throw IOException("Cannot move $staging")
                    }
            } else if (!staging.renameTo(target)) {
                throw IOException("Cannot move $staging to $target")
            }
        } finally {
            staging.delete()
        }
    }

    private inline fun mapErrors(
        onIoError: ImportResult,
        source: String,
        url: String?,
        block: () -> LibraryTrackDto,
    ): ImportResult = try {
        val track = block()
        logger.i("Import finished", mapOf("source" to source, "extension" to File(track.filePath).extension.lowercase()))
        ImportResult.Success(LibraryTrackMapper.toDomain(track))
    } catch (e: CancellationException) {
        throw e
    } catch (e: StreamUnavailableException) {
        // Many of these at once usually mean YouTube changed and the extractor needs an update.
        logger.w("Import unavailable", e, mapOf("source" to source))
        ImportResult.Unavailable
    } catch (e: HttpStatusException) {
        val properties = mapOf("source" to source, "status" to e.status.toString())
        if (e.status < HTTP_SERVER_ERROR) {
            // The stream was refused (403, 404): usually YouTube changed and the extractor needs an
            // update. Retrying does not help, so it is not a network error for the user.
            logger.e("Import refused by the server", e, properties)
            ImportResult.Unavailable
        } else {
            logger.w("Import server error", e, properties)
            onIoError
        }
    } catch (e: IOException) {
        // Timeouts, dropped connections, a full disk: the user's side.
        logger.w("Import I/O error", e, mapOf("source" to source))
        onIoError
    } catch (e: Exception) {
        // The link is public and tells which video breaks the extractor; a local path never goes out.
        val link = url?.let { mapOf("url" to videoLink(it)) }.orEmpty()
        logger.failure("Import failed", e, mapOf("source" to source) + link)
        ImportResult.Failure(e)
    }

    /**
     * A pasted link can carry more than the video: a playlist (`list=`), a share token (`si=`). Only
     * the video id is kept.
     */
    private fun videoLink(url: String): String {
        val query = url.substringAfter('?', "").substringBefore('#')
        val video = query.split('&').firstOrNull { it.startsWith("v=") }
        return url.substringBefore('?').substringBefore('#') + (video?.let { "?$it" } ?: "")
    }

    private companion object {
        const val SOURCE_YOUTUBE = "youtube"
        const val SOURCE_FILE = "file"
        const val HTTP_SERVER_ERROR = 500
        const val DEFAULT_NAME = "audio"
        const val DEFAULT_EXTENSION = "bin"
        val MP4_EXTENSIONS = setOf("m4a", "mp4")
    }
}
