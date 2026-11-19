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

class SetStemMuteAndSoloTest {

    private val song = LibraryTrack("1", "Song", "/song.m4a", null, 0)
    private val other = LibraryTrack("2", "Other", "/other.m4a", null, 0)
    private val stems = FakeStemsRepository(
        listOf(
            TrackStems("1", "model", TrackMode.STEMS, StemMix(mapOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 0.8f))),
            TrackStems("2", "model", TrackMode.STEMS, StemMix(mapOf(StemKind.VOCALS to 1f))),
        ),
    )
    private val sources = FakeAudioSourceRepository()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val mixer = FakeStemMixer()
    private val setMuted = SetStemMutedUseCase(stems, mixer, sources, nowPlaying)
    private val setSoloed = SetStemSoloedUseCase(stems, mixer, sources, nowPlaying)
    private val setGain = SetStemGainUseCase(stems, mixer, sources, nowPlaying)
    private val stemsSource = AudioSource.Stems(
        listOf(StemSource(StemKind.VOCALS, "/v.mp3"), StemSource(StemKind.DRUMS, "/d.mp3")),
    )

    private fun mixOf(trackId: String) = stems.stems.value.getValue(trackId).mix

    private suspend fun playSongAsStems() {
        nowPlaying.set(song)
        sources.open(stemsSource)
    }

    @Test
    fun `when a stem is muted then the flag is saved and the level kept and the mix applied live`() = runTest {
        playSongAsStems()

        setMuted("1", StemKind.DRUMS, true)

        assertEquals(setOf(StemKind.DRUMS), mixOf("1").mutedStems)
        assertEquals(0.8f, mixOf("1").gains[StemKind.DRUMS])
        assertEquals(listOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 0f), mixer.calls)
    }

    @Test
    fun `when a stem is unmuted then it plays at its level again`() = runTest {
        playSongAsStems()
        setMuted("1", StemKind.DRUMS, true)
        mixer.calls.clear()

        setMuted("1", StemKind.DRUMS, false)

        assertTrue(mixOf("1").mutedStems.isEmpty())
        assertEquals(listOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 0.8f), mixer.calls)
    }

    @Test
    fun `when a stem is soloed then the flag is saved and the others are silenced live`() = runTest {
        playSongAsStems()

        setSoloed("1", StemKind.VOCALS, true)

        assertEquals(setOf(StemKind.VOCALS), mixOf("1").soloedStems)
        assertEquals(listOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 0f), mixer.calls)
    }

    @Test
    fun `when the solo is removed then every stem plays at its level`() = runTest {
        playSongAsStems()
        setSoloed("1", StemKind.VOCALS, true)
        mixer.calls.clear()

        setSoloed("1", StemKind.VOCALS, false)

        assertEquals(listOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 0.8f), mixer.calls)
    }

    @Test
    fun `when a soloed stem is muted then everything is silent`() = runTest {
        playSongAsStems()
        setSoloed("1", StemKind.VOCALS, true)
        mixer.calls.clear()

        setMuted("1", StemKind.VOCALS, true)

        assertEquals(listOf(StemKind.VOCALS to 0f, StemKind.DRUMS to 0f), mixer.calls)
    }

    @Test
    fun `when the level of a muted stem moves then it stays silent`() = runTest {
        playSongAsStems()
        setMuted("1", StemKind.DRUMS, true)
        mixer.calls.clear()

        setGain("1", StemKind.DRUMS, 0.3f)

        assertEquals(listOf(StemKind.DRUMS to 0f), mixer.calls)
        assertEquals(0.3f, mixOf("1").gains[StemKind.DRUMS])
    }

    @Test
    fun `when the level of a stem silenced by a solo moves then it stays silent`() = runTest {
        playSongAsStems()
        setSoloed("1", StemKind.VOCALS, true)
        mixer.calls.clear()

        setGain("1", StemKind.DRUMS, 0.3f)
        setGain("1", StemKind.VOCALS, 0.9f)

        assertEquals(listOf(StemKind.DRUMS to 0f, StemKind.VOCALS to 0.9f), mixer.calls)
    }

    @Test
    fun `when the original plays then mute and solo are only saved`() = runTest {
        nowPlaying.set(song)
        sources.open(AudioSource.LocalFile("/song.m4a"))

        setMuted("1", StemKind.DRUMS, true)
        setSoloed("1", StemKind.VOCALS, true)

        assertTrue(mixer.calls.isEmpty())
        assertEquals(setOf(StemKind.DRUMS), mixOf("1").mutedStems)
        assertEquals(setOf(StemKind.VOCALS), mixOf("1").soloedStems)
    }

    @Test
    fun `when another track plays as stems then mute and solo are only saved`() = runTest {
        nowPlaying.set(other)
        sources.open(AudioSource.Stems(listOf(StemSource(StemKind.VOCALS, "/v.mp3"))))

        setMuted("1", StemKind.VOCALS, true)
        setSoloed("1", StemKind.VOCALS, true)

        assertTrue(mixer.calls.isEmpty())
        assertTrue(mixOf("2").mutedStems.isEmpty())
    }

    @Test
    fun `when the track does not have the stem then nothing is saved or applied`() = runTest {
        playSongAsStems()

        setMuted("1", StemKind.PIANO, true)
        setSoloed("1", StemKind.PIANO, true)

        assertTrue(mixer.calls.isEmpty())
        assertEquals(StemMix(mapOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 0.8f)), mixOf("1"))
    }

    @Test
    fun `when the track is not separated then nothing happens`() = runTest {
        setMuted("9", StemKind.VOCALS, true)
        setSoloed("9", StemKind.VOCALS, true)

        assertTrue(mixer.calls.isEmpty())
        assertTrue("9" !in stems.stems.value)
    }
}
