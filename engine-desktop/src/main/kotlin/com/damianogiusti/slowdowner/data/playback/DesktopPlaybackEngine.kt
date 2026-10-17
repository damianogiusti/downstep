package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.domain.click.ClickTrackEngine
import com.damianogiusti.slowdowner.domain.click.TrackTempo
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The C++ engine (Bungee) as domain engines, for desktop and later iOS. Speed, pitch and mix use the
 * shared slider curves ([SliderCurves]) and the loop buttons the shared rules ([LoopEditing]), so a preset
 * or a backup means the same thing on every platform.
 *
 * Settings are mirrored here because the C side only receives them. Position and end of file are
 * polled in [scope]: the C engine never calls back into the JVM. The poll reads the position alone
 * and publishes it in [positionFlow], not as an event: an event per tick made every listener re-read
 * all the controls 20 times a second.
 */
class DesktopPlaybackEngine(
    private val engine: NativeEngine,
    scope: CoroutineScope,
    pollInterval: Duration = 50.milliseconds,
) : PlaybackEngine, EffectsEngine, ClickTrackEngine, AutoCloseable {

    private val _events = MutableSharedFlow<PlaybackEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<PlaybackEvent> = _events

    private val _position = MutableStateFlow(0f)
    override val positionFlow: StateFlow<Float> = _position.asStateFlow()

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

    // Calls from other threads can be halfway through native code when close() runs: a poll, the
    // waveform's peaks, a decode on the IO dispatcher. Freeing the engine then would crash the process
    // (it did, when the app quit). They share the read side, so a long decode blocks neither the poll
    // nor the waveform; close() takes the write side and waits for them all.
    private val lifecycle = ReentrantReadWriteLock()
    @Volatile
    private var isClosed = false

    private val polling: Job = scope.launch {
        while (isActive) {
            delay(pollInterval)
            lifecycle.read { if (!isClosed) poll() }
        }
    }

    /**
     * Stops polling, waits for the calls in progress (a decode included), then frees the native engine.
     * A decode that does not end within a few seconds keeps the engine alive: the process is about to
     * exit, and a leak there is harmless where freeing it under the decode is not.
     */
    override fun close() {
        close(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** False when the calls in progress outlasted [timeout]: the engine is then left allocated. */
    internal fun close(timeout: Long, unit: TimeUnit): Boolean {
        polling.cancel()
        val write = lifecycle.writeLock()
        if (!write.tryLock(timeout, unit)) {
            isClosed = true
            return false
        }
        try {
            if (!isClosed) engine.close()
            isClosed = true
        } finally {
            write.unlock()
        }
        return true
    }

    /**
     * Decodes the whole file: blocking, call it off the UI thread. The C engine clears the loop of the
     * previous file; speed, pitch and effects carry over until the caller applies the track's own.
     * False once the engine is closed.
     */
    fun openFile(path: String): Boolean = lifecycle.read {
        if (isClosed) return@read false
        val opened = engine.open(path)
        if (opened) {
            applyLoop(LoopRegion.WHOLE_FILE)
            publishPosition()
            _events.tryEmit(PlaybackEvent.ControlsChanged)
        }
        opened
    }

    fun closeFile() = lifecycle.read {
        if (isClosed) return@read
        engine.closeFile()
        publishPosition()
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
        // Reported at once, not at the next poll: a click on the waveform moves the playhead now.
        publishPosition()
        _events.tryEmit(PlaybackEvent.PositionChanged)
    }

    override fun setSpeedFromSlider(position: Int) = setSpeedPercent(SliderCurves.speedPercent(position))

    override fun setPitchFromSlider(position: Int) =
        setPitchSemitones(SliderCurves.pitchSemitones(position, snapToSemitones = true, currentSemitones = pitchSemitones))

    // A slider drag repeats the same value often (pitch snaps to semitones): only a real change
    // reaches the audio thread and the listeners.
    override fun setSpeedPercent(percent: Float) {
        val speed = percent.coerceIn(MIN_SPEED_PERCENT, MAX_SPEED_PERCENT)
        if (speed == speedPercent) return
        speedPercent = speed
        engine.setSpeed(speed / 100.0)
        onPracticeSettingsChanged()
    }

    override fun setPitchSemitones(semitones: Float) {
        val pitch = semitones.coerceIn(MIN_PITCH_SEMITONES, MAX_PITCH_SEMITONES)
        if (pitch == pitchSemitones) return
        pitchSemitones = pitch
        engine.setPitch(pitch.toDouble())
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

    // Called off the main thread by the waveform: it must not race close() freeing the engine.
    override fun peaks(from: Float, to: Float, buckets: Int): FloatArray = lifecycle.read {
        if (isClosed) FloatArray(0) else engine.peaks(from.toDouble(), to.toDouble(), buckets)
    }

    override fun setMixFromSlider(position: Int) {
        mixRight = SliderCurves.mixRight(position)
        engine.setMix(mixRight.toDouble())
    }

    override fun setEqEnabled(enabled: Boolean) = engine.setEqEnabled(enabled)

    override fun setPreampDb(gainDb: Float) = engine.setPreamp(gainDb.toDouble())

    override fun setBandGainDb(band: Int, gainDb: Float) = engine.setEqBand(band, gainDb.toDouble())

    // The metronome and count-in. The view model pushes them whenever the tempo or the settings change,
    // possibly as the app quits: a closed engine ignores them.
    @Volatile
    var tempo: TrackTempo? = null
        private set

    override fun setTempo(tempo: TrackTempo?) = ifOpen {
        this.tempo = tempo
        engine.setTempo(tempo?.bpm?.toDouble() ?: 0.0, tempo?.beatOffsetSeconds?.toDouble() ?: 0.0, tempo?.beatsPerBar ?: 4)
    }

    override fun setMetronome(enabled: Boolean, volume: Float) = ifOpen { engine.setMetronome(enabled, volume.toDouble()) }

    override fun setCountIn(beats: Int, everyLoop: Boolean) = ifOpen { engine.setCountIn(beats, everyLoop) }

    override val countInRemaining: Int get() = lifecycle.read { if (isClosed) 0 else engine.countInRemaining }

    /**
     * The open file's tempo ([NativeEngine.detectTempo]). Blocking for a fraction of a second: off the
     * UI thread. Null once closed: close() waits for an analysis in progress.
     */
    fun detectTempo(hintBpm: Double = 0.0): NativeEngine.NativeTempo? = lifecycle.read {
        if (isClosed) null else engine.detectTempo(hintBpm)
    }

    private inline fun ifOpen(action: () -> Unit) = lifecycle.read { if (!isClosed) action() }

    /** Bungee has no quality levels (other hop sizes degrade it): the choice is kept, the sound is the same. */
    override fun setQuality(quality: ProcessingQuality) {
        this.quality = quality
        _events.tryEmit(PlaybackEvent.ControlsChanged)
    }

    private fun applyLoop(region: LoopRegion, seekTo: Float? = null) {
        loopStart = region.start
        loopEnd = region.end
        isLoopOn = region.isOn
        if (seekTo == null) {
            engine.setLoop(loopStart.toDouble(), loopEnd.toDouble(), isLoopOn)
        } else {
            engine.setLoopAndSeek(loopStart.toDouble(), loopEnd.toDouble(), isLoopOn, seekTo.toDouble())
            publishPosition()
        }
    }

    private fun publishPosition() {
        _position.value = engine.position.toFloat()
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

    // Two native calls per tick, both lock-free reads. The StateFlow drops repeats: while paused
    // nobody is woken up.
    private fun poll() {
        val reachedEnd = engine.takeEvents() and NativeEngine.EVENT_REACHED_END != 0
        publishPosition()
        if (reachedEnd) {
            _events.tryEmit(PlaybackEvent.PlayStateChanged)
            _events.tryEmit(PlaybackEvent.ReachedEnd)
        }
    }

    private companion object {
        const val CLOSE_TIMEOUT_SECONDS = 5L
    }
}
