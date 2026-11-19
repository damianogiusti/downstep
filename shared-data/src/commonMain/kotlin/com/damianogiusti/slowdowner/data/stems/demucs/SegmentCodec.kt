package com.damianogiusti.slowdowner.data.stems.demucs

import com.damianogiusti.slowdowner.data.stems.dsp.Stft
import com.damianogiusti.slowdowner.data.stems.dsp.StftPadding
import com.damianogiusti.slowdowner.data.stems.dsp.periodicHannWindow
import com.damianogiusti.slowdowner.data.stems.dsp.scaleInPlace
import kotlin.math.sqrt

/** What [SegmentCodec.analyse] measured, needed to scale the network's outputs back. */
data class SegmentStatistics(val spectral: MeanStd, val time: MeanStd)

/**
 * The part of `HTDemucs.forward` that the OpenVINO graph leaves out, for one segment: `_spec`, the
 * complex-as-channels packing and the input normalisation before the network; `x * std + mean`, `_mask`,
 * `_ispec` and the sum of the two branches after it.
 *
 * Not thread-safe (scratch buffers and an [Stft]): one instance per thread.
 */
class SegmentCodec {

    private val stft = Stft(FFT, HOP, periodicHannWindow(FFT))
    private val frameStride = stft.frameStride
    private val spectra = FloatArray(PADDED_FRAMES * frameStride)
    private val signal = FloatArray(stft.signalLength(PADDED_FRAMES))
    private val rootN = sqrt(FFT.toFloat())

    /**
     * Prepares the inputs of the network from [segment], two planar channels of [DemucsModel.SEGMENT_LENGTH]
     * samples (already normalised for the whole track). Writes the spectral input `x`, normalised, into
     * [spectrogram] (`[4][2048][336]`, [DemucsModel.SPECTROGRAM_FLOATS] floats) and normalises [segment] in
     * place into the time input `xt`.
     */
    fun analyse(segment: FloatArray, spectrogram: FloatArray): SegmentStatistics {
        require(segment.size == SEGMENT * DemucsModel.CHANNELS) { "segment has ${segment.size} samples" }
        require(spectrogram.size == DemucsModel.SPECTROGRAM_FLOATS) { "spectrogram has ${spectrogram.size} floats" }
        for (channel in 0 until DemucsModel.CHANNELS) {
            // _spec: reflect pad by hop / 2 * 3, then torch.stft(center = true) reflects by fft / 2 more.
            val raw = segment.copyOfRange(channel * SEGMENT, (channel + 1) * SEGMENT)
            val padded = StftPadding.reflect(raw, SPEC_PAD, SPEC_PAD + FRAMES * HOP - SEGMENT)
            val reflected = StftPadding.reflect(padded, FFT / 2)
            check(stft.frameCount(reflected.size) == PADDED_FRAMES) { "unexpected frame count" }
            stft.forward(reflected, reflected.size, spectra)
            scaleInPlace(spectra, 1f / rootN) // normalized = true
            // Drop the Nyquist bin and the two frames on each side; real and imaginary parts become channels.
            for (f in 0 until BINS) {
                val real = (channel * 2 * BINS + f) * FRAMES
                val imaginary = ((channel * 2 + 1) * BINS + f) * FRAMES
                for (t in 0 until FRAMES) {
                    val base = (t + EDGE_FRAMES) * frameStride + 2 * f
                    spectrogram[real + t] = spectra[base]
                    spectrogram[imaginary + t] = spectra[base + 1]
                }
            }
        }
        val spectral = normaliseInPlace(spectrogram)
        val time = normaliseInPlace(segment)
        return SegmentStatistics(spectral, time)
    }

    /**
     * Turns the two outputs of the network into the six stems of a segment. [spectralOutput] is `x_out`
     * `[6][4][2048][336]` and [timeOutput] is `xt_out` `[6][2][SEGMENT_LENGTH]`, both in the network's
     * normalised domain. [destination] gets [DemucsModel.STEMS]`.size * 2` planar channels of
     * [DemucsModel.SEGMENT_LENGTH] samples (see [DemucsModel.channelIndex]): the inverse transform of the
     * spectral branch plus the time branch.
     */
    fun synthesise(spectralOutput: FloatArray, timeOutput: FloatArray, statistics: SegmentStatistics, destination: Array<FloatArray>) {
        val stems = DemucsModel.STEMS.size
        require(spectralOutput.size == stems * DemucsModel.SPECTROGRAM_FLOATS) { "spectral output has ${spectralOutput.size} floats" }
        require(timeOutput.size == stems * DemucsModel.CHANNELS * SEGMENT) { "time output has ${timeOutput.size} floats" }
        require(destination.size == stems * DemucsModel.CHANNELS && destination.all { it.size >= SEGMENT }) { "destination is too small" }
        val spectralMean = statistics.spectral.mean.toFloat()
        val spectralStd = statistics.spectral.std.toFloat()
        val timeMean = statistics.time.mean.toFloat()
        val timeStd = statistics.time.std.toFloat()
        for (stem in 0 until stems) {
            for (channel in 0 until DemucsModel.CHANNELS) {
                // _ispec: one zero bin on top, two zero frames on each side; torch.istft(normalized = true).
                spectra.fill(0f)
                val group = stem * DemucsModel.SPECTROGRAM_FLOATS
                for (f in 0 until BINS) {
                    val real = group + (channel * 2 * BINS + f) * FRAMES
                    val imaginary = group + ((channel * 2 + 1) * BINS + f) * FRAMES
                    for (t in 0 until FRAMES) {
                        val base = (t + EDGE_FRAMES) * frameStride + 2 * f
                        spectra[base] = (spectralOutput[real + t] * spectralStd + spectralMean) * rootN
                        spectra[base + 1] = (spectralOutput[imaginary + t] * spectralStd + spectralMean) * rootN
                    }
                }
                stft.inverse(spectra, PADDED_FRAMES, signal)
                // center = true drops fft / 2; _ispec drops pad = hop / 2 * 3 more.
                val skip = FFT / 2 + SPEC_PAD
                val index = DemucsModel.channelIndex(stem, channel)
                val output = destination[index]
                val timeBase = index * SEGMENT
                for (n in 0 until SEGMENT) {
                    output[n] = signal[skip + n] + (timeOutput[timeBase + n] * timeStd + timeMean)
                }
            }
        }
    }

    private companion object {
        const val FFT = DemucsModel.FFT_SIZE
        const val HOP = DemucsModel.HOP
        const val BINS = DemucsModel.FREQUENCY_BINS
        const val FRAMES = DemucsModel.FRAMES
        const val SEGMENT = DemucsModel.SEGMENT_LENGTH
        const val SPEC_PAD = HOP / 2 * 3
        const val EDGE_FRAMES = 2

        /** Frames of `torch.stft` on the padded signal: `FRAMES + 2 * EDGE_FRAMES`. */
        const val PADDED_FRAMES = FRAMES + 2 * EDGE_FRAMES
    }
}
