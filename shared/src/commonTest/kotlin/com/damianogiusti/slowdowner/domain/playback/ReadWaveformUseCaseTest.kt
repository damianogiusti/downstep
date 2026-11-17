package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.fake.FakeEngine
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadWaveformUseCaseTest {

    private val engine = FakeEngine().apply { outline = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f) }
    private val read = ReadWaveformUseCase(engine)

    @Test
    fun `when a file is open then its levels are read over the range`() {
        val waveform = read(0.5f, 1f, 2)

        assertEquals(0.5f, waveform?.from)
        assertEquals(1f, waveform?.to)
        assertContentEquals(floatArrayOf(0.3f, 0.4f), waveform?.levels)
    }

    @Test
    fun `when nothing is open then there is no waveform`() {
        engine.isOpen = false

        assertNull(read(0f, 1f, 4))
    }

    @Test
    fun `when the engine cannot outline its audio then there is no waveform`() {
        engine.outline = FloatArray(0)

        assertNull(read(0f, 1f, 4))
    }

    @Test
    fun `when the range is empty then there is no waveform`() {
        assertNull(read(0.5f, 0.5f, 4))
    }
}
