package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.domain.effects.EffectsEngine
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import com.damianogiusti.slowdowner.domain.playback.LoopEditing
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.LoopState
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MAX_PITCH_SEMITONES
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MAX_SPEED_PERCENT
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MIN_PITCH_SEMITONES
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.MIN_SPEED_PERCENT
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import com.damianogiusti.slowdowner.domain.playback.SliderCurves
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The C++ engine (Bungee) as domain engines, for desktop and later iOS. Speed, pitch and mix use the
 * shared slider curves ([SliderCurves]) and the loop buttons the shared rules ([LoopEditing]), so a preset
 * or a backup means the same thing on every platform.
 *
 * Settings are mirrored here because the C side only receives them. Position and end of file are
 * polled in [scope]: the C engine never calls back into the JVM.
 */
class DesktopPlaybackEngine(
    private val engine: NativeEngine,
    scope: CoroutineScope,
    pollInterval: Duration = 50.milliseconds,
) : PlaybackEngine, EffectsEngine, AutoCloseable {

    private val _events = MutableSharedFlow<PlaybackEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<PlaybackEvent> = _events

    override val isOpen get() = engine.isOpen
    override val isPlaying get() = engine.isPlaying
    override val position get() = engine.position.toFloat()
    override val durationSeconds get() = engine.durationSeconds.toFloat()

    // Written by the control thread, read by the UI and the poller.
    @Volatile
    override var speedPercent = 100f
        private set
    override val speedSliderPosition get() = SliderCurves.speedPosition(speedPercent)
    @Volatile
    override var pitchSemitones = 0f
        private set
    override val pitchSliderPosition get() = SliderCurves.pitchPosition(pitchSemitones)

    @Volatile
    override var isLoopOn = false
        private set
    @Volatile
    override var loopStart = 0f
        private set
    @Volatile
    override var loopEnd = 1f
        private set

    @Volatile
    override var mixRight = 0.5f
        private set
    override val mixSliderPosition get() = SliderCurves.mixPosition(mixRight)
    @Volatile
    override var quality = ProcessingQuality.HIGH
        private set

    private val polling: Job = scope.launch {
        while (isActive) {
            delay(pollInterval)
            poll()
        }
    }

    /** Stops polling, then frees the native engine. */
    override fun close() {
        polling.cancel()
        engine.close()
    }

    /**
     * Decodes the whole file: blocking, call it off the UI thread. The C engine clears the loop of the
     * previous file; speed, pitch and effects carry over until the caller applies the track's own.
     */
    fun openFile(path: String): Boolean {
        val opened = engine.open(path)
        if (opened) {
            applyLoop(LoopRegion.WHOLE_FILE)
            _events.tryEmit(PlaybackEvent.ControlsChanged)
        }
        return opened
    }

    fun closeFile() {
        engine.closeFile()
        _events.tryEmit(PlaybackEvent.PlayStateChanged)
    }

    override fun togglePlayStop() {
        engine.setPlaying(!engine.isPlaying)
        _events.tryEmit(PlaybackEvent.PlayStateChanged)
    }

    override fun stop() {
        engine.setPlaying(false)
        _events.tryEmit(PlaybackEvent.PlayStateChanged)
    }

    override fun seekTo(position: Float) {
        engine.seek(position.coerceIn(0f, 1f).toDouble())
        // Reported at once: while paused the poller stays quiet.
        _events.tryEmit(PlaybackEvent.PositionChanged)
    }

    override fun setSpeedFromSlider(position: Int) = setSpeedPercent(SliderCurves.speedPercent(position))

    override fun setPitchFromSlider(position: Int) =
        setPitchSemitones(SliderCurves.pitchSemitones(position, snapToSemitones = true, currentSemitones = pitchSemitones))

    override fun setSpeedPercent(percent: Float) {
        speedPercent = percent.coerceIn(MIN_SPEED_PERCENT, MAX_SPEED_PERCENT)
        engine.setSpeed(speedPercent / 100.0)
        onPracticeSettingsChanged()
    }

    override fun setPitchSemitones(semitones: Float) {
        pitchSemitones = semitones.coerceIn(MIN_PITCH_SEMITONES, MAX_PITCH_SEMITONES)
        engine.setPitch(pitchSemitones.toDouble())
        onPracticeSettingsChanged()
    }

    override fun setLoop(region: LoopRegion) {
        if (!engine.isOpen) return
        val start = region.start.coerceIn(0f, 1f)
        val loop = LoopRegion(start, region.end.coerceIn(start, 1f), region.isOn)
        val isOutside = loop.isOn && (position < loop.start || position > loop.end)
        applyLoop(loop, seekTo = if (isOutside) loop.start else null)
        _events.tryEmit(PlaybackEvent.LoopChanged)
        onPracticeSettingsChanged()
    }

    override fun setLoopOn(enabled: Boolean) {
        if (enabled == isLoopOn) return
        // The points are kept while the loop is off: turning it back on restores them.
        applyLoop(LoopRegion(loopStart, loopEnd, enabled))
        _events.tryEmit(PlaybackEvent.LoopChanged)
        onPracticeSettingsChanged()
    }

    override fun markLoopStart() = editLoop { LoopEditing.markStart(it, durationSeconds) }
    override fun markLoopEnd() = editLoop { LoopEditing.markEnd(it, durationSeconds) }
    override fun halveLoop() = editLoop { LoopEditing.halve(it, durationSeconds) }
    override fun doubleLoop() = editLoop { LoopEditing.double(it) }
    override fun shiftLoopForward() = editLoop { LoopEditing.shiftForward(it) }
    override fun shiftLoopBackward() = editLoop { LoopEditing.shiftBackward(it) }

    override fun setMixFromSlider(position: Int) {
        mixRight = SliderCurves.mixRight(position)
        engine.setMix(mixRight.toDouble())
    }

    override fun setEqEnabled(enabled: Boolean) = engine.setEqEnabled(enabled)

    override fun setPreampDb(gainDb: Float) = engine.setPreamp(gainDb.toDouble())

    override fun setBandGainDb(band: Int, gainDb: Float) = engine.setEqBand(band, gainDb.toDouble())

    /** Bungee has no quality levels (other hop sizes degrade it): the choice is kept, the sound is the same. */
    override fun setQuality(quality: ProcessingQuality) {
        this.quality = quality
        _events.tryEmit(PlaybackEvent.ControlsChanged)
    }

    private fun applyLoop(region: LoopRegion, seekTo: Float? = null) {
        loopStart = region.start
        loopEnd = region.end
        isLoopOn = region.isOn
        if (seekTo == null) engine.setLoop(loopStart.toDouble(), loopEnd.toDouble(), isLoopOn)
        else engine.setLoopAndSeek(loopStart.toDouble(), loopEnd.toDouble(), isLoopOn, seekTo.toDouble())
    }

    private inline fun editLoop(edit: (LoopState) -> LoopState?) {
        if (!engine.isOpen) return
        val before = LoopState(loopStart, loopEnd, isLoopOn, position)
        val after = edit(before) ?: return
        applyLoop(
            LoopRegion(after.start, after.end, after.isOn),
            seekTo = after.position.takeIf { it != before.position },
        )
        _events.tryEmit(PlaybackEvent.LoopChanged)
        onPracticeSettingsChanged()
    }

    private fun onPracticeSettingsChanged() {
        _events.tryEmit(PlaybackEvent.PracticeSettingsChanged)
    }

    private fun poll() {
        if (engine.takeEvents() and NativeEngine.EVENT_REACHED_END != 0) {
            _events.tryEmit(PlaybackEvent.PlayStateChanged)
            _events.tryEmit(PlaybackEvent.ReachedEnd)
        }
        if (engine.isPlaying) _events.tryEmit(PlaybackEvent.PositionChanged)
    }
}
