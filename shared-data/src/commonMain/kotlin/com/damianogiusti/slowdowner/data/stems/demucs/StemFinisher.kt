package com.damianogiusti.slowdowner.data.stems.demucs

import com.damianogiusti.slowdowner.domain.stems.StemKind
import kotlin.math.pow

/** Level given to every stem before it is written, so loud masters do not clip. The player undoes it. */
object StemHeadroom {
    const val DB = -3.0

    /** `10^(DB / 20)`, the factor stems are multiplied by. */
    val GAIN: Float = 10.0.pow(DB / 20.0).toFloat()
}

/**
 * Last step before the files: turns the overlap-add output of the network (normalised for the whole track)
 * into the stems to write.
 *
 * 1. Undo the track normalisation, `y * std + mean`.
 * 2. Replace "other" by the residual `mix - (drums + bass + vocals + guitar + piano)`, so that all six sum back
 *    to the track; Demucs alone leaves a small error between the sum and the mix.
 * 3. Multiply every stem by [gain] (the headroom). The residual is taken before it, so with every fader up the
 *    six stems sum to `gain * mix`.
 *
 * [mix] is the track as decoded, one array per channel (left, right), before any normalisation.
 */
class StemFinisher(
    private val mix: Array<FloatArray>,
    private val normalisation: TrackNormalisation,
    private val gain: Float = StemHeadroom.GAIN,
) {

    /**
     * Finishes in place the stems of the track samples `start until start + count`. [block] holds the 12 planar
     * channels of [DemucsModel.channelIndex]; only their first [count] samples are used.
     */
    fun finish(block: Array<FloatArray>, start: Int, count: Int) {
        require(block.size == DemucsModel.STEMS.size * DemucsModel.CHANNELS) { "expected 12 channels, got ${block.size}" }
        val other = DemucsModel.STEMS.indexOf(StemKind.OTHER)
        for (channel in 0 until DemucsModel.CHANNELS) {
            val track = mix[channel]
            for (i in 0 until count) {
                var others = 0f
                for (stem in DemucsModel.STEMS.indices) {
                    val samples = block[DemucsModel.channelIndex(stem, channel)]
                    samples[i] = normalisation.restore(samples[i])
                    if (stem != other) others += samples[i]
                }
                block[DemucsModel.channelIndex(other, channel)][i] = track[start + i] - others
                for (stem in DemucsModel.STEMS.indices) {
                    block[DemucsModel.channelIndex(stem, channel)][i] *= gain
                }
            }
        }
    }
}
