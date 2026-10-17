package com.damianogiusti.slowdowner.data.playback

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_DOUBLE
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT
import java.nio.file.Files
import java.nio.file.Path

/**
 * One instance of the C engine (`engine/include/sdengine.h`) through FFM. A thin 1:1 wrapper: the
 * behaviour lives in C++ and in [DesktopPlaybackEngine].
 *
 * Every call is an `invokeExact`: the JIT inlines it, with no boxing or argument array. The casts and
 * the declared types of the arguments (`MemorySegment`, `Int`, `Double`) must match each
 * [FunctionDescriptor] exactly, or the call throws WrongMethodTypeException: NativeEngineTest calls
 * every one of them.
 *
 * Position, playing state and events are atomics on the C side, so they can be read from a polling
 * thread while another thread drives the controls.
 */
class NativeEngine private constructor(private val api: Api, private val nativeHandle: MemorySegment) : AutoCloseable {

    @Volatile
    private var isDestroyed = false

    // Every call goes through here: after close() the C memory is gone, so fail loudly instead.
    private val handle: MemorySegment
        get() {
            check(!isDestroyed) { "engine already closed" }
            return nativeHandle
        }


    val isOpen: Boolean get() = api.isOpen.invokeExact(handle) as Int != 0
    val durationSeconds: Double get() = api.durationSeconds.invokeExact(handle) as Double
    val isPlaying: Boolean get() = api.isPlaying.invokeExact(handle) as Int != 0
    val position: Double get() = api.position.invokeExact(handle) as Double
    val sampleRate: Int get() = api.sampleRate.invokeExact(handle) as Int

    /** Decodes the whole file. Blocking. */
    fun open(path: String): Boolean = Arena.ofConfined().use { arena ->
        val utf8Path: MemorySegment = arena.allocateFrom(path)
        api.open.invokeExact(handle, utf8Path) as Int != 0
    }

    fun closeFile() {
        api.close.invokeExact(handle)
    }

    fun setPlaying(playing: Boolean) {
        api.setPlaying.invokeExact(handle, if (playing) 1 else 0)
    }

    fun seek(position: Double) {
        api.seek.invokeExact(handle, position)
    }

    fun setSpeed(ratio: Double) {
        api.setSpeed.invokeExact(handle, ratio)
    }

    fun setPitch(semitones: Double) {
        api.setPitch.invokeExact(handle, semitones)
    }

    fun setLoop(start: Double, end: Double, enabled: Boolean) {
        api.setLoop.invokeExact(handle, start, end, if (enabled) 1 else 0)
    }

    /** Loop and seek reach the audio thread together. */
    fun setLoopAndSeek(start: Double, end: Double, enabled: Boolean, position: Double) {
        api.setLoopAndSeek.invokeExact(handle, start, end, if (enabled) 1 else 0, position)
    }

    fun setMix(mixRight: Double) {
        api.setMix.invokeExact(handle, mixRight)
    }

    fun setEqEnabled(enabled: Boolean) {
        api.setEqEnabled.invokeExact(handle, if (enabled) 1 else 0)
    }

    /** Band [band] (0 until [EQ_MAX_BANDS]) becomes a bell at [frequencyHz], [gainDb] and [q], together. */
    fun setEqBandParams(band: Int, frequencyHz: Double, gainDb: Double, q: Double) {
        api.setEqBandParams.invokeExact(handle, band, frequencyHz, gainDb, q)
    }

    fun setEqBandCount(count: Int) {
        api.setEqBandCount.invokeExact(handle, count)
    }

    fun setPreamp(gainDb: Double) {
        api.setPreamp.invokeExact(handle, gainDb)
    }

    /** Isolate: bit 0 low, bit 1 mid, bit 2 high; 0 = the full mix. */
    fun setIsolation(mask: Int) {
        api.setIsolation.invokeExact(handle, mask)
    }

    fun setOutputGain(gain: Double) {
        api.setOutputGain.invokeExact(handle, gain)
    }

    /** [buckets] peak levels 0..1 over [from]..[to] of the open file; empty when nothing is open. */
    fun peaks(from: Double, to: Double, buckets: Int): FloatArray = Arena.ofConfined().use { arena ->
        if (buckets <= 0) return FloatArray(0)
        val buffer: MemorySegment = arena.allocate(MemoryLayout.sequenceLayout(buckets.toLong(), JAVA_FLOAT))
        val written = api.peaks.invokeExact(handle, from, to, buffer, buckets) as Int
        if (written <= 0) FloatArray(0) else buffer.toArray(JAVA_FLOAT)
    }

    /** Returns and clears the [EVENT_REACHED_END] bits raised since the previous call. */
    fun takeEvents(): Int = api.takeEvents.invokeExact(handle) as Int

    /**
     * Starts an MP3 export of the open file with the current sound, loop ignored. It renders in its
     * own player over the audio already decoded (shared, not decoded again): playback is not
     * disturbed. Null if nothing is open or [path] cannot be written.
     */
    fun beginExport(path: String, bitrateKbps: Int = 192): NativeExport? = Arena.ofConfined().use { arena ->
        val utf8Path: MemorySegment = arena.allocateFrom(path)
        val exporter = api.exportBegin.invokeExact(handle, utf8Path, bitrateKbps) as MemorySegment
        if (exporter == MemorySegment.NULL) null else NativeExport(api, exporter)
    }

    /**
     * As [beginExport], but only [start]..[end] (fractions of the file): a loop's slice, with short
     * fades at both edges. Null also when the range is empty.
     */
    fun beginExport(path: String, start: Double, end: Double, bitrateKbps: Int = 192): NativeExport? =
        Arena.ofConfined().use { arena ->
            val utf8Path: MemorySegment = arena.allocateFrom(path)
            val exporter = api.exportBeginRange.invokeExact(handle, utf8Path, bitrateKbps, start, end) as MemorySegment
            if (exporter == MemorySegment.NULL) null else NativeExport(api, exporter)
        }

    /** One MP3 export in progress. Call [next] until it returns null, then [finish]. */
    class NativeExport internal constructor(private val api: Api, private val exporter: MemorySegment) {
        private var isFinished = false

        /** Encodes the next chunk: the progress 0..1, or null once everything is encoded. */
        fun next(): Float? {
            check(!isFinished) { "export already finished" }
            val progress = api.exportNext.invokeExact(exporter) as Double
            return if (progress < 0) null else progress.toFloat()
        }

        /** Closes the file and frees the exporter. True if the file is complete. */
        fun finish(): Boolean {
            if (isFinished) return false
            isFinished = true
            return api.exportEnd.invokeExact(exporter) as Int != 0
        }
    }

    /** Manual engines only: [frames] interleaved stereo frames. */
    fun render(frames: Int): FloatArray = Arena.ofConfined().use { arena ->
        val buffer: MemorySegment = arena.allocate(MemoryLayout.sequenceLayout(frames * 2L, JAVA_FLOAT))
        check(api.render.invokeExact(handle, buffer, frames) as Int != 0) { "render() needs a manual engine" }
        buffer.toArray(JAVA_FLOAT)
    }

    /** Destroys the engine; the instance is unusable afterwards. */
    @Synchronized
    override fun close() {
        if (isDestroyed) return
        isDestroyed = true
        api.destroy.invokeExact(nativeHandle)
    }

    companion object {
        const val EVENT_REACHED_END = 1
        const val EQ_MAX_BANDS = 8

        /** System property with the path of `libsdengine`. Set by Gradle for tests. */
        const val LIBRARY_PROPERTY = "slowdowner.engine.library"

        /** Plays on the default output device. Null if the device cannot be opened. */
        fun withDevice(library: Path = defaultLibrary()): NativeEngine? = create(library, withDevice = true, 0)

        /** Nothing plays: pull audio with [render] at [sampleRate] (export, tests). */
        fun manual(sampleRate: Int, library: Path = defaultLibrary()): NativeEngine =
            requireNotNull(create(library, withDevice = false, sampleRate)) { "cannot create a manual engine" }

        private fun create(library: Path, withDevice: Boolean, sampleRate: Int): NativeEngine? {
            val api = Api.load(library)
            val handle = api.create.invokeExact(if (withDevice) 1 else 0, sampleRate) as MemorySegment
            return if (handle == MemorySegment.NULL) null else NativeEngine(api, handle)
        }

        private fun defaultLibrary(): Path {
            val path = System.getProperty(LIBRARY_PROPERTY)
            requireNotNull(path) { "set -D$LIBRARY_PROPERTY=<path to libsdengine>" }
            return Path.of(path)
        }
    }

    internal class Api(lookup: SymbolLookup) {
        private val linker = Linker.nativeLinker()
        private val find = { name: String ->
            lookup.find(name).orElseThrow { IllegalStateException("missing native symbol $name") }
        }

        private fun function(name: String, result: MemoryLayout?, vararg args: MemoryLayout, critical: Boolean = false) =
            linker.downcallHandle(
                find(name),
                if (result == null) FunctionDescriptor.ofVoid(*args) else FunctionDescriptor.of(result, *args),
                *if (critical) CRITICAL else NONE,
            )

        // Critical: getters and setters that only touch atomics. They skip the thread-state switch of a
        // normal downcall, which costs more than the call itself, but stall the GC while they run: never
        // for anything that decodes, encodes, renders, allocates much or takes a lock.
        val create = function("sd_engine_create", ADDRESS, JAVA_INT, JAVA_INT)
        val destroy = function("sd_engine_destroy", null, ADDRESS)
        val open = function("sd_engine_open", JAVA_INT, ADDRESS, ADDRESS)
        val close = function("sd_engine_close", null, ADDRESS)
        val isOpen = function("sd_engine_is_open", JAVA_INT, ADDRESS, critical = true)
        val durationSeconds = function("sd_engine_duration_seconds", JAVA_DOUBLE, ADDRESS, critical = true)
        val setPlaying = function("sd_engine_set_playing", null, ADDRESS, JAVA_INT, critical = true)
        val isPlaying = function("sd_engine_is_playing", JAVA_INT, ADDRESS, critical = true)
        val position = function("sd_engine_position", JAVA_DOUBLE, ADDRESS, critical = true)
        val seek = function("sd_engine_seek", null, ADDRESS, JAVA_DOUBLE, critical = true)
        val setSpeed = function("sd_engine_set_speed", null, ADDRESS, JAVA_DOUBLE, critical = true)
        val setPitch = function("sd_engine_set_pitch", null, ADDRESS, JAVA_DOUBLE, critical = true)
        val setLoop = function("sd_engine_set_loop", null, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT, critical = true)
        val setLoopAndSeek = function(
            "sd_engine_set_loop_and_seek", null, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT, JAVA_DOUBLE, critical = true,
        )
        val setMix = function("sd_engine_set_mix", null, ADDRESS, JAVA_DOUBLE, critical = true)
        val setEqEnabled = function("sd_engine_set_eq_enabled", null, ADDRESS, JAVA_INT, critical = true)
        val setEqBandParams = function(
            "sd_engine_set_eq_band_params", null, ADDRESS, JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, critical = true,
        )
        val setEqBandCount = function("sd_engine_set_eq_band_count", null, ADDRESS, JAVA_INT, critical = true)
        val setPreamp = function("sd_engine_set_preamp", null, ADDRESS, JAVA_DOUBLE, critical = true)
        val setIsolation = function("sd_engine_set_isolation", null, ADDRESS, JAVA_INT, critical = true)
        val setOutputGain = function("sd_engine_set_output_gain", null, ADDRESS, JAVA_DOUBLE, critical = true)
        // Not critical: it takes a (short, uncontended) lock.
        val peaks = function("sd_engine_peaks", JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, ADDRESS, JAVA_INT)
        val takeEvents = function("sd_engine_take_events", JAVA_INT, ADDRESS, critical = true)
        val sampleRate = function("sd_engine_sample_rate", JAVA_INT, ADDRESS, critical = true)
        val render = function("sd_engine_render", JAVA_INT, ADDRESS, ADDRESS, JAVA_INT)
        val exportBegin = function("sd_export_begin", ADDRESS, ADDRESS, ADDRESS, JAVA_INT)
        val exportBeginRange = function("sd_export_begin_range", ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE)
        val exportNext = function("sd_export_next", JAVA_DOUBLE, ADDRESS)
        val exportEnd = function("sd_export_end", JAVA_INT, ADDRESS)

        companion object {
            private val CRITICAL = arrayOf(Linker.Option.critical(false))
            private val NONE = emptyArray<Linker.Option>()
            private val loaded = mutableMapOf<Path, Api>()

            // The library stays loaded for the life of the process: engines come and go, the code does not.
            @Synchronized
            fun load(library: Path): Api = loaded.getOrPut(library.toAbsolutePath()) {
                // Windows does not look for a DLL's imports in the DLL's own folder. Once loaded,
                // LAME is found by name when the engine needs it.
                val lame = library.toAbsolutePath().resolveSibling("mp3lame.dll")
                if (Files.exists(lame)) System.load(lame.toString())
                Api(SymbolLookup.libraryLookup(library, Arena.global()))
            }
        }
    }
}

