package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Does nothing: the tests that need it only look at what a ViewModel does around the engine. */
class FakePlaybackEngine : PlaybackEngine {
    override val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 64)
    override val isOpen = false
    override val isPlaying = false
    override val position = 0f
    override val positionFlow: StateFlow<Float> = MutableStateFlow(0f)
    override val durationSeconds = 0f
    override val speedPercent = 100f
    override val speedSliderPosition = 0
    override var pitchSemitones = 0f
        private set
    override val pitchSliderPosition = 0
    override val isLoopOn = false
    override val loopStart = 0f
    override val loopEnd = 1f

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
    override fun setLoop(region: LoopRegion) = Unit
    override fun setLoopOn(enabled: Boolean) = Unit
    override fun markLoopStart() = Unit
    override fun markLoopEnd() = Unit
    override fun halveLoop() = Unit
    override fun doubleLoop() = Unit
    override fun shiftLoopForward() = Unit
    override fun shiftLoopBackward() = Unit
}
