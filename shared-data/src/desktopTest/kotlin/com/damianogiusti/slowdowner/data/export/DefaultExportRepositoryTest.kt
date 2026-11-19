package com.damianogiusti.slowdowner.data.export

import com.damianogiusti.slowdowner.data.library.DocumentDataSource
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.export.RenderRange
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

    private val trackDir get() = File(temp.root, "renders")
    private val whole = RenderRange.WHOLE_FILE
    private val loop = RenderRange(0.2f, 0.4f)
    private val wholeExport = mapOf("format" to "mp3", "part" to "whole", "sound" to "current")

    private fun repository(exportDir: File = File(temp.root, "exports")) =
        DefaultExportRepository(renderer, documents, exportDir, trackDir, UnconfinedTestDispatcher(), logger)

    @Test
    fun `when the render completes then the file is returned and progress reported`() = runTest {
        val progress = mutableListOf<Float>()

        val result = repository().exportOpenTrack("Song 75%.mp3", whole) { progress += it }

        val file = (result as ExportResult.Success).file
        assertEquals("Song 75%.mp3", file.name)
        assertTrue(File(file.path).length() > 0)
        assertEquals(listOf(0.5f, 1f), progress)
        assertTrue(renderer.isEnded)
        assertEquals(wholeExport, logger.at('I').single().properties)
        assertEquals(RenderRequest(whole, RenderSound.CURRENT), renderer.request)
    }

    @Test
    fun `when exporting then older exports are deleted`() = runTest {
        val exportDir = File(temp.root, "exports").apply { mkdirs() }
        val old = File(exportDir, "Old.mp3").apply { writeBytes(byteArrayOf(1)) }

        repository(exportDir).exportOpenTrack("New.mp3", whole) {}

        assertFalse(old.exists())
    }

    @Test
    fun `an export of the loop renders only the loop with the current sound`() = runTest {
        repository().exportOpenTrack("Song loop.mp3", loop) {}

        assertEquals(RenderRequest(loop, RenderSound.CURRENT), renderer.request)
        assertEquals("range", logger.at('I').single().properties["part"])
    }

    @Test
    fun `a render for the library uses the original sound outside the exports`() = runTest {
        val result = repository().renderOriginal(loop) {}

        val file = (result as ExportResult.Success).file
        assertEquals(RenderRequest(loop, RenderSound.ORIGINAL), renderer.request)
        assertEquals(trackDir, File(file.path).parentFile)
        assertEquals("original", logger.at('I').single().properties["sound"])
    }

    @Test
    fun `an export does not delete a render waiting for the library`() = runTest {
        val rendered = (repository().renderOriginal(loop) {} as ExportResult.Success).file

        repository().exportOpenTrack("Song.mp3", whole) {}

        assertTrue(File(rendered.path).exists())
    }

    @Test
    fun `a render for the library replaces one left behind`() = runTest {
        val left = File(trackDir.apply { mkdirs() }, "old.mp3").apply { writeBytes(byteArrayOf(1)) }

        repository().renderOriginal(loop) {}

        assertFalse(left.exists())
    }

    @Test
    fun `when the engine cannot start then the result is Failure`() = runTest {
        renderer.canBegin = false

        val result = repository().exportOpenTrack("Song.mp3", whole) {}

        assertTrue(result is ExportResult.Failure)
        assertFalse(renderer.isEnded)
        assertEquals(wholeExport + ("stage" to "begin"), logger.at('E').single().properties)
    }

    @Test
    fun `when the render fails then the partial file is deleted`() = runTest {
        renderer.error = IllegalStateException("native")

        val result = repository().exportOpenTrack("Song.mp3", whole) {}

        assertTrue(result is ExportResult.Failure)
        assertTrue(renderer.isEnded)
        assertFalse(File(temp.root, "exports/Song.mp3").exists())
        assertEquals(renderer.error, logger.at('E').single().throwable)
    }

    @Test
    fun `when the render is cancelled then the partial file is deleted`() = runTest {
        renderer.error = CancellationException("cancelled")

        runCatching { repository().exportOpenTrack("Song.mp3", whole) {} }

        assertTrue(renderer.isEnded)
        assertFalse(File(temp.root, "exports/Song.mp3").exists())
    }

    @Test
    fun `when the render writes nothing then the result is Failure`() = runTest {
        renderer.writesAudio = false

        assertTrue(repository().exportOpenTrack("Song.mp3", whole) {} is ExportResult.Failure)
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

    private class FakeOfflineRenderer : OfflineRenderer {
        var canBegin = true
        var writesAudio = true
        var error: Exception? = null
        var isEnded = false
        var request: RenderRequest? = null
        private var output: File? = null
        private val steps = ArrayDeque(listOf(0.5f, 1f))

        override fun begin(outputPath: String, request: RenderRequest): Boolean {
            output = File(outputPath)
            this.request = request
            return canBegin
        }

        override fun renderNext(): Float? {
            error?.let { throw it }
            if (writesAudio) output?.appendBytes(byteArrayOf(1))
            return steps.removeFirstOrNull()
        }

        override fun end() { isEnded = true }
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
