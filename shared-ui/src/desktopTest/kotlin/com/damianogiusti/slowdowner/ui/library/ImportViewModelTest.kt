package com.damianogiusti.slowdowner.ui.library

import com.damianogiusti.slowdowner.domain.library.FindImportableUrlUseCase
import com.damianogiusti.slowdowner.domain.library.ImportDocumentUseCase
import com.damianogiusti.slowdowner.domain.library.ImportProgress
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.library.ImportTrackFromUrlUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `download progress is shown in whole-percent steps`() = runTest {
        val repository = ChunkedDownload(chunks = 5_000)
        val viewModel = ImportViewModel(
            FindImportableUrlUseCase(repository),
            ImportTrackFromUrlUseCase(repository),
            ImportDocumentUseCase(repository),
        )
        val fractions = mutableListOf<Float?>()
        backgroundScope.launch {
            viewModel.state.collect { (it as? ImportUiState.Downloading)?.let { state -> fractions += state.fraction } }
        }

        viewModel.onTextShared("https://youtu.be/abc")
        advanceUntilIdle()

        // 0 % to 100 %: one state per step, not one per chunk.
        assertEquals(101, fractions.size)
        assertEquals(1f, fractions.last())
    }

    private class ChunkedDownload(private val chunks: Int) : LibraryRepository {
        override fun observeTracks(): Flow<List<LibraryTrack>> = emptyFlow()
        override suspend fun remove(id: String) = Unit
        override fun findImportableUrl(text: String?) = text
        override suspend fun importDocument(contentUri: String) = ImportResult.Unavailable

        override suspend fun importFromUrl(url: String, onProgress: (ImportProgress) -> Unit): ImportResult {
            onProgress(ImportProgress.Resolving)
            for (chunk in 0..chunks) {
                onProgress(ImportProgress.Downloading("Song", chunk * 1_000L, chunks * 1_000L))
                // Lets the collector see every state the ViewModel publishes.
                yield()
            }
            return ImportResult.Unavailable
        }
    }
}
