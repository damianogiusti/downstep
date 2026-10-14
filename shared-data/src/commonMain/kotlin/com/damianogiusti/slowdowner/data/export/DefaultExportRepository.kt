package com.damianogiusti.slowdowner.data.export

import com.damianogiusti.slowdowner.data.library.DocumentDataSource
import com.damianogiusti.slowdowner.domain.export.ExportRepository
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DefaultExportRepository(
    private val renderer: OfflineRenderer,
    private val documents: DocumentDataSource,
    /** App-private and shared through a FileProvider: it holds only the latest export. */
    private val exportDir: File,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
) : ExportRepository {

    override suspend fun exportOpenTrack(
        fileName: String,
        onProgress: (Float) -> Unit,
    ): ExportResult = withContext(dispatcher) {
        exportDir.mkdirs()
        // Old exports were already shared or saved: do not let the cache grow.
        exportDir.listFiles()?.forEach(File::delete)
        val target = File(exportDir, fileName)
        if (!renderer.begin(target.path)) return@withContext ExportResult.Failure(IOException("Cannot start export"))

        var isComplete = false
        try {
            while (true) {
                ensureActive()
                val progress = renderer.renderNext() ?: break
                onProgress(progress)
            }
            isComplete = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext ExportResult.Failure(e)
        } finally {
            renderer.end()
            if (!isComplete) target.delete()
        }

        if (target.length() > 0) {
            ExportResult.Success(ExportedFile(target.path, target.name))
        } else {
            target.delete()
            ExportResult.Failure(IOException("Export produced an empty file"))
        }
    }

    override suspend fun saveTo(file: ExportedFile, contentUri: String): Boolean = withContext(dispatcher) {
        try {
            documents.write(File(file.path), contentUri)
            true
        } catch (e: IOException) {
            false
        }
    }
}
