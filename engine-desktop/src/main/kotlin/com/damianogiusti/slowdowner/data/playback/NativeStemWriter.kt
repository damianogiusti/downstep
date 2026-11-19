package com.damianogiusti.slowdowner.data.playback

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT
import java.nio.file.Path

/** Format of a stem file: MP3 at [bitrateKbps] on average, 1 or 2 [channels], 8000..48000 Hz. */
data class StemFormat(val sampleRate: Int, val channels: Int, val bitrateKbps: Int = DEFAULT_BITRATE_KBPS) {
    companion object {
        const val DEFAULT_BITRATE_KBPS = 192
    }
}

/** Outcome of [NativeStemWriter.open], mapped from the `SD_STEM_*` codes of `sdengine.h`. */
sealed interface StemWriterOpenResult {
    data class Opened(val writer: NativeStemWriter) : StemWriterOpenResult

    /** The file cannot be created. */
    data object CannotCreateFile : StemWriterOpenResult

    /** A rate, channel count or bitrate the MP3 encoder does not take. */
    data object UnsupportedFormat : StemWriterOpenResult
}

/**
 * Writes one stem as an MP3 (`sd_stem_writer_*`): LAME directly, with the Xing/LAME tag that lets the
 * platform decoder return exactly the frames written. Feed it block by block with [write], then [close].
 * Not thread-safe: one writer, one thread at a time.
 */
class NativeStemWriter private constructor(
    private val api: NativeEngine.Api,
    private val nativeHandle: MemorySegment,
    private val channels: Int,
) : AutoCloseable {

    private var isClosed = false

    /** Set by [close]: whether the file is complete. False before. */
    var isComplete: Boolean = false
        private set

    /**
     * Encodes interleaved samples (-1..1), [channels] per frame: all of [interleaved], whose size must be a
     * multiple of the channel count. False once the encoder or the disk failed: the file is then not complete
     * and every later write fails too.
     */
    fun write(interleaved: FloatArray): Boolean {
        require(interleaved.size % channels == 0) { "size is not a multiple of $channels channels" }
        return Arena.ofConfined().use { arena ->
            val buffer: MemorySegment = arena.allocate(interleaved.size * 4L, 4)
            MemorySegment.copy(interleaved, 0, buffer, JAVA_FLOAT, 0, interleaved.size)
            write(buffer)
        }
    }

    /** Same, from caller-owned native memory: as many whole frames as [interleaved] holds. */
    fun write(interleaved: MemorySegment): Boolean {
        check(!isClosed) { "writer already closed" }
        val frames = interleaved.byteSize() / (4L * channels)
        return api.stemWriterWrite.invokeExact(nativeHandle, interleaved, frames) as Int == 0
    }

    /** Flushes, writes the tag and closes the file. True if the file is complete. */
    override fun close() {
        if (isClosed) return
        isClosed = true
        isComplete = api.stemWriterClose.invokeExact(nativeHandle) as Int != 0
    }

    companion object {
        fun open(path: String, format: StemFormat, library: Path = NativeEngine.defaultLibrary()): StemWriterOpenResult {
            val api = NativeEngine.Api.load(library)
            return Arena.ofConfined().use { arena ->
                val utf8Path: MemorySegment = arena.allocateFrom(path)
                val status: MemorySegment = arena.allocate(JAVA_INT)
                val handle = api.stemWriterOpen.invokeExact(
                    utf8Path, format.sampleRate, format.channels, format.bitrateKbps, status,
                ) as MemorySegment
                when {
                    handle != MemorySegment.NULL -> StemWriterOpenResult.Opened(NativeStemWriter(api, handle, format.channels))
                    status.get(JAVA_INT, 0) == STEM_BAD_FORMAT -> StemWriterOpenResult.UnsupportedFormat
                    else -> StemWriterOpenResult.CannotCreateFile
                }
            }
        }

        private const val STEM_BAD_FORMAT = 2
    }
}
