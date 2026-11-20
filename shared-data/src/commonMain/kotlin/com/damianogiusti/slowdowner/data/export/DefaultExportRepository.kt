package com.damianogiusti.slowdowner.data.export

import com.damianogiusti.slowdowner.data.library.DocumentDataSource
import com.damianogiusti.slowdowner.data.logging.failure
import com.damianogiusti.slowdowner.domain.export.ExportRepository
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.export.RenderRange
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DefaultExportRepository(
    private val renderer: OfflineRenderer,
    private val documents: DocumentDataSource,
    /** App-private and shared through a FileProvider: it holds only the latest export. */
    private val exportDir: File,
    /** App-private: renders that become library tracks, moved out as soon as they are complete. */
    private val trackDir: File,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val logger: Logger,
) : ExportRepository {

    override suspend fun exportOpenTrack(
        fileName: String,
        range: RenderRange,
        onProgress: (Float) -> Unit,
    ): ExportResult = withContext(dispatcher) {
        // Old exports were already shared or saved: do not let the cache grow.
        render(File(exportDir, fileName), RenderRequest(range, RenderSound.CURRENT), onProgress)
    }

    override suspend fun renderOriginal(range: RenderRange, onProgress: (Float) -> Unit): ExportResult =
        withContext(dispatcher) {
            // A render left by a crash or a kill was never moved into the library.
            render(File(trackDir, TRACK_FILE_NAME), RenderRequest(range, RenderSound.ORIGINAL), onProgress)
        }

    /** Empties [target]'s folder, then renders into [target]. */
    private suspend fun render(target: File, request: RenderRequest, onProgress: (Float) -> Unit): ExportResult {
        val dir = target.parentFile ?: return ExportResult.Failure(IOException("No folder for the render"))
        dir.mkdirs()
        dir.listFiles()?.forEach(File::delete)
        val properties = mapOf(
            "format" to target.extension.lowercase(),
            "part" to if (request.range == RenderRange.WHOLE_FILE) "whole" else "range",
            "sound" to request.sound.name.lowercase(),
        )
        if (!renderer.begin(target.path, request)) {
            logger.e("Export failed", properties = properties + ("stage" to "begin"))
            return ExportResult.Failure(IOException("Cannot start export"))
        }

        var isComplete = false
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val progress = renderer.renderNext() ?: break
                onProgress(progress)
            }
            isComplete = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.failure("Export failed", e, properties + ("stage" to "render"))
            return ExportResult.Failure(e)
        } finally {
            renderer.end()
            if (!isComplete) target.delete()
        }

        return if (target.length() > 0) {
            logger.i("Export finished", properties)
            ExportResult.Success(ExportedFile(target.path, target.name))
        } else {
            logger.e("Export failed", properties = properties + ("stage" to "empty"))
            target.delete()
            ExportResult.Failure(IOException("Export produced an empty file"))
        }
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

    private companion object {
        const val TRACK_FILE_NAME = "loop.mp3"
    }
}
