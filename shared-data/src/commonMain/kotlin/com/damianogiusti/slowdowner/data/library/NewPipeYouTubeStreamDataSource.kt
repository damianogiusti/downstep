package com.damianogiusti.slowdowner.data.library

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

class NewPipeYouTubeStreamDataSource : YouTubeStreamDataSource {

    override fun findSupportedUrl(text: String?): String? =
        text?.let { URL.find(it)?.value }?.takeIf { YOUTUBE_URL.containsMatchIn(it) }

    override fun resolve(url: String): ResolvedAudioDto {
        val info = try {
            StreamInfo.getInfo(url)
        } catch (e: ContentNotAvailableException) {
            throw StreamUnavailableException("Content not available", e)
        }
        val best = pickBest(info.audioStreams)
            ?: throw StreamUnavailableException("No downloadable audio stream")
        return ResolvedAudioDto(
            id = info.id,
            title = info.name,
            streamUrl = best.content,
            extension = best.format?.suffix ?: "m4a",
        )
    }

    // The engine decodes AAC reliably; other codecs are only a fallback. DASH and
    // HLS would need segment assembly, so only plain HTTP streams are considered.
    private fun pickBest(streams: List<AudioStream>): AudioStream? {
        val progressive = streams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        return progressive.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: progressive.maxByOrNull { it.averageBitrate }
    }

    private companion object {
        val URL = Regex("""https?://\S+""")
        val YOUTUBE_URL = Regex("""^https?://((www|m|music)\.)?(youtube\.com|youtu\.be)/""")
    }
}
