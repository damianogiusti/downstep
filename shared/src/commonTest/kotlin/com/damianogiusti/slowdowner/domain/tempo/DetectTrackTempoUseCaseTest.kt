package com.damianogiusti.slowdowner.domain.tempo

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.fake.FakeLoopRepository
import com.damianogiusti.slowdowner.fake.FakeTempoDetector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetectTrackTempoUseCaseTest {

    private val nowPlaying = InMemoryNowPlayingRepository()
    private val repository = FakeLoopRepository()
    private val detector = FakeTempoDetector()
    private val detect = DetectTrackTempoUseCase(detector, repository, nowPlaying)
    private val song = LibraryTrack("a", "A", "/a.m4a", null, 0)
    private val other = LibraryTrack("b", "B", "/b.m4a", null, 0)

    @Test
    fun `when no detector is bound then nothing is analysed`() = runTest {
        nowPlaying.set(song)
        val unavailable = DetectTrackTempoUseCase(null, repository, nowPlaying)

        assertFalse(unavailable.isAvailable)
        assertFalse(unavailable.isPending("a"))
        assertFalse(unavailable("a"))
        assertEquals(0, repository.trackLoops("a").tempoAnalysisVersion)
    }

    @Test
    fun `when a track is first opened then its tempo is detected and saved with the analysis version`() = runTest {
        nowPlaying.set(song)

        assertTrue(detect.isPending("a"))
        assertTrue(detect("a"))

        val saved = repository.trackLoops("a")
        assertEquals(120f, saved.detectedBpm)
        assertEquals(0.25f, saved.beatOffsetSeconds)
        assertEquals(DetectTrackTempoUseCase.ANALYSIS_VERSION, saved.tempoAnalysisVersion)
        assertEquals(120f, saved.effectiveBpm)
    }

    @Test
    fun `when the track was analysed already then the cached result is kept without analysing again`() = runTest {
        nowPlaying.set(song)
        detect("a")
        detector.estimate = TempoEstimate(90f, 0f, 1f)

        assertFalse(detect.isPending("a"))
        assertFalse(detect("a"))

        assertEquals(listOf<Float?>(null), detector.requests)
        assertEquals(120f, repository.trackLoops("a").detectedBpm)
    }

    @Test
    fun `when nothing is found then that is cached too`() = runTest {
        nowPlaying.set(song)
        detector.estimate = null

        assertTrue(detect("a"))

        val saved = repository.trackLoops("a")
        assertNull(saved.detectedBpm)
        assertNull(saved.effectiveBpm)
        assertEquals(DetectTrackTempoUseCase.ANALYSIS_VERSION, saved.tempoAnalysisVersion)
        assertFalse(detect.isPending("a"))
    }

    @Test
    fun `when an older analysis ran then the track is analysed again`() = runTest {
        nowPlaying.set(song)
        repository.setDetectedTempo("a", TempoEstimate(60f, 0f, 1f), analysisVersion = DetectTrackTempoUseCase.ANALYSIS_VERSION - 1)

        assertTrue(detect("a"))
        assertEquals(120f, repository.trackLoops("a").detectedBpm)
    }

    @Test
    fun `when another track is open then its audio is not taken for this one`() = runTest {
        nowPlaying.set(other)

        assertFalse(detect("a"))

        assertTrue(detector.requests.isEmpty())
        assertEquals(0, repository.trackLoops("a").tempoAnalysisVersion)
    }

    @Test
    fun `when the user typed a tempo then its beats are found with it as the hint`() = runTest {
        nowPlaying.set(song)
        repository.setBpm("a", 96f)

        detect("a")

        val saved = repository.trackLoops("a")
        assertEquals(96f, saved.effectiveBpm)
        assertEquals(120f, saved.detectedBpm)
        assertEquals(0.1f, saved.beatOffsetSeconds)
        assertEquals(listOf(null, 96f), detector.requests)
    }

    @Test
    fun `when the typed tempo changes or is cleared then the beats follow the tempo in use`() = runTest {
        nowPlaying.set(song)
        detect("a")
        repository.setBpm("a", 80f)

        assertTrue(detect.alignBeats("a"))
        assertEquals(0.1f, repository.trackLoops("a").beatOffsetSeconds)

        repository.setBpm("a", null)
        detector.hintedOffset = 0.24f
        assertTrue(detect.alignBeats("a"))
        assertEquals(0.24f, repository.trackLoops("a").beatOffsetSeconds)
        assertEquals(listOf(null, 80f, 120f), detector.requests)
    }

    @Test
    fun `when there is no tempo at all then the first beat is unknown`() = runTest {
        nowPlaying.set(song)
        repository.setBeatOffset("a", 0.3f)

        assertTrue(detect.alignBeats("a"))

        assertNull(repository.trackLoops("a").beatOffsetSeconds)
    }
}
