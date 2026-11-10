package com.damianogiusti.slowdowner.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException

class NewPipeYouTubeStreamDataSourceTest {

    private val dataSource = NewPipeYouTubeStreamDataSource()

    @Test
    fun `finds the link inside share-sheet text`() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?si=xyz",
            dataSource.findSupportedUrl("Great song https://youtu.be/dQw4w9WgXcQ?si=xyz"),
        )
    }

    @Test
    fun `accepts mobile and music hosts`() {
        listOf(
            "https://m.youtube.com/watch?v=abc",
            "https://music.youtube.com/watch?v=abc",
            "https://www.youtube.com/watch?v=abc",
        ).forEach { assertEquals(it, dataSource.findSupportedUrl(it)) }
    }

    @Test
    fun `NewPipe is initialised by the first resolve, once`() {
        var created = 0
        val lazy = NewPipeYouTubeStreamDataSource {
            created++
            OfflineDownloader()
        }
        lazy.findSupportedUrl("https://youtu.be/abc")
        assertEquals(0, created)

        repeat(2) { assertTrue(runCatching { lazy.resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ") }.isFailure) }

        assertEquals(1, created)
    }

    @Test
    fun `picks the best M4A audio stream`() {
        val best = dataSource.pickBest(
            audio = listOf(
                audio("opus", MediaFormat.WEBMA_OPUS, 160),
                audio("low", MediaFormat.M4A, 48),
                audio("high", MediaFormat.M4A, 128),
            ),
            video = listOf(video("muxed", MediaFormat.MPEG_4)),
        )

        assertEquals("high", best?.id)
    }

    @Test
    fun `without an audio stream, picks the MP4 video with sound`() {
        val best = dataSource.pickBest(
            audio = emptyList(),
            video = listOf(
                video("webm", MediaFormat.WEBM),
                videoBuilder("silent", MediaFormat.MPEG_4).setIsVideoOnly(true).build(),
                video("muxed", MediaFormat.MPEG_4),
            ),
        )

        assertEquals("muxed", best?.id)
        assertEquals("mp4", best?.format?.suffix)
    }

    @Test
    fun `without a progressive stream, there is nothing to download`() {
        val best = dataSource.pickBest(
            audio = listOf(audioBuilder("dash", MediaFormat.M4A).setDeliveryMethod(DeliveryMethod.DASH).build()),
            video = listOf(videoBuilder("hls", MediaFormat.MPEG_4).setDeliveryMethod(DeliveryMethod.HLS).build()),
        )

        assertNull(best)
    }

    @Test
    fun `rejects other hosts and plain text`() {
        assertNull(dataSource.findSupportedUrl("https://vimeo.com/123"))
        assertNull(dataSource.findSupportedUrl("https://notyoutube.com/watch?v=abc"))
        assertNull(dataSource.findSupportedUrl("no link here"))
        assertNull(dataSource.findSupportedUrl(null))
    }

    private fun audio(id: String, format: MediaFormat, bitrate: Int) =
        audioBuilder(id, format).setAverageBitrate(bitrate).build()

    private fun audioBuilder(id: String, format: MediaFormat) = AudioStream.Builder()
        .setId(id)
        .setContent("https://example.com/$id", true)
        .setMediaFormat(format)
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)

    private fun video(id: String, format: MediaFormat) = videoBuilder(id, format).build()

    private fun videoBuilder(id: String, format: MediaFormat) = VideoStream.Builder()
        .setId(id)
        .setContent("https://example.com/$id", true)
        .setMediaFormat(format)
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        .setIsVideoOnly(false)
        .setResolution("360p")

    private class OfflineDownloader : Downloader() {
        override fun execute(request: Request): Response = throw IOException("offline")
    }
}
