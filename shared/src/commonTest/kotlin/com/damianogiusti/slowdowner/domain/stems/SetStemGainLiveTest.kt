package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.StemSource
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeStemMixer
import com.damianogiusti.slowdowner.fake.FakeStemsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetStemGainLiveTest {

    private val song = LibraryTrack("1", "Song", "/song.m4a", null, 0)
    private val other = LibraryTrack("2", "Other", "/other.m4a", null, 0)
    private val stems = FakeStemsRepository(
        listOf(
            TrackStems("1", "model", TrackMode.STEMS, StemMix(mapOf(StemKind.VOCALS to 1f, StemKind.DRUMS to 1f))),
            TrackStems("2", "model", TrackMode.STEMS, StemMix(mapOf(StemKind.VOCALS to 1f))),
        ),
    )
    private val sources = FakeAudioSourceRepository()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val mixer = FakeStemMixer()
    private val setGain = SetStemGainUseCase(stems, mixer, sources, nowPlaying)
    private val stemsSource = AudioSource.Stems(
        listOf(StemSource(StemKind.VOCALS, "/v.mp3"), StemSource(StemKind.DRUMS, "/d.mp3")),
    )

    private fun gainOf(trackId: String, stem: StemKind) = stems.stems.value[trackId]?.mix?.gains?.get(stem)

    @Test
    fun `when the track plays as stems then the gain is saved and applied live`() = runTest {
        nowPlaying.set(song)
        sources.open(stemsSource)

        setGain("1", StemKind.DRUMS, 0.25f)

        assertEquals(listOf(StemKind.DRUMS to 0.25f), mixer.calls)
        assertEquals(0.25f, gainOf("1", StemKind.DRUMS))
    }

    @Test
    fun `when the gain is out of range then the mixer gets it clamped`() = runTest {
        nowPlaying.set(song)
        sources.open(stemsSource)

        setGain("1", StemKind.DRUMS, 1.5f)

        assertEquals(listOf(StemKind.DRUMS to 1f), mixer.calls)
    }

    @Test
    fun `when the gain is not a number then nothing is saved or applied`() = runTest {
        nowPlaying.set(song)
        sources.open(stemsSource)

        setGain("1", StemKind.DRUMS, Float.NaN)

        assertTrue(mixer.calls.isEmpty())
        assertEquals(1f, gainOf("1", StemKind.DRUMS))
    }

    @Test
    fun `when the open track plays the original then the gain is only saved`() = runTest {
        nowPlaying.set(song)
        sources.open(AudioSource.LocalFile("/song.m4a"))

        setGain("1", StemKind.DRUMS, 0.25f)

        assertTrue(mixer.calls.isEmpty())
        assertEquals(0.25f, gainOf("1", StemKind.DRUMS))
    }

    @Test
    fun `when another track plays as stems then the gain is only saved`() = runTest {
        nowPlaying.set(other)
        sources.open(AudioSource.Stems(listOf(StemSource(StemKind.VOCALS, "/v.mp3"))))

        setGain("1", StemKind.VOCALS, 0.25f)

        assertTrue(mixer.calls.isEmpty())
        assertEquals(0.25f, gainOf("1", StemKind.VOCALS))
        assertEquals(1f, gainOf("2", StemKind.VOCALS))
    }

    @Test
    fun `when nothing is open then the gain is only saved`() = runTest {
        setGain("1", StemKind.VOCALS, 0.5f)

        assertTrue(mixer.calls.isEmpty())
        assertEquals(0.5f, gainOf("1", StemKind.VOCALS))
    }

    @Test
    fun `when the open stems lack the stem then it is not applied`() = runTest {
        nowPlaying.set(song)
        sources.open(AudioSource.Stems(listOf(StemSource(StemKind.VOCALS, "/v.mp3"))))

        setGain("1", StemKind.DRUMS, 0.25f)

        assertTrue(mixer.calls.isEmpty())
    }
}
