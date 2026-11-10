package com.damianogiusti.slowdowner.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
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
    fun `a video link inside a playlist gets past the link check`() {
        val offline = NewPipeYouTubeStreamDataSource { OfflineDownloader() }

        val error = runCatching {
            offline.resolve("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ&start_radio=1")
        }.exceptionOrNull()

        // Offline, so it still fails, but on the network and not on the link.
        assertTrue(error !is ParsingException || error.message?.startsWith("URL not accepted") != true)
    }

    @Test
    fun `NewPipe accepts every playlist link once the playlist is dropped`() {
        val video = "dQw4w9WgXcQ"
        // The shapes seen in failed imports: radio, mix, playlist, YouTube Music mix and album.
        listOf(
            "https://www.youtube.com/watch?v=$video&list=RD$video&start_radio=1",
            "https://www.youtube.com/watch?v=$video&list=RDa1b2c3d4e5f&index=2",
            "https://www.youtube.com/watch?v=$video&list=PLa1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6&index=7&pp=a1b2c3",
            "https://music.youtube.com/watch?v=$video&list=RDAMVM$video",
            "https://music.youtube.com/watch?v=$video&list=OLAK5uy_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q",
        ).forEach { link ->
            assertEquals(link, video, YouTube.streamLHFactory.fromUrl(withoutPlaylist(link)).id)
        }
    }

    @Test
    fun `drops the playlist and keeps the rest of the link`() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc&start_radio=1",
            withoutPlaylist("https://www.youtube.com/watch?v=abc&list=RDabc&start_radio=1"),
        )
        assertEquals("https://m.youtube.com/watch?v=abc", withoutPlaylist("https://m.youtube.com/watch?list=PLx&v=abc"))
        assertEquals(
            "https://music.youtube.com/watch?v=abc#top",
            withoutPlaylist("https://music.youtube.com/watch?v=abc&list=OLAKx#top"),
        )
        assertEquals("https://youtu.be/abc?si=xyz", withoutPlaylist("https://youtu.be/abc?si=xyz"))
        assertEquals("https://youtu.be/abc", withoutPlaylist("https://youtu.be/abc"))
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
