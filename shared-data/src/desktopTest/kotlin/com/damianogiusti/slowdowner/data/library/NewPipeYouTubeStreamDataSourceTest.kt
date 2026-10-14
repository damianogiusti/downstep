package com.damianogiusti.slowdowner.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun `rejects other hosts and plain text`() {
        assertNull(dataSource.findSupportedUrl("https://vimeo.com/123"))
        assertNull(dataSource.findSupportedUrl("https://notyoutube.com/watch?v=abc"))
        assertNull(dataSource.findSupportedUrl("no link here"))
        assertNull(dataSource.findSupportedUrl(null))
    }
}
