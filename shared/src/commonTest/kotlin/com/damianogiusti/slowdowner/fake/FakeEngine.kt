package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.effects.EffectsEngine
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEngine : PlaybackEngine, EffectsEngine {
    override val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 64)
    override var isOpen = true
    override var isPlaying = false
    override val positionFlow = MutableStateFlow(0.5f)
    override var position
        get() = positionFlow.value
        set(value) { positionFlow.value = value }
    override var durationSeconds = 100f
    var speed = 100f
    override val speedPercent get() = speed
    override val speedSliderPosition = 0
    var pitch = 0f
    override val pitchSemitones get() = pitch
    override val pitchSliderPosition = 0
    var loopEnabled = false
    override val isLoopOn get() = loopEnabled
    override var loopStart = 0f
    override var loopEnd = 1f

    /** Every practice setter call, in order, e.g. "speed:75.0". */
    val practiceCalls = mutableListOf<String>()

    override fun togglePlayStop() { isPlaying = !isPlaying }
    override fun stop() { isPlaying = false }
    override fun seekTo(position: Float) { this.position = position }
    override fun setSpeedFromSlider(position: Int) = Unit
    override fun setPitchFromSlider(position: Int) = Unit
    override fun setSpeedPercent(percent: Float) {
        speed = percent
        practiceCalls += "speed:$percent"
    }
    override fun setPitchSemitones(semitones: Float) {
        pitch = semitones
        practiceCalls += "pitch:$semitones"
    }
    override fun setLoop(region: LoopRegion) {
        loopStart = region.start
        loopEnd = region.end
        loopEnabled = region.isOn
        practiceCalls += "loop:$region"
    }
    override fun setLoopOn(enabled: Boolean) { loopEnabled = enabled }
    override fun markLoopStart() = Unit
    override fun markLoopEnd() = Unit
    override fun halveLoop() = Unit
    override fun doubleLoop() = Unit
    override fun shiftLoopForward() = Unit
    override fun shiftLoopBackward() = Unit
    /** Peak levels of the whole file: [peaks] slices them like the real engine. */
    var outline = FloatArray(0)
    override fun peaks(from: Float, to: Float, buckets: Int): FloatArray {
        if (!isOpen || outline.isEmpty()) return FloatArray(0)
        return FloatArray(buckets) { i -> outline[((from + (to - from) * i / buckets) * outline.size).toInt().coerceIn(0, outline.lastIndex)] }
    }

    override val mixRight = 0.5f
    override val mixSliderPosition = 0
    var appliedQuality = ProcessingQuality.HIGH
    override val quality get() = appliedQuality
    var qualityChanges = 0
    var appliedEqEnabled = false
    var appliedPreampDb = 0f
    var appliedEqBands: List<EqBand> = emptyList()
    var isolationMask = 0

    override fun setMixFromSlider(position: Int) = Unit
    override fun setEqEnabled(enabled: Boolean) { appliedEqEnabled = enabled }
    override fun setPreampDb(gainDb: Float) { appliedPreampDb = gainDb }
    override fun setEqBands(bands: List<EqBand>) { appliedEqBands = bands }
    override fun setIsolation(mask: Int) { isolationMask = mask }
    override fun setQuality(quality: ProcessingQuality) {
        appliedQuality = quality
        qualityChanges++
    }
}
