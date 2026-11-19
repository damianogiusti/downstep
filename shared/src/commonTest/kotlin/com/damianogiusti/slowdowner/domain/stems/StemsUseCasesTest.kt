package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeStemMixer
import com.damianogiusti.slowdowner.fake.FakeStemsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StemsUseCasesTest {

    private val song = TrackStems(
        trackId = "a",
        model = "model",
        mode = TrackMode.STEMS,
        mix = StemMix(mapOf(StemKind.VOCALS to 1f, StemKind.DRUMS to 0.5f)),
    )
    private val repository = FakeStemsRepository(listOf(song))
    private val setGain = SetStemGainUseCase(repository, FakeStemMixer(), FakeAudioSourceRepository(), InMemoryNowPlayingRepository())

    @Test
    fun `observing a separated track gives its stems`() = runTest {
        assertEquals(song, ObserveTrackStemsUseCase(repository)("a").first())
    }

    @Test
    fun `observing a track that is not separated gives null`() = runTest {
        assertNull(ObserveTrackStemsUseCase(repository)("b").first())
    }

    @Test
    fun `observing separated tracks gives their ids`() = runTest {
        assertEquals(setOf("a"), ObserveSeparatedTracksUseCase(repository)().first())
    }

    @Test
    fun `setting the mode changes it for the track`() = runTest {
        SetTrackModeUseCase(repository)("a", TrackMode.FULL_TRACK)

        assertEquals(TrackMode.FULL_TRACK, repository.stems.value["a"]?.mode)
    }

    @Test
    fun `setting the mode of a track that is not separated does nothing`() = runTest {
        SetTrackModeUseCase(repository)("b", TrackMode.FULL_TRACK)

        assertEquals(setOf("a"), repository.stems.value.keys)
    }

    @Test
    fun `setting a gain changes only that stem`() = runTest {
        setGain("a", StemKind.DRUMS, 0.25f)

        assertEquals(mapOf(StemKind.VOCALS to 1f, StemKind.DRUMS to 0.25f), repository.stems.value["a"]?.mix?.gains)
    }

    @Test
    fun `setting the gain of a stem the track lacks does nothing`() = runTest {
        setGain("a", StemKind.PIANO, 0.25f)

        assertEquals(song, repository.stems.value["a"])
    }

    @Test
    fun `deleting stems removes the track from the separated ones`() = runTest {
        DeleteTrackStemsUseCase(repository)("a")

        assertEquals(emptySet(), ObserveSeparatedTracksUseCase(repository)().first())
        assertEquals(listOf("a"), repository.deleted)
    }
}
