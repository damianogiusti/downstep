package com.damianogiusti.slowdowner.domain.effects

interface EffectsEngine {
    /** 0 = left channel only, 1 = right channel only. */
    val mixRight: Float
    val mixSliderPosition: Int
    val quality: ProcessingQuality

    fun setMixFromSlider(position: Int)
    fun setEqEnabled(enabled: Boolean)
    fun setPreampDb(gainDb: Float)
    fun setBandGainDb(band: Int, gainDb: Float)
    /** Rebuilds the native player: a short audio gap is expected. */
    fun setQuality(quality: ProcessingQuality)

    /** False where the engine cannot measure loudness yet: the setting is not offered there. */
    val canNormalizeVolume: Boolean

    /** Quiet tracks are raised and loud ones lowered to one loudness. Playback only: exports keep the original level. */
    fun setVolumeNormalized(enabled: Boolean)
}
