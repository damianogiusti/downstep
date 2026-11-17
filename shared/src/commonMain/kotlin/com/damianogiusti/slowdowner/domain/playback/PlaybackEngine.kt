package com.damianogiusti.slowdowner.domain.playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport and DSP controls of the audio engine. Sliders use slider positions because the
 * engine owns the non-linear slider → value mapping. Value setters are for restoring saved
 * settings, presets and step buttons.
 */
interface PlaybackEngine {
    val events: Flow<PlaybackEvent>

    val isOpen: Boolean
    val isPlaying: Boolean
    /** 0..1 over the whole file. */
    val position: Float
    /**
     * [position] as it moves: polled while playing, and set at once by seeks, loop jumps, open and close.
     * The only position source for the UI: [events] carry no per-tick position change.
     */
    val positionFlow: StateFlow<Float>
    val durationSeconds: Float

    val speedPercent: Float
    val speedSliderPosition: Int
    val pitchSemitones: Float
    val pitchSliderPosition: Int

    val isLoopOn: Boolean
    val loopStart: Float
    val loopEnd: Float

    fun togglePlayStop()
    fun stop()
    fun seekTo(position: Float)
    fun setSpeedFromSlider(position: Int)
    fun setPitchFromSlider(position: Int)
    fun setSpeedPercent(percent: Float)
    fun setPitchSemitones(semitones: Float)
    /** Sets both loop points at once; an inactive region is kept for when the loop is turned on. */
    fun setLoop(region: LoopRegion)
    fun setLoopOn(enabled: Boolean)
    fun markLoopStart()
    fun markLoopEnd()
    fun halveLoop()
    fun doubleLoop()
    fun shiftLoopForward()
    fun shiftLoopBackward()

    /**
     * Waveform of the open file for drawing: [buckets] peak levels 0..1, one per equal slice of
     * [from]..[to] (fractions 0..1 of the file). Cheap, any thread. Empty when nothing is open, or
     * on an engine that cannot outline its audio: the waveform then draws flat.
     */
    fun peaks(from: Float, to: Float, buckets: Int): FloatArray = FloatArray(0)

    companion object {
        const val SLIDER_MAX = 10_000
        // Full range of the engine sliders, measured on device.
        const val MIN_SPEED_PERCENT = 25f
        const val MAX_SPEED_PERCENT = 200f
        const val MIN_PITCH_SEMITONES = -12f
        const val MAX_PITCH_SEMITONES = 12f
    }
}
