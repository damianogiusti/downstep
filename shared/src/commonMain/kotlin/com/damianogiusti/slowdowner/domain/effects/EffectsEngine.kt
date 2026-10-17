package com.damianogiusti.slowdowner.domain.effects

interface EffectsEngine {
    /** 0 = left channel only, 1 = right channel only. */
    val mixRight: Float
    val mixSliderPosition: Int
    val quality: ProcessingQuality

    fun setMixFromSlider(position: Int)
    fun setEqEnabled(enabled: Boolean)
    fun setPreampDb(gainDb: Float)
    /** The EQ's bells, at most [EqCurve.MAX_BANDS], each a peaking filter. Changes glide, without clicks. */
    fun setEqBands(bands: List<EqBand>)
    /** Isolate's stage after the EQ ([IsolationBands.mask]); 0 = the full mix. */
    fun setIsolation(mask: Int)
    /** Rebuilds the native player: a short audio gap is expected. */
    fun setQuality(quality: ProcessingQuality)
}
