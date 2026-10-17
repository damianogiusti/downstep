package com.damianogiusti.slowdowner.domain.click

import com.damianogiusti.slowdowner.domain.loops.TrackLoops
import com.damianogiusti.slowdowner.fake.FakeClickEngine
import com.damianogiusti.slowdowner.fake.FakeClickSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClickUseCasesTest {

    @Test
    fun `when no click engine is bound then metronome and count-in are unavailable`() {
        val metronome = SetMetronomeUseCase(engine = null)
        val countIn = SetCountInUseCase(engine = null)

        assertFalse(metronome.isAvailable)
        assertFalse(countIn.isAvailable)
        assertFalse(metronome(enabled = true, volume = 1f, tempo = TrackTempo(120f)))
        assertFalse(countIn(enabled = true))
    }

    @Test
    fun `when the metronome is on without a tempo then the engine gets the grid cleared and stays silent`() {
        val engine = FakeClickEngine()
        val metronome = SetMetronomeUseCase(engine)

        assertTrue(metronome.isAvailable)
        assertFalse(metronome(enabled = true, volume = 0.5f, tempo = null))
        assertNull(engine.tempo)
        assertFalse(engine.isMetronomeOn)

        assertTrue(metronome(enabled = true, volume = 2f, tempo = TrackTempo(92f, 0.3f)))
        assertEquals(TrackTempo(92f, 0.3f), engine.tempo)
        assertTrue(engine.isMetronomeOn)
        assertEquals(1f, engine.volume)
    }

    @Test
    fun `when the count-in is set then its beats are kept in range and every loop needs it on`() {
        val engine = FakeClickEngine()
        val countIn = SetCountInUseCase(engine)

        assertTrue(countIn(enabled = true, beats = 99, everyLoop = true))
        assertEquals(ClickSettings.MAX_COUNT_IN_BEATS, engine.countInBeats)
        assertTrue(engine.countInEveryLoop)
        countIn(enabled = false, beats = 4, everyLoop = true)
        assertEquals(0, engine.countInBeats)
        assertFalse(engine.countInEveryLoop)
        countIn(enabled = true, beats = 0)
        assertEquals(ClickSettings.MIN_COUNT_IN_BEATS, engine.countInBeats)
    }

    @Test
    fun `when a track has a tempo then the typed one wins and its first beat goes with it`() {
        assertNull(TrackLoops("a").tempo())
        assertEquals(TrackTempo(128f, 0f), TrackLoops("a", detectedBpm = 128f).tempo())
        assertEquals(TrackTempo(90f, 0.2f), TrackLoops("a", bpm = 90f, detectedBpm = 128f, beatOffsetSeconds = 0.2f).tempo())
        assertNull(TrackLoops("a", bpm = 5f).tempo())
    }

    @Test
    fun `when the settings change then they are saved within range`() = runTest {
        val repository = FakeClickSettingsRepository()
        val update = UpdateClickSettingsUseCase(repository)
        val observe = ObserveClickSettingsUseCase(repository)

        assertEquals(ClickSettings(), observe().first())
        update { it.copy(isMetronomeOn = true, volume = 1.7f, countInBeats = 40) }

        assertEquals(
            ClickSettings(isMetronomeOn = true, volume = 1f, countInBeats = ClickSettings.MAX_COUNT_IN_BEATS),
            observe().first(),
        )
    }
}
