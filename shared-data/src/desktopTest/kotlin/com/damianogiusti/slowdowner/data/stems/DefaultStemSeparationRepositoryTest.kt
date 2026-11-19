package com.damianogiusti.slowdowner.data.stems

import androidx.sqlite.SQLiteException
import com.damianogiusti.slowdowner.data.library.LibraryTrackDto
import com.damianogiusti.slowdowner.domain.stems.SeparationFailureReason
import com.damianogiusti.slowdowner.domain.stems.SeparationJobState
import com.damianogiusti.slowdowner.domain.stems.SeparationResult
import com.damianogiusti.slowdowner.domain.stems.SeparationStart
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemModelRepository
import com.damianogiusti.slowdowner.domain.stems.StemModelState
import com.damianogiusti.slowdowner.domain.stems.ModelDownloadResult
import com.damianogiusti.slowdowner.fake.FakeLibraryStore
import com.damianogiusti.slowdowner.fake.FakeStemSeparatorDataSource
import com.damianogiusti.slowdowner.fake.FakeStemsStore
import com.damianogiusti.slowdowner.fake.RecordingLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStemSeparationRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val stemsRoot by lazy { File(folder.root, "stems") }
    private val model = SeparationModel("model", listOf(File("graph.xml"), File("weights.bin")))
    private val logger = RecordingLogger()
    private val models = FakeModelRepository()
    private val library by lazy {
        FakeLibraryStore(folder.root, listOf(LibraryTrackDto("track", "Song", "/library/track.mp3", null, 0L)))
    }
    private val store by lazy { FakeStemsStore(root = stemsRoot) }

    /** Writes two stem files, as the real separator does, and finishes. */
    private val succeeds: suspend (SeparationRequest, (Float) -> Unit) -> SeparationOutcome = { request, _ ->
        File(request.outputDir, "vocals.mp3").writeText("v")
        File(request.outputDir, "drums.mp3").writeText("d")
        SeparationOutcome.Separated(mapOf(StemKind.VOCALS to "vocals.mp3", StemKind.DRUMS to "drums.mp3"))
    }
    private val separator = FakeStemSeparatorDataSource(succeeds)

    // Not backgroundScope: advanceUntilIdle skips background work, and this is the job under test.
    private fun TestScope.repository(): DefaultStemSeparationRepository =
        DefaultStemSeparationRepository(
            separator, library, store, models, model,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            StandardTestDispatcher(testScheduler), logger,
        )

    private suspend fun DefaultStemSeparationRepository.finalState() = observe().first { it is SeparationJobState.Finished }

    @Test
    fun `a separation reports progress then saves the stems as full track with gain one`() = runTest {
        separator.behavior = { request, progress ->
            progress(0.25f)
            progress(0.5f)
            progress(1f)
            succeeds(request, progress)
        }
        val repository = repository()
        val seen = mutableListOf<SeparationJobState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observe().collect { seen += it } }

        assertEquals(SeparationStart.Started, repository.start("track"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                SeparationJobState.Idle,
                SeparationJobState.Running("track", 0f),
                SeparationJobState.Running("track", 0.25f),
                SeparationJobState.Running("track", 0.5f),
                SeparationJobState.Running("track", 1f),
                SeparationJobState.Finished("track", SeparationResult.Separated),
            ),
            seen,
        )
        val saved = store.tracks.value.getValue("track")
        assertEquals("model", saved.model)
        assertEquals("FULL_TRACK", saved.mode)
        assertEquals(
            listOf(StemFileDto("VOCALS", "vocals.mp3", 1f), StemFileDto("DRUMS", "drums.mp3", 1f)),
            saved.stems,
        )
        assertEquals("/library/track.mp3", separator.requests.single().inputPath)
        assertEquals(model.files, separator.requests.single().modelFiles)
    }

    @Test
    fun `progress is passed on once per whole percent`() = runTest {
        separator.behavior = { request, progress ->
            listOf(0.001f, 0.004f, 0.501f, 0.502f, 0.509f, 0.51f).forEach(progress)
            succeeds(request, progress)
        }
        val repository = repository()
        val seen = mutableListOf<Float>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observe().collect { if (it is SeparationJobState.Running) seen += it.progress }
        }

        repository.start("track")
        advanceUntilIdle()

        assertEquals(listOf(0f, 0.5f, 0.51f), seen)
    }

    @Test
    fun `starting while a job runs gives busy and does not disturb it`() = runTest {
        val release = CompletableDeferred<Unit>()
        separator.behavior = { request, progress -> release.await(); succeeds(request, progress) }
        val repository = repository()
        repository.start("track")
        advanceUntilIdle()

        assertEquals(SeparationStart.Busy, repository.start("other"))
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(SeparationJobState.Finished("track", SeparationResult.Separated), repository.finalState())
        assertEquals(1, separator.requests.size)
    }

    @Test
    fun `without the model nothing starts`() = runTest {
        models.state.value = StemModelState.NotDownloaded
        val repository = repository()

        assertEquals(SeparationStart.ModelMissing, repository.start("track"))
        assertEquals(SeparationJobState.Idle, repository.observe().first())
        assertTrue(separator.requests.isEmpty())
    }

    @Test
    fun `an unknown track does not start`() = runTest {
        val repository = repository()

        assertEquals(SeparationStart.TrackMissing, repository.start("gone"))
        assertEquals(SeparationJobState.Idle, repository.observe().first())
        assertTrue(separator.requests.isEmpty())
    }

    @Test
    fun `a track that cannot be decoded is reported as unreadable`() = runTest {
        assertFailure(SeparationFailure.DECODE_FAILED, SeparationFailureReason.UNREADABLE_TRACK)
    }

    @Test
    fun `a track that is too long is reported as too long`() = runTest {
        assertFailure(SeparationFailure.TOO_LONG, SeparationFailureReason.TOO_LONG)
    }

    @Test
    fun `a model failure is reported as a model error`() = runTest {
        assertFailure(SeparationFailure.MODEL_FAILED, SeparationFailureReason.MODEL_ERROR)
    }

    @Test
    fun `a write failure is reported as a write error`() = runTest {
        assertFailure(SeparationFailure.WRITE_FAILED, SeparationFailureReason.WRITE_ERROR)
    }

    private suspend fun TestScope.assertFailure(failure: SeparationFailure, reason: SeparationFailureReason) {
        separator.behavior = { _, _ -> SeparationOutcome.Failed(failure) }
        val repository = repository()

        repository.start("track")
        advanceUntilIdle()

        assertEquals(SeparationJobState.Finished("track", SeparationResult.Failed(reason)), repository.finalState())
        assertTrue(store.tracks.value.isEmpty())
        assertFalse("a run that left nothing leaves no folder", File(stemsRoot, "track").exists())
        assertEquals(listOf(reason.name), logger.at('W').map { it.properties["reason"] })
    }

    @Test
    fun `cancelling stops the job and adds nothing`() = runTest {
        separator.behavior = { _, progress -> progress(0.3f); awaitCancellation() }
        val repository = repository()
        repository.start("track")
        advanceUntilIdle()

        repository.cancel()

        assertEquals(SeparationJobState.Finished("track", SeparationResult.Cancelled), repository.observe().first())
        assertTrue(store.tracks.value.isEmpty())
        assertFalse(File(stemsRoot, "track").exists())
    }

    @Test
    fun `cancelling when nothing runs does nothing`() = runTest {
        val repository = repository()

        repository.cancel()

        assertEquals(SeparationJobState.Idle, repository.observe().first())
    }

    @Test
    fun `a track removed during the job leaves no rows and no folder`() = runTest {
        store.addError = SQLiteException("FOREIGN KEY constraint failed")
        val repository = repository()

        repository.start("track")
        advanceUntilIdle()

        assertEquals(
            SeparationJobState.Finished("track", SeparationResult.Failed(SeparationFailureReason.TRACK_REMOVED)),
            repository.finalState(),
        )
        assertTrue(store.tracks.value.isEmpty())
        assertEquals(listOf("track"), store.deleted)
        assertFalse(File(stemsRoot, "track").exists())
    }

    @Test
    fun `a failure to save the rows is a write error and removes the files`() = runTest {
        store.addError = IOException("disk full")
        val repository = repository()

        repository.start("track")
        advanceUntilIdle()

        assertEquals(
            SeparationJobState.Finished("track", SeparationResult.Failed(SeparationFailureReason.WRITE_ERROR)),
            repository.finalState(),
        )
        assertFalse(File(stemsRoot, "track").exists())
    }

    @Test
    fun `an unexpected exception becomes an unexpected failure and an error line`() = runTest {
        separator.behavior = { _, _ -> throw IllegalStateException("boom") }
        val repository = repository()

        repository.start("track")
        advanceUntilIdle()

        assertEquals(
            SeparationJobState.Finished("track", SeparationResult.Failed(SeparationFailureReason.UNEXPECTED)),
            repository.finalState(),
        )
        assertEquals(1, logger.at('E').size)
    }

    @Test
    fun `the outcome stays until acknowledged and a new start replaces it`() = runTest {
        val repository = repository()
        repository.start("track")
        advanceUntilIdle()
        assertEquals(SeparationJobState.Finished("track", SeparationResult.Separated), repository.observe().first())

        repository.acknowledge()
        assertEquals(SeparationJobState.Idle, repository.observe().first())

        repository.start("track")
        advanceUntilIdle()
        repository.start("track")
        assertEquals(SeparationJobState.Running("track", 0f), repository.observe().first())
        advanceUntilIdle()
    }

    @Test
    fun `acknowledging while a job runs keeps it`() = runTest {
        separator.behavior = { _, _ -> awaitCancellation() }
        val repository = repository()
        repository.start("track")
        advanceUntilIdle()

        repository.acknowledge()

        assertEquals(SeparationJobState.Running("track", 0f), repository.observe().first())
    }

    @Test
    fun `shutdown cancels the job and waits for it`() = runTest {
        separator.behavior = { _, _ -> awaitCancellation() }
        val repository = repository()
        repository.start("track")
        advanceUntilIdle()

        repository.shutdown(5.seconds)

        assertEquals(SeparationJobState.Finished("track", SeparationResult.Cancelled), repository.observe().first())
        assertTrue(logger.at('W').isEmpty())
    }

    @Test
    fun `shutdown gives up after the timeout when the job does not stop`() = runTest {
        separator.behavior = { _, _ -> withContext(NonCancellable) { delay(60.seconds); SeparationOutcome.Failed(SeparationFailure.MODEL_FAILED) } }
        val repository = repository()
        repository.start("track")
        runCurrent() // not advanceUntilIdle: that would run the job's 60 s delay out
        val before = currentTime

        repository.shutdown(5.seconds)

        assertEquals(5_000L, currentTime - before)
        assertEquals(listOf("Stem separation did not stop in time"), logger.at('W').map { it.message })
    }

    @Test
    fun `logs carry no track data`() = runTest {
        val repository = repository()
        repository.start("track")
        advanceUntilIdle()

        val everything = logger.lines.joinToString { it.message + it.properties }
        assertFalse(everything.contains("track"))
        assertFalse(everything.contains("Song"))
        assertEquals(listOf("Stem separation started", "Stem separation finished"), logger.at('I').map { it.message })
    }

    private class FakeModelRepository : StemModelRepository {
        val state = MutableStateFlow<StemModelState>(StemModelState.Ready)
        override fun observe(): Flow<StemModelState> = state
        override suspend fun download(onProgress: (Float) -> Unit): ModelDownloadResult = error("unused")
        override suspend fun delete() = error("unused")
    }
}
