package com.damianogiusti.slowdowner.data.library

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/** Remux with the platform extractor/muxer: compressed samples are copied, never re-encoded. */
class MediaMuxerContainerDataSource : MediaContainerDataSource {

    override fun remuxToProgressiveMp4(source: File, target: File) {
        val partial = File(target.path + ".part")
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.path)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio track in $source")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            copySamples(extractor, format, partial)
            if (!partial.renameTo(target)) throw IOException("Cannot move remuxed file to $target")
        } finally {
            extractor.release()
            partial.delete()
        }
    }

    private fun copySamples(extractor: MediaExtractor, format: MediaFormat, output: File) {
        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val track = muxer.addTrack(format)
            muxer.start()
            val buffer = ByteBuffer.allocate(maxSampleSize(format))
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                info.set(0, size, extractor.sampleTime, flags)
                muxer.writeSampleData(track, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
        }
    }

    private fun maxSampleSize(format: MediaFormat) =
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else {
            DEFAULT_MAX_SAMPLE_SIZE
        }

    private companion object {
        const val DEFAULT_MAX_SAMPLE_SIZE = 256 * 1024
    }
}
