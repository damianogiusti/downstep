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
import java.lang.invoke.MethodHandle
import java.nio.file.Files
import java.nio.file.Path

/**
 * One instance of the C engine (`engine/include/sdengine.h`) through FFM. A thin 1:1 wrapper: the
 * behaviour lives in C++ and in [DesktopPlaybackEngine].
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


    val isOpen: Boolean get() = api.isOpen.call(handle) as Int != 0
    val durationSeconds: Double get() = api.durationSeconds.call(handle) as Double
    val isPlaying: Boolean get() = api.isPlaying.call(handle) as Int != 0
    val position: Double get() = api.position.call(handle) as Double
    val sampleRate: Int get() = api.sampleRate.call(handle) as Int

    /** Decodes the whole file. Blocking. */
    fun open(path: String): Boolean = Arena.ofConfined().use { arena ->
        api.open.call(handle, arena.allocateFrom(path)) as Int != 0
    }

    fun closeFile() = api.close.callVoid(handle)
    fun setPlaying(playing: Boolean) = api.setPlaying.callVoid(handle, if (playing) 1 else 0)
    fun seek(position: Double) = api.seek.callVoid(handle, position)
    fun setSpeed(ratio: Double) = api.setSpeed.callVoid(handle, ratio)
    fun setPitch(semitones: Double) = api.setPitch.callVoid(handle, semitones)
    fun setLoop(start: Double, end: Double, enabled: Boolean) =
        api.setLoop.callVoid(handle, start, end, if (enabled) 1 else 0)
    /** Loop and seek reach the audio thread together. */
    fun setLoopAndSeek(start: Double, end: Double, enabled: Boolean, position: Double) =
        api.setLoopAndSeek.callVoid(handle, start, end, if (enabled) 1 else 0, position)
    fun setMix(mixRight: Double) = api.setMix.callVoid(handle, mixRight)
    fun setEqEnabled(enabled: Boolean) = api.setEqEnabled.callVoid(handle, if (enabled) 1 else 0)
    fun setEqBand(band: Int, gainDb: Double) = api.setEqBand.callVoid(handle, band, gainDb)
    fun setPreamp(gainDb: Double) = api.setPreamp.callVoid(handle, gainDb)
    fun setOutputGain(gain: Double) = api.setOutputGain.callVoid(handle, gain)

    /** Returns and clears the [EVENT_REACHED_END] bits raised since the previous call. */
    fun takeEvents(): Int = api.takeEvents.call(handle) as Int

    /**
     * Starts an MP3 export of the open file with the current sound, loop ignored. It decodes the file
     * again on its own: playback is not disturbed. Null if nothing is open or [path] cannot be written.
     */
    fun beginExport(path: String, bitrateKbps: Int = 192): NativeExport? = Arena.ofConfined().use { arena ->
        val exporter = api.exportBegin.call(handle, arena.allocateFrom(path), bitrateKbps) as MemorySegment
        if (exporter == MemorySegment.NULL) null else NativeExport(api, exporter)
    }

    /** One MP3 export in progress. Call [next] until it returns null, then [finish]. */
    class NativeExport internal constructor(private val api: Api, private val exporter: MemorySegment) {
        private var isFinished = false

        /** Encodes the next chunk: the progress 0..1, or null once everything is encoded. */
        fun next(): Float? {
            check(!isFinished) { "export already finished" }
            val progress = api.exportNext.call(exporter) as Double
            return if (progress < 0) null else progress.toFloat()
        }

        /** Closes the file and frees the exporter. True if the file is complete. */
        fun finish(): Boolean {
            if (isFinished) return false
            isFinished = true
            return api.exportEnd.call(exporter) as Int != 0
        }
    }

    /** Manual engines only: [frames] interleaved stereo frames. */
    fun render(frames: Int): FloatArray = Arena.ofConfined().use { arena ->
        val buffer = arena.allocate(MemoryLayout.sequenceLayout(frames * 2L, JAVA_FLOAT))
        check(api.render.call(handle, buffer, frames) as Int != 0) { "render() needs a manual engine" }
        buffer.toArray(JAVA_FLOAT)
    }

    /** Destroys the engine; the instance is unusable afterwards. */
    @Synchronized
    override fun close() {
        if (isDestroyed) return
        isDestroyed = true
        api.destroy.callVoid(nativeHandle)
    }

    companion object {
        const val EVENT_REACHED_END = 1
        const val EQ_BANDS = 7

        /** System property with the path of `libsdengine`. Set by Gradle for tests. */
        const val LIBRARY_PROPERTY = "slowdowner.engine.library"

        /** Plays on the default output device. Null if the device cannot be opened. */
        fun withDevice(library: Path = defaultLibrary()): NativeEngine? = create(library, withDevice = true, 0)

        /** Nothing plays: pull audio with [render] at [sampleRate] (export, tests). */
        fun manual(sampleRate: Int, library: Path = defaultLibrary()): NativeEngine =
            requireNotNull(create(library, withDevice = false, sampleRate)) { "cannot create a manual engine" }

        private fun create(library: Path, withDevice: Boolean, sampleRate: Int): NativeEngine? {
            val api = Api.load(library)
            val handle = api.create.call(if (withDevice) 1 else 0, sampleRate) as MemorySegment
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

        private fun function(name: String, result: MemoryLayout?, vararg args: MemoryLayout) =
            linker.downcallHandle(
                find(name),
                if (result == null) FunctionDescriptor.ofVoid(*args) else FunctionDescriptor.of(result, *args),
            )

        val create = function("sd_engine_create", ADDRESS, JAVA_INT, JAVA_INT)
        val destroy = function("sd_engine_destroy", null, ADDRESS)
        val open = function("sd_engine_open", JAVA_INT, ADDRESS, ADDRESS)
        val close = function("sd_engine_close", null, ADDRESS)
        val isOpen = function("sd_engine_is_open", JAVA_INT, ADDRESS)
        val durationSeconds = function("sd_engine_duration_seconds", JAVA_DOUBLE, ADDRESS)
        val setPlaying = function("sd_engine_set_playing", null, ADDRESS, JAVA_INT)
        val isPlaying = function("sd_engine_is_playing", JAVA_INT, ADDRESS)
        val position = function("sd_engine_position", JAVA_DOUBLE, ADDRESS)
        val seek = function("sd_engine_seek", null, ADDRESS, JAVA_DOUBLE)
        val setSpeed = function("sd_engine_set_speed", null, ADDRESS, JAVA_DOUBLE)
        val setPitch = function("sd_engine_set_pitch", null, ADDRESS, JAVA_DOUBLE)
        val setLoop = function("sd_engine_set_loop", null, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT)
        val setLoopAndSeek = function("sd_engine_set_loop_and_seek", null, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_INT, JAVA_DOUBLE)
        val setMix = function("sd_engine_set_mix", null, ADDRESS, JAVA_DOUBLE)
        val setEqEnabled = function("sd_engine_set_eq_enabled", null, ADDRESS, JAVA_INT)
        val setEqBand = function("sd_engine_set_eq_band", null, ADDRESS, JAVA_INT, JAVA_DOUBLE)
        val setPreamp = function("sd_engine_set_preamp", null, ADDRESS, JAVA_DOUBLE)
        val setOutputGain = function("sd_engine_set_output_gain", null, ADDRESS, JAVA_DOUBLE)
        val takeEvents = function("sd_engine_take_events", JAVA_INT, ADDRESS)
        val sampleRate = function("sd_engine_sample_rate", JAVA_INT, ADDRESS)
        val render = function("sd_engine_render", JAVA_INT, ADDRESS, ADDRESS, JAVA_INT)
        val exportBegin = function("sd_export_begin", ADDRESS, ADDRESS, ADDRESS, JAVA_INT)
        val exportNext = function("sd_export_next", JAVA_DOUBLE, ADDRESS)
        val exportEnd = function("sd_export_end", JAVA_INT, ADDRESS)

        companion object {
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

private fun MethodHandle.call(vararg args: Any): Any? = invokeWithArguments(*args)

private fun MethodHandle.callVoid(vararg args: Any) {
    invokeWithArguments(*args)
}
