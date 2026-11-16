package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.data.library.HttpStatusException
import com.damianogiusti.slowdowner.domain.stems.ModelDownloadResult
import com.damianogiusti.slowdowner.domain.stems.StemModelState
import com.damianogiusti.slowdowner.fake.FakeHttpFileDataSource
import com.damianogiusti.slowdowner.fake.RecordingLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStemModelRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val first = ByteArray(10) { it.toByte() }
    private val second = ByteArray(30) { (it * 3).toByte() }
    private val spec = StemModelSpec(
        id = "model",
        files = listOf(
            ModelFileSpec("a.bin", "https://host/a", first.size.toLong(), sha(first)),
            ModelFileSpec("b.bin", "https://host/b", second.size.toLong(), sha(second)),
        ),
    )
    private val http = FakeHttpFileDataSource(mapOf("https://host/a" to first, "https://host/b" to second))
    private val logger = RecordingLogger()
    private val modelsDir by lazy { File(folder.root, "models") }
    private val modelDir by lazy { File(modelsDir, "model") }

    private fun repository(spec: StemModelSpec = this.spec, http: FakeHttpFileDataSource = this.http) =
        DefaultStemModelRepository(http, spec, modelsDir, UnconfinedTestDispatcher(), logger)

    @Test
    fun `download writes every file and reports success`() = runTest {
        val result = repository().download {}

        assertEquals(ModelDownloadResult.Success, result)
        assertTrue(File(modelDir, "a.bin").readBytes().contentEquals(first))
        assertTrue(File(modelDir, "b.bin").readBytes().contentEquals(second))
        assertEquals(listOf("model"), logger.at('I').map { it.properties["model"] })
    }

    @Test
    fun `progress goes over the bytes of all files and ends at one`() = runTest {
        val progress = mutableListOf<Float>()

        repository().download { progress += it }

        assertEquals(1f, progress.last(), 0f)
        assertEquals(progress.sorted(), progress)
        // Half of the first file is 5 of 40 bytes.
        assertTrue(progress.any { it == 5f / 40f })
        // Second file half done: 10 + 15 of 40.
        assertTrue(progress.any { it == 25f / 40f })
    }

    @Test
    fun `observe is not downloaded before the download and ready after`() = runTest {
        val repository = repository()
        assertEquals(StemModelState.NotDownloaded, repository.observe().first())

        repository.download {}

        assertEquals(StemModelState.Ready, repository.observe().first())
    }

    @Test
    fun `an open observer sees the state change after download and delete`() = runTest {
        val repository = repository()
        val seen = mutableListOf<StemModelState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repository.observe().collect { seen += it } }

        repository.download {}
        repository.delete()
        job.cancel()

        assertEquals(listOf(StemModelState.NotDownloaded, StemModelState.Ready, StemModelState.NotDownloaded), seen)
    }

    @Test
    fun `observe is ready when files from an earlier run have the expected size`() = runTest {
        writeModel()

        assertEquals(StemModelState.Ready, repository().observe().first())
    }

    @Test
    fun `observe does not check the hash`() = runTest {
        modelDir.mkdirs()
        File(modelDir, "a.bin").writeBytes(ByteArray(first.size) { 9 })
        File(modelDir, "b.bin").writeBytes(second)

        assertEquals(StemModelState.Ready, repository().observe().first())
    }

    @Test
    fun `observe is not downloaded when a file has the wrong size`() = runTest {
        writeModel()
        File(modelDir, "b.bin").writeBytes(second.copyOf(5))

        assertEquals(StemModelState.NotDownloaded, repository().observe().first())
    }

    @Test
    fun `a model that is already there is not downloaded again`() = runTest {
        writeModel()

        assertEquals(ModelDownloadResult.Success, repository().download {})

        assertTrue(http.downloadedUrls.isEmpty())
    }

    @Test
    fun `only the missing file is downloaded`() = runTest {
        writeModel()
        File(modelDir, "b.bin").delete()

        repository().download {}

        assertEquals(listOf("https://host/b"), http.downloadedUrls)
    }

    @Test
    fun `a file of the right size with the wrong content is downloaded again`() = runTest {
        writeModel()
        File(modelDir, "a.bin").writeBytes(ByteArray(first.size) { 9 })

        assertEquals(ModelDownloadResult.Success, repository().download {})

        assertEquals(listOf("https://host/a"), http.downloadedUrls)
        assertTrue(File(modelDir, "a.bin").readBytes().contentEquals(first))
    }

    @Test
    fun `a partial file from an earlier download is replaced`() = runTest {
        modelDir.mkdirs()
        File(modelDir, "a.bin").writeBytes(first.copyOf(4))
        File(modelDir, "a.bin.part").writeBytes(first.copyOf(2))

        assertEquals(ModelDownloadResult.Success, repository().download {})

        assertTrue(File(modelDir, "a.bin").readBytes().contentEquals(first))
        assertEquals(StemModelState.Ready, repository().observe().first())
    }

    @Test
    fun `a wrong hash gives corrupted and deletes the file`() = runTest {
        val tampered = spec.copy(files = listOf(spec.files[0].copy(sha256 = sha(byteArrayOf(1)))))

        val result = repository(tampered).download {}

        assertEquals(ModelDownloadResult.Corrupted, result)
        assertFalse(File(modelDir, "a.bin").exists())
        assertEquals(StemModelState.NotDownloaded, repository(tampered).observe().first())
        assertEquals("a.bin", logger.at('W').single().properties["file"])
    }

    @Test
    fun `a wrong size gives corrupted and deletes the file`() = runTest {
        val tampered = spec.copy(files = listOf(spec.files[0].copy(sizeBytes = 11)))

        val result = repository(tampered).download {}

        assertEquals(ModelDownloadResult.Corrupted, result)
        assertFalse(File(modelDir, "a.bin").exists())
    }

    @Test
    fun `an io error gives network error`() = runTest {
        http.error = IOException("offline")

        assertEquals(ModelDownloadResult.NetworkError, repository().download {})
        assertEquals(StemModelState.NotDownloaded, repository().observe().first())
        assertTrue(logger.at('E').isEmpty())
    }

    @Test
    fun `a server error gives network error`() = runTest {
        http.error = HttpStatusException(503)

        assertEquals(ModelDownloadResult.NetworkError, repository().download {})
    }

    @Test
    fun `a refused request gives failure and is logged as an error`() = runTest {
        val refusal = HttpStatusException(404)
        http.error = refusal

        assertEquals(ModelDownloadResult.Failure(refusal), repository().download {})
        assertEquals(refusal, logger.at('E').single().throwable)
    }

    @Test
    fun `an unexpected exception gives failure and is logged as an error`() = runTest {
        val bug = IllegalStateException("bug")
        http.error = bug

        assertEquals(ModelDownloadResult.Failure(bug), repository().download {})
        assertEquals(bug, logger.at('E').single().throwable)
    }

    @Test
    fun `cancellation is rethrown and leaves the state refreshed`() = runTest {
        http.duringDownload = { throw CancellationException("stop") }
        val repository = repository()

        try {
            repository.download {}
            fail("Expected a cancellation")
        } catch (_: CancellationException) {
        }

        assertEquals(StemModelState.NotDownloaded, repository.observe().first())
        assertTrue(logger.lines.isEmpty())
    }

    @Test
    fun `delete removes the files and the folder`() = runTest {
        writeModel()
        File(modelDir, "a.bin.part").writeBytes(byteArrayOf(1))
        val repository = repository()

        repository.delete()

        assertFalse(modelDir.exists())
        assertEquals(StemModelState.NotDownloaded, repository.observe().first())
    }

    @Test
    fun `delete without a model does nothing`() = runTest {
        repository().delete()

        assertFalse(modelDir.exists())
    }

    @Test
    fun `delete does not follow a link inside the folder`() = runTest {
        writeModel()
        val outside = folder.newFolder("outside")
        val kept = File(outside, "kept.txt").apply { writeText("keep") }
        Files.createSymbolicLink(File(modelDir, "link").toPath(), outside.toPath())

        repository().delete()

        assertTrue(kept.exists())
        assertTrue(modelDir.exists())
    }

    @Test
    fun `an id or a file name that could leave the models folder is rejected`() {
        val bad = listOf(
            spec.copy(id = ".."),
            spec.copy(id = "a/b"),
            spec.copy(files = listOf(spec.files[0].copy(name = "../x"))),
            spec.copy(files = listOf(spec.files[0].copy(name = "a\\b"))),
        )
        bad.forEach { candidate ->
            try {
                repository(candidate)
                fail("Expected $candidate to be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    private fun writeModel() {
        modelDir.mkdirs()
        File(modelDir, "a.bin").writeBytes(first)
        File(modelDir, "b.bin").writeBytes(second)
    }

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
