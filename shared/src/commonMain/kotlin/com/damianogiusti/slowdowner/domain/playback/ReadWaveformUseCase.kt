package com.damianogiusti.slowdowner.domain.playback

/**
 * The open file's waveform over [from]..[to] in [buckets] levels. Cheap: the engine reads it from a
 * summary built when the file opened, so it is fine on the main thread. Null when nothing is open, or
 * on an engine that cannot outline its audio (Android's).
 */
class ReadWaveformUseCase(private val engine: PlaybackEngine) {

    operator fun invoke(from: Float, to: Float, buckets: Int): Waveform? {
        if (!engine.isOpen || buckets <= 0 || to <= from) return null
        val levels = engine.peaks(from.coerceIn(0f, 1f), to.coerceIn(0f, 1f), buckets)
        return if (levels.isEmpty()) null else Waveform(from, to, levels)
    }
}
