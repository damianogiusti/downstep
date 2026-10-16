package com.damianogiusti.slowdowner.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
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
    fun `rejects other hosts and plain text`() {
        assertNull(dataSource.findSupportedUrl("https://vimeo.com/123"))
        assertNull(dataSource.findSupportedUrl("https://notyoutube.com/watch?v=abc"))
        assertNull(dataSource.findSupportedUrl("no link here"))
        assertNull(dataSource.findSupportedUrl(null))
    }

    private class OfflineDownloader : Downloader() {
        override fun execute(request: Request): Response = throw IOException("offline")
    }
}
