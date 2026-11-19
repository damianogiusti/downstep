package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.fake.FakeStemsStore
import com.damianogiusti.slowdowner.fake.FakeStemsStore.Companion.stem
import com.damianogiusti.slowdowner.fake.RecordingLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultStemsRepositoryTest {

    private val logger = RecordingLogger()
    private val store = FakeStemsStore(
        listOf(TrackStemsDto("a", "model", "STEMS", listOf(stem("VOCALS"), stem("DRUMS", 0.5f)))),
    )
    private val repository = DefaultStemsRepository(store, UnconfinedTestDispatcher(), logger)

    @Test
    fun `when a track is separated then it is observed with mode and gains`() = runTest {
        val stems = repository.observe("a").first()

        assertEquals("model", stems?.model)
        assertEquals(TrackMode.STEMS, stems?.mode)
        assertEquals(mapOf(StemKind.VOCALS to 1f, StemKind.DRUMS to 0.5f), stems?.mix?.gains)
    }

    @Test
    fun `when a track is not separated then observe gives null`() = runTest {
        assertNull(repository.observe("b").first())
    }

    @Test
    fun `when stems exist then the separated ids are observed`() = runTest {
        assertEquals(setOf("a"), repository.observeSeparatedTrackIds().first())
    }

    @Test
    fun `when the mode is set then it is observed`() = runTest {
        repository.setMode("a", TrackMode.FULL_TRACK)

        assertEquals(TrackMode.FULL_TRACK, repository.observe("a").first()?.mode)
    }

    @Test
    fun `when the track is not separated then setting the mode does nothing`() = runTest {
        repository.setMode("b", TrackMode.FULL_TRACK)

        assertNull(repository.observe("b").first())
    }

    @Test
    fun `when a gain is set then only that stem changes`() = runTest {
        repository.setGain("a", StemKind.DRUMS, 0.75f)

        assertEquals(mapOf(StemKind.VOCALS to 1f, StemKind.DRUMS to 0.75f), repository.observe("a").first()?.mix?.gains)
    }

    @Test
    fun `when a stem is muted then the flag is observed and the level is kept`() = runTest {
        repository.setMuted("a", StemKind.DRUMS, true)

        val mix = repository.observe("a").first()?.mix
        assertEquals(setOf(StemKind.DRUMS), mix?.mutedStems)
        assertEquals(0.5f, mix?.gains?.get(StemKind.DRUMS))
        assertEquals(0f, mix?.effectiveGain(StemKind.DRUMS))
    }

    @Test
    fun `when a stem is soloed and then not then the flag is observed`() = runTest {
        repository.setSoloed("a", StemKind.VOCALS, true)
        assertEquals(setOf(StemKind.VOCALS), repository.observe("a").first()?.mix?.soloedStems)

        repository.setSoloed("a", StemKind.VOCALS, false)
        assertEquals(emptySet<StemKind>(), repository.observe("a").first()?.mix?.soloedStems)
    }

    @Test
    fun `when the track is not separated then mute and solo do nothing`() = runTest {
        repository.setMuted("b", StemKind.DRUMS, true)
        repository.setSoloed("b", StemKind.DRUMS, true)

        assertNull(repository.observe("b").first())
    }

    @Test
    fun `when a gain is out of range then it is clamped`() = runTest {
        repository.setGain("a", StemKind.DRUMS, 1.7f)
        assertEquals(1f, repository.observe("a").first()?.mix?.gains?.get(StemKind.DRUMS))

        repository.setGain("a", StemKind.DRUMS, -0.3f)
        assertEquals(0f, repository.observe("a").first()?.mix?.gains?.get(StemKind.DRUMS))
    }

    @Test
    fun `when a gain is not a number then it is ignored`() = runTest {
        repository.setGain("a", StemKind.DRUMS, Float.NaN)

        assertEquals(0.5f, repository.observe("a").first()?.mix?.gains?.get(StemKind.DRUMS))
    }

    @Test
    fun `when the stems are deleted then the track is no longer separated`() = runTest {
        repository.delete("a")

        assertEquals(listOf("a"), store.deleted)
        assertNull(repository.observe("a").first())
    }

    @Test
    fun `when the stored mode is unknown then observe gives null and does not crash`() = runTest {
        store.add(TrackStemsDto("c", "model", "HOLOGRAM", listOf(stem("VOCALS"))))

        assertNull(repository.observe("c").first())
        assertEquals(1, logger.at('W').size)
    }
}
