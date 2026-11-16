package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.fake.FakeStemModelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StemModelUseCasesTest {

    private val repository = FakeStemModelRepository()

    @Test
    fun `observing the model gives its state`() = runTest {
        assertEquals(StemModelState.NotDownloaded, ObserveStemModelUseCase(repository)().first())
    }

    @Test
    fun `downloading gives the result and passes the progress on`() = runTest {
        val progress = mutableListOf<Float>()

        val result = DownloadStemModelUseCase(repository)(progress::add)

        assertEquals(ModelDownloadResult.Success, result)
        assertEquals(listOf(0.5f, 1f), progress)
        assertEquals(StemModelState.Ready, repository.state.value)
    }

    @Test
    fun `a failed download gives the failure result`() = runTest {
        repository.result = ModelDownloadResult.Corrupted

        assertEquals(ModelDownloadResult.Corrupted, DownloadStemModelUseCase(repository)({}))
        assertEquals(StemModelState.NotDownloaded, repository.state.value)
    }

    @Test
    fun `deleting the model makes it not downloaded`() = runTest {
        DownloadStemModelUseCase(repository)({})

        DeleteStemModelUseCase(repository)()

        assertEquals(StemModelState.NotDownloaded, repository.state.value)
    }
}
