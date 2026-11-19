package com.damianogiusti.slowdowner.data.stems.demucs

import com.damianogiusti.slowdowner.domain.stems.StemKind
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.log10
import kotlin.random.Random

class StemFinisherTest {

    private val count = 500
    private val random = Random(5)
    private val mix = Array(2) { FloatArray(count + 20) { random.nextFloat() * 1.6f - 0.8f } }
    private val normalisation = TrackNormalisation(MeanStd(0.003, 0.25))

    private fun networkOutput(): Array<FloatArray> = Array(12) { FloatArray(count) { random.nextFloat() * 2f - 1f } }

    @Test
    fun `at full gain the six stems sum to the mix`() {
        val block = networkOutput()

        StemFinisher(mix, normalisation, gain = 1f).finish(block, start = 10, count = count)

        for (channel in 0..1) {
            for (i in 0 until count) {
                var sum = 0f
                for (stem in DemucsModel.STEMS.indices) sum += block[DemucsModel.channelIndex(stem, channel)][i]
                assertEquals(mix[channel][10 + i], sum, 1e-5f)
            }
        }
    }

    @Test
    fun `with the headroom the stems sum to the mix times the headroom`() {
        val block = networkOutput()

        StemFinisher(mix, normalisation).finish(block, start = 0, count = count)

        assertEquals(-3.0, 20 * log10(StemHeadroom.GAIN.toDouble()), 1e-6)
        for (channel in 0..1) {
            for (i in 0 until count) {
                var sum = 0f
                for (stem in DemucsModel.STEMS.indices) sum += block[DemucsModel.channelIndex(stem, channel)][i]
                assertEquals(StemHeadroom.GAIN * mix[channel][i], sum, 1e-5f)
            }
        }
    }

    @Test
    fun `the other stem is the residual and the rest are restored then scaled`() {
        val block = networkOutput()
        val before = block.map { it.copyOf() }

        StemFinisher(mix, normalisation).finish(block, start = 0, count = count)

        val drums = DemucsModel.channelIndex(DemucsModel.STEMS.indexOf(StemKind.DRUMS), 1)
        for (i in 0 until count) {
            assertEquals(normalisation.restore(before[drums][i]) * StemHeadroom.GAIN, block[drums][i], 1e-6f)
        }
        val other = DemucsModel.channelIndex(DemucsModel.STEMS.indexOf(StemKind.OTHER), 0)
        var expected = mix[0][7]
        for (stem in DemucsModel.STEMS.indices) {
            if (DemucsModel.STEMS[stem] != StemKind.OTHER) expected -= normalisation.restore(before[DemucsModel.channelIndex(stem, 0)][7])
        }
        assertEquals(expected * StemHeadroom.GAIN, block[other][7], 1e-5f)
    }
}
