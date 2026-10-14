package com.damianogiusti.slowdowner.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpFileDataSourceTest {

    @Test
    fun `when a stream url is cleartext then it is upgraded to https`() {
        assertEquals(
            "https://rr5---sn-x.googlevideo.com/videoplayback?id=1",
            OkHttpFileDataSource.secureUrl("http://rr5---sn-x.googlevideo.com/videoplayback?id=1"),
        )
    }

    @Test
    fun `when a stream url is already https then it is unchanged`() {
        val url = "https://rr5---sn-x.googlevideo.com/videoplayback?id=1"

        assertEquals(url, OkHttpFileDataSource.secureUrl(url))
    }
}
