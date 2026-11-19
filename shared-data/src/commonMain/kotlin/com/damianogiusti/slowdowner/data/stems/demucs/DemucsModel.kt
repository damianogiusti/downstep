package com.damianogiusti.slowdowner.data.stems.demucs

import com.damianogiusti.slowdowner.domain.stems.StemKind

/**
 * Fixed numbers of Intel's OpenVINO conversion of Demucs `htdemucs_6s` (STFT, iSTFT and the whole-track
 * normalisation are left to the host). Every shape is static: the network always sees one segment.
 */
object DemucsModel {
    const val SAMPLE_RATE = 44_100
    const val CHANNELS = 2

    /** 7.8 s: `int(44100 * 7.8)`, the `segment` of the model. */
    const val SEGMENT_LENGTH = 343_980

    /** `int((1 - 0.25) * SEGMENT_LENGTH)`: Demucs' default overlap of 25 %. */
    const val STRIDE = 257_985

    const val FFT_SIZE = 4096
    const val HOP = 1024

    /** Bins given to the network: the Nyquist bin is dropped. */
    const val FREQUENCY_BINS = 2048

    /** `ceil(SEGMENT_LENGTH / HOP)`. */
    const val FRAMES = 336

    /** Complex spectrogram as channels: left re, left im, right re, right im. */
    const val SPECTROGRAM_CHANNELS = 4

    /** Order of the stems in both outputs of the network. */
    val STEMS: List<StemKind> = listOf(
        StemKind.DRUMS, StemKind.BASS, StemKind.OTHER, StemKind.VOCALS, StemKind.GUITAR, StemKind.PIANO,
    )

    /** Number of floats of the spectral input `x` and of one stem group in the spectral output. */
    const val SPECTROGRAM_FLOATS = SPECTROGRAM_CHANNELS * FREQUENCY_BINS * FRAMES

    /** Index of the planar channel of [stem] (position in [STEMS]) and [channel] (0 left, 1 right) in the 12 of a segment. */
    fun channelIndex(stem: Int, channel: Int): Int = stem * CHANNELS + channel
}
