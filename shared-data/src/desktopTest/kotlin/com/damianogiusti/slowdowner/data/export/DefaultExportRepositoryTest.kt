package com.damianogiusti.slowdowner.data.export

import com.damianogiusti.slowdowner.data.library.DocumentDataSource
import com.damianogiusti.slowdowner.domain.export.ExportItem
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.fake.RecordingLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DefaultExportRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val renderer = FakeOfflineRenderer()
    private val documents = FakeDocumentDataSource()
    private val logger = RecordingLogger()
    private val exportDir get() = File(temp.root, "exports")

    private fun repository(exportDir: File = this.exportDir) =
        DefaultExportRepository(renderer, documents, exportDir, UnconfinedTestDispatcher(), logger)

    private fun single(name: String) = listOf(ExportItem(name))

    @Test
    fun `when the render completes then the file is returned and progress reported`() = runTest {
        val progress = mutableListOf<Float>()

        val result = repository().exportOpenTrack(single("Song 75%.mp3")) { progress += it }

        val file = (result as ExportResult.Success).files.single()
        assertEquals("Song 75%.mp3", file.name)
        assertTrue(File(file.path).length() > 0)
        assertEquals(listOf(0.5f, 1f), progress)
        assertTrue(renderer.isEnded)
        assertEquals(mapOf("format" to "mp3", "files" to "1"), logger.at('I').single().properties)
    }

    @Test
    fun `when exporting then older exports are deleted`() = runTest {
        val exportDir = File(temp.root, "exports").apply { mkdirs() }
        val old = File(exportDir, "Old.mp3").apply { writeBytes(byteArrayOf(1)) }

        repository(exportDir).exportOpenTrack(single("New.mp3")) {}

        assertFalse(old.exists())
    }

    @Test
    fun `when the engine cannot start then the result is Failure`() = runTest {
        renderer.canBegin = { false }

        val result = repository().exportOpenTrack(single("Song.mp3")) {}

        assertTrue(result is ExportResult.Failure)
        assertFalse(renderer.isEnded)
        assertEquals(
            mapOf("format" to "mp3", "files" to "1", "stage" to "begin", "index" to "0"),
            logger.at('E').single().properties,
        )
    }

    @Test
    fun `when the render fails then the partial file is deleted`() = runTest {
        renderer.error = { IllegalStateException("native") }

        val result = repository().exportOpenTrack(single("Song.mp3")) {}

        assertTrue(result is ExportResult.Failure)
        assertTrue(renderer.isEnded)
        assertFalse(File(exportDir, "Song.mp3").exists())
        assertTrue(logger.at('E').single().throwable is IllegalStateException)
    }

    @Test
    fun `when the render is cancelled then the partial file is deleted`() = runTest {
        renderer.error = { CancellationException("cancelled") }

        runCatching { repository().exportOpenTrack(single("Song.mp3")) {} }

        assertTrue(renderer.isEnded)
        assertFalse(File(exportDir, "Song.mp3").exists())
    }

    @Test
    fun `when the render writes nothing then the result is Failure`() = runTest {
        renderer.writesAudio = false

        assertTrue(repository().exportOpenTrack(single("Song.mp3")) {} is ExportResult.Failure)
        assertFalse(File(exportDir, "Song.mp3").exists())
    }

    @Test
    fun `when a batch completes then every file is returned in order with its range`() = runTest {
        val items = listOf(ExportItem("Song 100%.mp3"), ExportItem("Song - 01 Intro.mp3", 0.1f, 0.3f), ExportItem("Song - 02 Solo.mp3", 0.5f, 0.6f))

        val result = repository().exportOpenTrack(items) {}

        val files = (result as ExportResult.Success).files
        assertEquals(items.map { it.fileName }, files.map { it.name })
        assertTrue(files.all { File(it.path).length() > 0 })
        assertEquals(items.map { Triple(File(exportDir, it.fileName).path, it.start, it.end) }, renderer.begins)
        assertEquals(3, renderer.ends)
        assertEquals(mapOf("format" to "mp3", "files" to "3"), logger.at('I').single().properties)
    }

    @Test
    fun `when a batch renders then progress covers the whole batch weighted by length`() = runTest {
        val progress = mutableListOf<Float>()
        // Weights 1 and 0.25: the full track is four fifths of the work.
        val items = listOf(ExportItem("Full.mp3"), ExportItem("Loop.mp3", 0.2f, 0.45f))

        repository().exportOpenTrack(items) { progress += it }

        assertEquals(listOf(0.4f, 0.8f, 0.9f, 1f), progress.map { Math.round(it * 1000) / 1000f })
        assertEquals(progress.sorted(), progress)
    }

    @Test
    fun `when a later file of the batch cannot start then the earlier files are deleted`() = runTest {
        renderer.canBegin = { index -> index == 0 }

        val result = repository().exportOpenTrack(listOf(ExportItem("Full.mp3"), ExportItem("Loop.mp3", 0.2f, 0.4f))) {}

        assertTrue(result is ExportResult.Failure)
        assertFalse(File(exportDir, "Full.mp3").exists())
        assertFalse(File(exportDir, "Loop.mp3").exists())
        assertEquals("1", logger.at('E').single().properties["index"])
    }

    @Test
    fun `when a batch is cancelled then none of its files is left and every render is closed`() = runTest {
        // Cancelled during the second file, after the first one is complete.
        renderer.error = { index -> if (index == 1) CancellationException("cancelled") else null }

        val outcome = runCatching {
            repository().exportOpenTrack(listOf(ExportItem("Full.mp3"), ExportItem("Loop.mp3", 0.2f, 0.4f))) {}
        }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(2, renderer.begins.size)
        assertEquals(2, renderer.ends)
        assertEquals(emptyList<String>(), exportDir.list()?.toList().orEmpty())
    }

    @Test
    fun `when a file of the batch is cut short then the batch fails and leaves no file`() = runTest {
        // The second file's write failed half way (a full disk): it has bytes, but it is not complete.
        renderer.completes = { index -> index != 1 }

        val result = repository().exportOpenTrack(listOf(ExportItem("Full.mp3"), ExportItem("Loop.mp3", 0.2f, 0.4f))) {}

        assertTrue(result is ExportResult.Failure)
        assertEquals(emptyList<String>(), exportDir.list()?.toList().orEmpty())
        assertEquals(mapOf("format" to "mp3", "files" to "2", "stage" to "finish", "index" to "1"), logger.at('E').single().properties)
    }

    @Test
    fun `when a batch exports then older exports are deleted once and not between files`() = runTest {
        exportDir.mkdirs()
        val old = File(exportDir, "Old.mp3").apply { writeBytes(byteArrayOf(1)) }

        repository().exportOpenTrack(listOf(ExportItem("A.mp3"), ExportItem("B.mp3", 0f, 0.5f))) {}

        assertFalse(old.exists())
        assertEquals(setOf("A.mp3", "B.mp3"), exportDir.list()?.toSet())
    }

    @Test
    fun `when a batch exports then the sound settings are taken once for all its files and let go`() = runTest {
        repository().exportOpenTrack(listOf(ExportItem("A.mp3"), ExportItem("B.mp3", 0f, 0.5f), ExportItem("C.mp3", 0.5f, 1f))) {}

        assertEquals(1, renderer.batches)
        assertEquals(listOf(true, true, true), renderer.beginsInBatch)
        assertFalse(renderer.isInBatch)
    }

    @Test
    fun `when a batch fails or is cancelled then its settings are let go too`() = runTest {
        renderer.canBegin = { index -> index == 0 }
        repository().exportOpenTrack(listOf(ExportItem("A.mp3"), ExportItem("B.mp3", 0f, 0.5f))) {}
        assertFalse(renderer.isInBatch)

        renderer.canBegin = { true }
        renderer.error = { CancellationException("cancelled") }
        runCatching { repository().exportOpenTrack(single("A.mp3")) {} }
        assertEquals(2, renderer.batches)
        assertFalse(renderer.isInBatch)
    }

    @Test
    fun `when the sound settings cannot be taken then nothing is rendered`() = runTest {
        renderer.canBeginBatch = false

        val result = repository().exportOpenTrack(single("Song.mp3")) {}

        assertTrue(result is ExportResult.Failure)
        assertTrue(renderer.begins.isEmpty())
        assertFalse(renderer.isInBatch)
        assertEquals("settings", logger.at('E').single().properties["stage"])
    }

    @Test
    fun `when the copy fails then saving returns false`() = runTest {
        documents.error = IOException("denied")

        assertFalse(repository().saveTo(ExportedFile("/x.mp3", "x.mp3"), "content://doc"))
    }

    @Test
    fun `when the copy succeeds then saving returns true`() = runTest {
        assertTrue(repository().saveTo(ExportedFile("/x.mp3", "x.mp3"), "content://doc"))
        assertEquals(listOf("content://doc"), documents.written)
    }

    @Test
    fun `when saving a batch to a folder then every file lands there without replacing one`() = runTest {
        val folder = temp.newFolder("chosen")
        File(folder, "Song - 01 Intro.mp3").writeBytes(byteArrayOf(9))
        val files = listOf(ExportedFile("/e/Song 100%.mp3", "Song 100%.mp3"), ExportedFile("/e/Song - 01 Intro.mp3", "Song - 01 Intro.mp3"))

        assertTrue(repository().saveAllTo(files, folder.path))

        assertEquals(
            listOf(File(folder, "Song 100%.mp3").path, File(folder, "Song - 01 Intro (2).mp3").path),
            documents.written,
        )
    }

    @Test
    fun `when one copy of a batch fails then saving returns false`() = runTest {
        documents.error = IOException("disk full")

        assertFalse(repository().saveAllTo(listOf(ExportedFile("/e/a.mp3", "a.mp3")), temp.root.path))
    }

    /** Two steps per file (0.5, then 1); [canBegin] and [error] get the index of the file. */
    private class FakeOfflineRenderer : OfflineRenderer {
        var canBegin: (Int) -> Boolean = { true }
        var writesAudio = true
        var error: (Int) -> Exception? = { null }
        /** False: the engine stopped early (a write failed) and reports the file as cut short. */
        var completes: (Int) -> Boolean = { true }
        val begins = mutableListOf<Triple<String, Float, Float>>()
        var ends = 0
        var canBeginBatch = true
        var batches = 0
        var isInBatch = false
        /** For each [begin]: whether it rendered with a batch's settings. */
        val beginsInBatch = mutableListOf<Boolean>()
        val isEnded get() = ends > 0
        private var output: File? = null
        private val steps = ArrayDeque<Float>()

        override fun beginBatch(): Boolean {
            batches++
            isInBatch = canBeginBatch
            return canBeginBatch
        }

        override fun endBatch() {
            isInBatch = false
        }

        override fun begin(outputPath: String, start: Float, end: Float): Boolean {
            val index = begins.size
            begins += Triple(outputPath, start, end)
            beginsInBatch += isInBatch
            output = File(outputPath)
            steps.clear()
            steps += listOf(0.5f, 1f)
            return canBegin(index)
        }

        override fun renderNext(): Float? {
            error(begins.size - 1)?.let { throw it }
            if (writesAudio) output?.appendBytes(byteArrayOf(1))
            return steps.removeFirstOrNull()
        }

        override fun end(): Boolean {
            ends++
            return completes(begins.size - 1)
        }
    }

    private class FakeDocumentDataSource : DocumentDataSource {
        var error: IOException? = null
        val written = mutableListOf<String>()
        override fun displayName(contentUri: String) = error("unused")
        override fun copy(contentUri: String, target: File) = error("unused")
        override fun open(contentUri: String): java.io.InputStream = error("unused")
        override fun write(source: File, contentUri: String) {
            error?.let { throw it }
            written += contentUri
        }
    }
}
