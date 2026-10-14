package com.damianogiusti.slowdowner.data.library

import com.damianogiusti.slowdowner.domain.library.ImportProgress
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.fake.RecordingLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLibraryRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { FakeLibraryStore(folder.root) }
    private val youTube = FakeYouTubeStreamDataSource()
    private val http = FakeHttpFileDataSource()
    private val documents = FakeDocumentDataSource()
    private val containers = FakeMediaContainerDataSource()
    private val logger = RecordingLogger()

    private fun repository() = DefaultLibraryRepository(
        store = store,
        youTube = youTube,
        http = http,
        documents = documents,
        containers = containers,
        dispatcher = UnconfinedTestDispatcher(),
        logger = logger,
        now = { NOW },
        newId = { "fixed" },
    )

    @Test
    fun `importFromUrl downloads the stream and stores the track`() = runTest {
        val progress = mutableListOf<ImportProgress>()

        val result = repository().importFromUrl(URL) { progress += it }

        val track = (result as ImportResult.Success).track
        assertEquals("yt_abc", track.id)
        assertEquals("Song", track.title)
        assertEquals(URL, track.sourceUrl)
        assertTrue(File(track.filePath).exists())
        assertEquals(listOf("https://stream"), http.downloadedUrls)
        assertEquals(ImportProgress.Resolving, progress.first())
        assertEquals(ImportProgress.Downloading("Song", 3, 3), progress.last())
    }

    @Test
    fun `importFromUrl remuxes mp4 audio so the engine can seek it`() = runTest {
        val track = (repository().importFromUrl(URL) {} as ImportResult.Success).track

        assertEquals(listOf(track.filePath), containers.remuxedTargets)
        assertTrue(File(track.filePath).exists())
        assertEquals(listOf(File(track.filePath)), folder.root.listFiles()!!.toList())
    }

    @Test
    fun `a failed remux keeps the original file`() = runTest {
        containers.error = IllegalStateException("bad container")

        val track = (repository().importFromUrl(URL) {} as ImportResult.Success).track

        assertTrue(File(track.filePath).exists())
        assertEquals(listOf(File(track.filePath)), folder.root.listFiles()!!.toList())
    }

    @Test
    fun `importDocument leaves non-mp4 files untouched`() = runTest {
        documents.name = "Take 3.mp3"

        repository().importDocument("content://doc")

        assertTrue(containers.remuxedTargets.isEmpty())
    }

    @Test
    fun `importFromUrl reuses a track already in the library`() = runTest {
        repository().importFromUrl(URL) {}

        val result = repository().importFromUrl(URL) {}

        assertTrue(result is ImportResult.Success)
        assertEquals(1, http.downloadedUrls.size)
    }

    @Test
    fun `importFromUrl maps unavailable streams`() = runTest {
        youTube.error = StreamUnavailableException("gone")

        assertEquals(ImportResult.Unavailable, repository().importFromUrl(URL) {})
    }

    @Test
    fun `importFromUrl maps IO errors to network errors`() = runTest {
        http.error = IOException("offline")

        assertEquals(ImportResult.NetworkError, repository().importFromUrl(URL) {})
        assertTrue(store.tracks.value.isEmpty())
        assertTrue(logger.at('E').isEmpty())
        assertEquals(mapOf("source" to "youtube"), logger.at('W').single().properties)
    }

    @Test
    fun `when import fails for an unknown reason then it is logged as an error with the link`() = runTest {
        val error = IllegalStateException("parser changed")
        youTube.error = error

        assertEquals(ImportResult.Failure(error), repository().importFromUrl(URL) {})
        val line = logger.at('E').single()
        assertEquals(error, line.throwable)
        assertEquals(mapOf("source" to "youtube", "url" to URL), line.properties)
    }

    @Test
    fun `the logged link keeps only the video`() = runTest {
        youTube.error = IllegalStateException("parser changed")

        repository().importFromUrl("https://www.youtube.com/watch?v=abc&list=PLprivate&si=token") {}
        repository().importFromUrl("https://youtu.be/abc?si=token") {}

        assertEquals(
            listOf("https://www.youtube.com/watch?v=abc", "https://youtu.be/abc"),
            logger.at('E').map { it.properties["url"] },
        )
    }

    @Test
    fun `a finished import is logged with source and format only`() = runTest {
        repository().importFromUrl(URL) {}

        assertEquals(mapOf("source" to "youtube", "extension" to "m4a"), logger.at('I').single().properties)
    }

    @Test
    fun `when a document import fails then the log has no path`() = runTest {
        documents.error = IllegalStateException("broken")

        repository().importDocument("file:///Users/someone/Take 3.mp3")

        assertEquals(mapOf("source" to "file"), logger.at('E').single().properties)
    }

    @Test
    fun `importDocument copies the file keeping name and extension`() = runTest {
        documents.name = "Take 3.mp3"

        val track = (repository().importDocument("content://doc") as ImportResult.Success).track

        assertEquals("Take 3", track.title)
        assertTrue(track.filePath.endsWith("local_fixed.mp3"))
        assertEquals(null, track.sourceUrl)
    }

    @Test
    fun `observeTracks returns newest first`() = runTest {
        store.add(LibraryTrackDto("old", "Old", "/a", null, 1))
        store.add(LibraryTrackDto("new", "New", "/b", null, 2))

        val ids = repository().observeTracks().first().map { it.id }

        assertEquals(listOf("new", "old"), ids)
    }

    private class FakeLibraryStore(private val dir: File) : LibraryStore {
        val tracks = MutableStateFlow<List<LibraryTrackDto>>(emptyList())
        override fun observe(): Flow<List<LibraryTrackDto>> = tracks
        override suspend fun find(id: String) = tracks.value.firstOrNull { it.id == id }
        override suspend fun add(track: LibraryTrackDto) {
            tracks.value = tracks.value.filterNot { it.id == track.id } + track
        }
        override suspend fun remove(id: String) {
            tracks.value = tracks.value.filterNot { it.id == id }
        }
        override fun fileFor(id: String, extension: String) = File(dir, "$id.$extension")
    }

    private class FakeYouTubeStreamDataSource : YouTubeStreamDataSource {
        var error: Exception? = null
        override fun findSupportedUrl(text: String?) = text
        override fun resolve(url: String): ResolvedAudioDto {
            error?.let { throw it }
            return ResolvedAudioDto(id = "abc", title = "Song", streamUrl = "https://stream", extension = "m4a")
        }
    }

    private class FakeHttpFileDataSource : HttpFileDataSource {
        var error: Exception? = null
        val downloadedUrls = mutableListOf<String>()
        override suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit) {
            error?.let { throw it }
            downloadedUrls += url
            target.writeBytes(byteArrayOf(1, 2, 3))
            onProgress(3, 3)
        }
    }

    private class FakeDocumentDataSource : DocumentDataSource {
        var name: String? = null
        var error: Exception? = null
        override fun displayName(contentUri: String) = name
        override fun copy(contentUri: String, target: File) {
            error?.let { throw it }
            target.writeBytes(byteArrayOf(1))
        }
        override fun open(contentUri: String): java.io.InputStream = error("unused")
        override fun write(source: File, contentUri: String) = error("unused")
    }

    private class FakeMediaContainerDataSource : MediaContainerDataSource {
        var error: Exception? = null
        val remuxedTargets = mutableListOf<String>()
        override fun remuxToProgressiveMp4(source: File, target: File) {
            error?.let { throw it }
            source.copyTo(target)
            remuxedTargets += target.path
        }
    }

    private companion object {
        const val URL = "https://youtu.be/abc"
        const val NOW = 42L
    }
}
