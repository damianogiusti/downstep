package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.data.library.HttpFileDataSource
import com.damianogiusti.slowdowner.data.library.HttpStatusException
import com.damianogiusti.slowdowner.data.logging.failure
import com.damianogiusti.slowdowner.domain.stems.ModelDownloadResult
import com.damianogiusti.slowdowner.domain.stems.StemModelRepository
import com.damianogiusti.slowdowner.domain.stems.StemModelState
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Model files in `<modelsDir>/<spec.id>/<name>`, downloaded on demand and checked by size and
 * SHA-256 before they count as there.
 */
class DefaultStemModelRepository(
    private val http: HttpFileDataSource,
    private val spec: StemModelSpec,
    modelsDir: File,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val logger: Logger,
    private val checksum: FileChecksum = FileChecksum(),
) : StemModelRepository {

    private val dir = File(modelsDir, spec.id)

    // Files change outside Room, so the state is cached here and refreshed after each change,
    // like the effects settings. Only a cold Flow leaves the repository.
    private val cache = MutableStateFlow<StemModelState?>(null)
    private val changeLock = Mutex()

    init {
        require(isSafeName(spec.id)) { "Not a valid model id for a folder name" }
        require(spec.files.all { isSafeName(it.name) }) { "Not a valid model file name" }
    }

    override fun observe(): Flow<StemModelState> =
        cache.onStart { refresh() }.filterNotNull()

    override suspend fun download(onProgress: (Float) -> Unit): ModelDownloadResult = changeLock.withLock {
        withContext(dispatcher) {
            try {
                fetchMissingFiles(onProgress)
                logger.i("Stem model downloaded", mapOf("model" to spec.id))
                ModelDownloadResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: CorruptedModelException) {
                logger.w("Stem model verification failed", e, mapOf("model" to spec.id, "file" to e.fileName))
                ModelDownloadResult.Corrupted
            } catch (e: HttpStatusException) {
                val properties = mapOf("model" to spec.id, "status" to e.status.toString())
                if (e.status < HTTP_SERVER_ERROR) {
                    // Refused (403, 404): the link is wrong or gone, and retrying will not help.
                    logger.e("Stem model refused by the server", e, properties)
                    ModelDownloadResult.Failure(e)
                } else {
                    logger.w("Stem model server error", e, properties)
                    ModelDownloadResult.NetworkError
                }
            } catch (e: IOException) {
                // Timeouts, dropped connections, a full disk: the user's side.
                logger.w("Stem model I/O error", e, mapOf("model" to spec.id))
                ModelDownloadResult.NetworkError
            } catch (e: Exception) {
                logger.failure("Stem model download failed", e, mapOf("model" to spec.id))
                ModelDownloadResult.Failure(e)
            } finally {
                refresh()
            }
        }
    }

    override suspend fun delete() = changeLock.withLock {
        withContext(dispatcher) {
            try {
                deleteFolder()
            } finally {
                refresh()
            }
        }
    }

    private suspend fun fetchMissingFiles(onProgress: (Float) -> Unit) {
        dir.mkdirs()
        val total = spec.files.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var finished = 0L
        for (file in spec.files) {
            val target = File(dir, file.name)
            if (!isVerified(target, file)) {
                target.delete()
                http.download(file.url, target) { downloaded, _ ->
                    onProgress(((finished + downloaded.coerceAtMost(file.sizeBytes)).toFloat() / total).coerceIn(0f, 1f))
                }
                if (!isVerified(target, file)) {
                    target.delete()
                    throw CorruptedModelException(file.name)
                }
            }
            finished += file.sizeBytes
            onProgress((finished.toFloat() / total).coerceIn(0f, 1f))
        }
    }

    private fun isVerified(target: File, file: ModelFileSpec) =
        hasExpectedSize(target, file) && checksum.sha256(target).equals(file.sha256, ignoreCase = true)

    private fun hasExpectedSize(target: File, file: ModelFileSpec) =
        Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) && target.length() == file.sizeBytes

    /** Size only: hashing tens of megabytes on every observe is not worth it, download verifies the hash. */
    private fun refresh() {
        val isReady = spec.files.all { hasExpectedSize(File(dir, it.name), it) }
        cache.value = if (isReady) StemModelState.Ready else StemModelState.NotDownloaded
    }

    /**
     * Only regular files directly inside the folder (partial downloads included), then the folder.
     * No recursion and no link is followed.
     */
    private fun deleteFolder() {
        if (!Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        dir.listFiles()?.forEach { child ->
            if (Files.isRegularFile(child.toPath(), LinkOption.NOFOLLOW_LINKS)) child.delete()
        }
        // Fails, on purpose, while something else is still inside.
        dir.delete()
    }

    private fun isSafeName(name: String) =
        name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' } &&
            !name.contains("..")

    private class CorruptedModelException(val fileName: String) : IOException("Model file does not match its size or checksum")

    private companion object {
        const val HTTP_SERVER_ERROR = 500
    }
}
