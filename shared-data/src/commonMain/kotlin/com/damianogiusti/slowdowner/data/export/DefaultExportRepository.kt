package com.damianogiusti.slowdowner.data.export

import com.damianogiusti.slowdowner.data.library.DocumentDataSource
import com.damianogiusti.slowdowner.data.logging.failure
import com.damianogiusti.slowdowner.domain.export.ExportItem
import com.damianogiusti.slowdowner.domain.export.ExportRepository
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DefaultExportRepository(
    private val renderer: OfflineRenderer,
    private val documents: DocumentDataSource,
    /** App-private and shared through a FileProvider: it holds only the latest export batch. */
    private val exportDir: File,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val logger: Logger,
) : ExportRepository {

    override suspend fun exportOpenTrack(
        items: List<ExportItem>,
        onProgress: (Float) -> Unit,
    ): ExportResult = withContext(dispatcher) {
        exportDir.mkdirs()
        // Old exports were already shared or saved: do not let the cache grow. Once per batch, so
        // the batch's own files stay.
        exportDir.listFiles()?.forEach(File::delete)
        val format = mapOf("format" to (items.firstOrNull()?.fileName?.substringAfterLast('.', "")?.lowercase() ?: ""))
        val properties = format + ("files" to items.size.toString())
        // Progress is weighted by each item's share of the track: at one speed for the whole batch,
        // that is its share of the render time, so the bar moves evenly from the first file to the last.
        val weights = items.map { it.length.coerceAtLeast(MIN_WEIGHT) }
        val total = weights.sum()
        val written = mutableListOf<File>()
        var current: File? = null
        var isComplete = false
        try {
            var done = 0f
            items.forEachIndexed { index, item ->
                val target = File(exportDir, item.fileName)
                current = target // the engine may have created it before failing to start
                if (!renderer.begin(target.path, item.start, item.end)) {
                    logger.e("Export failed", properties = properties + ("stage" to "begin") + ("index" to index.toString()))
                    return@withContext ExportResult.Failure(IOException("Cannot start export"))
                }
                try {
                    while (true) {
                        ensureActive()
                        val progress = renderer.renderNext() ?: break
                        onProgress(((done + progress.coerceIn(0f, 1f) * weights[index]) / total).coerceAtMost(1f))
                    }
                } finally {
                    renderer.end()
                }
                current = null
                written += target
                if (target.length() <= 0) {
                    logger.e("Export failed", properties = properties + ("stage" to "empty") + ("index" to index.toString()))
                    return@withContext ExportResult.Failure(IOException("Export produced an empty file"))
                }
                done += weights[index]
            }
            isComplete = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.failure("Export failed", e, properties + ("stage" to "render"))
            return@withContext ExportResult.Failure(e)
        } finally {
            // All or nothing: a cancelled or failed batch leaves no file behind.
            if (!isComplete) {
                current?.delete()
                written.forEach(File::delete)
            }
        }

        logger.i("Export finished", properties)
        ExportResult.Success(written.map { ExportedFile(it.path, it.name) })
    }

    override suspend fun saveTo(file: ExportedFile, contentUri: String): Boolean = withContext(dispatcher) {
        try {
            documents.write(File(file.path), contentUri)
            true
        } catch (e: IOException) {
            logger.w("Cannot save the export", e)
            false
        }
    }

    override suspend fun saveAllTo(files: List<ExportedFile>, folder: String): Boolean = withContext(dispatcher) {
        val directory = File(folder)
        var isComplete = true
        for (file in files) {
            try {
                documents.write(File(file.path), freeTarget(directory, file.name).path)
            } catch (e: IOException) {
                logger.w("Cannot save the export", e)
                isComplete = false
            }
        }
        isComplete
    }

    /** [name] in [directory], or "name (2).mp3", "name (3).mp3"… if a file already has it. */
    private fun freeTarget(directory: File, name: String): File {
        val stem = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var target = File(directory, name)
        var copy = 2
        while (target.exists()) target = File(directory, "$stem (${copy++})$extension")
        return target
    }

    private companion object {
        /** A loop is never empty, but a zero weight would stall the bar on it. */
        const val MIN_WEIGHT = 0.0001f
    }
}
