package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Nearly nothing: an open file, a loop and a waveform are set by hand, and peak reads are counted. */
class FakePlaybackEngine : PlaybackEngine {
    override val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 64)
    override var isOpen = false
    override val isPlaying = false
    override val position = 0f
    override val positionFlow: StateFlow<Float> = MutableStateFlow(0f)
    override var durationSeconds = 0f
    override val speedPercent = 100f
    override val speedSliderPosition = 0
    override var pitchSemitones = 0f
        private set
    override val pitchSliderPosition = 0
    override var isLoopOn = false
        private set
    override var loopStart = 0f
    override var loopEnd = 1f

    /** Peak levels of the whole file; empty draws no waveform, like an engine that cannot outline. */
    var outline = FloatArray(0)
    var peakReads = 0

    /** Slider positions the pitch slider sent to the engine itself (not Fine). */
    val sliderPitchCalls = mutableListOf<Int>()

    override fun togglePlayStop() = Unit
    override fun stop() = Unit
    override fun seekTo(position: Float) = Unit
    override fun setSpeedFromSlider(position: Int) = Unit
    override fun setPitchFromSlider(position: Int) {
        sliderPitchCalls += position
    }
    override fun setSpeedPercent(percent: Float) = Unit
    override fun setPitchSemitones(semitones: Float) {
        pitchSemitones = semitones
    }
    override fun setLoop(region: LoopRegion) {
        loopStart = region.start
        loopEnd = region.end
        isLoopOn = region.isOn
    }
    override fun setLoopOn(enabled: Boolean) {
        isLoopOn = enabled
    }
    override fun markLoopStart() = Unit
    override fun markLoopEnd() = Unit
    override fun halveLoop() = Unit
    override fun doubleLoop() = Unit
    override fun shiftLoopForward() = Unit
    override fun shiftLoopBackward() = Unit

    override fun peaks(from: Float, to: Float, buckets: Int): FloatArray {
        peakReads++
        if (!isOpen || outline.isEmpty()) return FloatArray(0)
        return FloatArray(buckets) { i -> outline[((from + (to - from) * i / buckets) * outline.size).toInt().coerceIn(0, outline.lastIndex)] }
    }
}
