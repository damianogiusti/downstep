package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.update.ReleaseDataSource
import com.damianogiusti.slowdowner.data.update.ReleaseDto
import java.io.File
import java.io.IOException

class FakeReleaseDataSource : ReleaseDataSource {
    var latest: ReleaseDto? = null
    var latestError: Exception? = null
    /** What a download writes, by URL. */
    val files = mutableMapOf<String, ByteArray>()

    override fun latest(): ReleaseDto? {
        latestError?.let { throw it }
        return latest
    }

    override suspend fun download(url: String, target: File, onProgress: (downloaded: Long, total: Long) -> Unit) {
        val bytes = files[url] ?: throw IOException("Offline")
        target.writeBytes(bytes)
        onProgress(bytes.size.toLong(), bytes.size.toLong())
    }
}
