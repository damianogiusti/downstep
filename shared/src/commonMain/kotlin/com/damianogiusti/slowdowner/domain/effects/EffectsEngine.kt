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
}
