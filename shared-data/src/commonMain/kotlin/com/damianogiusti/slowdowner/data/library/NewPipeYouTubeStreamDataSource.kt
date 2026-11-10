package com.damianogiusti.slowdowner.data.library

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * [downloader] initialises NewPipe on the first [resolve], not at app start: building the HTTP client
 * and the extractor cost startup time for an import that may never happen. Null when the app has
 * initialised NewPipe itself.
 */
class NewPipeYouTubeStreamDataSource(downloader: (() -> Downloader)? = null) : YouTubeStreamDataSource {

    // NewPipe keeps its downloader in a static: once per process, whichever thread imports first.
    private val initialized = lazy { downloader?.let { NewPipe.init(it()) } }

    override fun findSupportedUrl(text: String?): String? =
        text?.let { URL.find(it)?.value }?.takeIf { YOUTUBE_URL.containsMatchIn(it) }

    override fun resolve(url: String): ResolvedAudioDto {
        initialized.value
        val info = try {
            StreamInfo.getInfo(withoutPlaylist(url))
        } catch (e: ContentNotAvailableException) {
            throw StreamUnavailableException("Content not available", e)
        }
        val best = pickBest(info.audioStreams, info.videoStreams)
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
    // Some videos, often old uploads, have no audio-only stream: then the video with
    // sound (MP4, AAC) is downloaded, and the engine reads only its audio track.
    internal fun pickBest(audio: List<AudioStream>, video: List<VideoStream>): Stream? {
        val progressive = audio.filter { it.isProgressive() }
        return progressive.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: progressive.maxByOrNull { it.averageBitrate }
            ?: video.firstOrNull { !it.isVideoOnly && it.format == MediaFormat.MPEG_4 && it.isProgressive() }
    }

    private fun Stream.isProgressive() = isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

    private companion object {
        val URL = Regex("""https?://\S+""")
        val YOUTUBE_URL = Regex("""^https?://((www|m|music)\.)?(youtube\.com|youtu\.be)/""")
    }
}

/**
 * NewPipe refuses a watch link that also names a playlist (`list=`): the links of mixes, playlists and
 * YouTube Music albums. Only the video is imported, so the playlist is dropped.
 */
internal fun withoutPlaylist(url: String): String {
    val base = url.substringBefore('#').substringBefore('?')
    val query = url.substringBefore('#').substringAfter('?', "")
    val fragment = url.substringAfter('#', "")
    val kept = query.split('&').filter { it.isNotEmpty() && !it.startsWith("list=") }
    return base +
        (if (kept.isEmpty()) "" else kept.joinToString("&", prefix = "?")) +
        (if (fragment.isEmpty()) "" else "#$fragment")
}
