package com.damianogiusti.slowdowner.domain.stems

/**
 * The level of each stem while a track plays as [com.damianogiusti.slowdowner.domain.source.AudioSource.Stems].
 * Not suspending: it only sets a gain, so callers can apply it with no suspension point after an open.
 */
interface StemMixer {
    /** [gain] is 0..1, 1 being the original level. Does nothing when the open source is not stems or lacks [stem]. */
    fun setGain(stem: StemKind, gain: Float)
}
