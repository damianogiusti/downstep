package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.OpenResult
import com.damianogiusti.slowdowner.domain.source.StemSource
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemMix
import com.damianogiusti.slowdowner.domain.stems.StemMixer
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.stems.TrackStems
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeLogger
import com.damianogiusti.slowdowner.fake.FakeStemMixer
import com.damianogiusti.slowdowner.fake.FakeStemsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenTrackByModeTest {

    private val sources = FakeAudioSourceRepository()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val mixer = FakeStemMixer()
    private val logger = FakeLogger()
    private val track = LibraryTrack("1", "Song", "/song.m4a", null, 0)
    private val separated = TrackStems(
        trackId = "1",
        model = "model",
        mode = TrackMode.STEMS,
        mix = StemMix(mapOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 1f)),
    )
    private val stems = FakeStemsRepository(listOf(separated))
    private val stemsSource = AudioSource.Stems(
        listOf(StemSource(StemKind.VOCALS, "/stems/1/VOCALS.mp3"), StemSource(StemKind.DRUMS, "/stems/1/DRUMS.mp3")),
    )

    private fun open(mixer: StemMixer = this.mixer) = OpenTrackUseCase(sources, nowPlaying, stems, mixer, logger)

    @Test
    fun `when the mode is full track then the original opens`() = runTest {
        stems.setMode("1", TrackMode.FULL_TRACK)

        assertEquals(OpenResult.Opened, open()(track))

        assertEquals(listOf<AudioSource>(AudioSource.LocalFile("/song.m4a")), sources.opened)
        assertEquals(track, nowPlaying.observe().value)
        assertEquals(emptyList(), mixer.calls)
    }

    @Test
    fun `when the track is not separated then the original opens`() = runTest {
        stems.stems.value = emptyMap()

        open()(track)

        assertEquals(listOf<AudioSource>(AudioSource.LocalFile("/song.m4a")), sources.opened)
    }

    @Test
    fun `when the mode is stems then the stems open and the saved gains are applied`() = runTest {
        assertEquals(OpenResult.Opened, open()(track))

        assertEquals(listOf<AudioSource>(stemsSource), sources.opened)
        assertEquals(track, nowPlaying.observe().value)
        assertEquals(listOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 1f), mixer.calls)
    }

    @Test
    fun `the gains are applied after the track is marked as playing with no suspension in between`() = runTest {
        var playingWhenApplied: LibraryTrack? = null
        val checking = object : StemMixer {
            override fun setGain(stem: StemKind, gain: Float) {
                playingWhenApplied = nowPlaying.observe().value
            }
        }

        open(checking)(track)

        assertEquals(track, playingWhenApplied)
    }

    @Test
    fun `when the stem files are missing then the original opens and the mode stays`() = runTest {
        stems.missingFiles += "1"

        assertEquals(OpenResult.Opened, open()(track))

        assertEquals(listOf<AudioSource>(AudioSource.LocalFile("/song.m4a")), sources.opened)
        assertEquals(TrackMode.STEMS, stems.stems.value["1"]?.mode)
    }

    @Test
    fun `when the stems fail to open then the original opens and the mode goes back to full track`() = runTest {
        sources.stemsResult = OpenResult.Failed

        assertEquals(OpenResult.Opened, open()(track))

        assertEquals(listOf(stemsSource, AudioSource.LocalFile("/song.m4a")), sources.opened)
        assertEquals(AudioSource.LocalFile("/song.m4a"), sources.current)
        assertEquals(track, nowPlaying.observe().value)
        assertEquals(TrackMode.FULL_TRACK, stems.stems.value["1"]?.mode)
        assertEquals(emptyList(), mixer.calls)
        assertEquals(mapOf("reason" to "failed"), logger.warnings.single().properties)
    }

    @Test
    fun `when the stems are too long then the original opens and the mode goes back to full track`() = runTest {
        sources.stemsResult = OpenResult.TooLong

        assertEquals(OpenResult.Opened, open()(track))

        assertEquals(TrackMode.FULL_TRACK, stems.stems.value["1"]?.mode)
        assertEquals(mapOf("reason" to "tooLong"), logger.warnings.single().properties)
    }

    @Test
    fun `when the stems and the original both fail then now playing is cleared`() = runTest {
        sources.result = OpenResult.Failed

        assertEquals(OpenResult.Failed, open()(track))

        assertNull(nowPlaying.observe().value)
    }
}
