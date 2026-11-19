package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.fake.FakeStemSeparationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StemSeparationUseCasesTest {

    private val repository = FakeStemSeparationRepository()

    @Test
    fun `starting passes the track on and gives the answer`() = runTest {
        val result = StartStemSeparationUseCase(repository)("track-1")

        assertEquals(SeparationStart.Started, result)
        assertEquals(listOf("track-1"), repository.started)
    }

    @Test
    fun `starting while busy gives busy and starts nothing new`() = runTest {
        repository.startResult = SeparationStart.Busy

        assertEquals(SeparationStart.Busy, StartStemSeparationUseCase(repository)("track-2"))
        assertEquals(SeparationJobState.Idle, repository.state.value)
    }

    @Test
    fun `observing gives the current state first`() = runTest {
        assertEquals(SeparationJobState.Idle, ObserveStemSeparationUseCase(repository)().first())

        StartStemSeparationUseCase(repository)("track-1")

        assertEquals(SeparationJobState.Running("track-1", 0f), ObserveStemSeparationUseCase(repository)().first())
    }

    @Test
    fun `cancelling asks the repository once`() = runTest {
        CancelStemSeparationUseCase(repository)()

        assertEquals(1, repository.cancels)
    }

    @Test
    fun `acknowledging asks the repository once`() = runTest {
        AcknowledgeStemSeparationUseCase(repository)()

        assertEquals(1, repository.acknowledges)
    }
}
