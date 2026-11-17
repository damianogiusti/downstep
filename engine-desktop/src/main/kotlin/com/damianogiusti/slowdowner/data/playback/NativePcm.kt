package com.damianogiusti.slowdowner.data.playback

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT
import java.nio.file.Path

/** Outcome of [NativePcm.decode], mapped from the `SD_PCM_*` codes of `sdengine.h`. */
sealed interface PcmDecodeResult {
    data class Decoded(val pcm: NativePcm) : PcmDecodeResult

    /** Unreadable, unsupported, no audio, or more than two channels. */
    data object Failed : PcmDecodeResult

    /** The decoded audio does not fit in the memory budget. */
    data object TooLong : PcmDecodeResult
}

/**
 * A file decoded to float PCM in native memory (`sd_pcm_*`), without a player. Planar, at the file's own
 * rate and channel count: no resampling, and a mono file stays mono (reading channel 1 gives channel 0).
 * The caller owns it and must [close] it. [copy] may be called from several threads at once, never
 * together with [close].
 */
class NativePcm private constructor(private val api: NativeEngine.Api, private val nativeHandle: MemorySegment) :
    AutoCloseable {

    @Volatile
    private var isClosed = false

    private val handle: MemorySegment
        get() {
            check(!isClosed) { "pcm already closed" }
            return nativeHandle
        }

    val sampleRate: Int get() = api.pcmSampleRate.invokeExact(handle) as Int
    val channels: Int get() = api.pcmChannels.invokeExact(handle) as Int
    val frames: Long get() = api.pcmFrames.invokeExact(handle) as Long

    /** Fills [out] from [firstFrame] of [channel]. Returns how many samples were copied: fewer at the end of the audio. */
    fun copy(channel: Int, firstFrame: Long, out: FloatArray): Int = Arena.ofConfined().use { arena ->
        val buffer: MemorySegment = arena.allocate(out.size * 4L, 4)
        val copied = copy(channel, firstFrame, buffer).toInt()
        MemorySegment.copy(buffer, JAVA_FLOAT, 0, out, 0, copied)
        copied
    }

    /** Same, straight into caller-owned native memory: as many floats as [out] holds. */
    fun copy(channel: Int, firstFrame: Long, out: MemorySegment): Long =
        api.pcmCopy.invokeExact(handle, channel, firstFrame, out, out.byteSize() / 4) as Long

    @Synchronized
    override fun close() {
        if (isClosed) return
        isClosed = true
        api.pcmFree.invokeExact(nativeHandle)
    }

    companion object {
        /**
         * Decodes the whole file with the platform decoder. Blocking: call it off the UI thread.
         * [memoryBudgetBytes] as in [NativeEngine.setMemoryBudget]: 0 = what the OS can give.
         */
        fun decode(path: String, memoryBudgetBytes: Long = 0, library: Path = NativeEngine.defaultLibrary()): PcmDecodeResult {
            val api = NativeEngine.Api.load(library)
            return Arena.ofConfined().use { arena ->
                val utf8Path: MemorySegment = arena.allocateFrom(path)
                val status: MemorySegment = arena.allocate(JAVA_INT)
                val handle = api.pcmDecode.invokeExact(utf8Path, memoryBudgetBytes, status) as MemorySegment
                when {
                    handle != MemorySegment.NULL -> PcmDecodeResult.Decoded(NativePcm(api, handle))
                    status.get(JAVA_INT, 0) == PCM_TOO_LONG -> PcmDecodeResult.TooLong
                    else -> PcmDecodeResult.Failed
                }
            }
        }

        private const val PCM_TOO_LONG = 2
    }
}
