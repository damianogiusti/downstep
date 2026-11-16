package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.library.HttpFileDataSource
import java.io.File

/** Writes the bytes given for a url, like the real source writes a file. Never touches the network. */
class FakeHttpFileDataSource(private val bytesByUrl: Map<String, ByteArray> = emptyMap()) : HttpFileDataSource {

    var error: Exception? = null
    /** Runs while the file is being "downloaded", after the first progress step. */
    var duringDownload: (suspend () -> Unit)? = null
    val downloadedUrls = mutableListOf<String>()

    override suspend fun download(url: String, target: File, onProgress: (downloaded: Long, total: Long) -> Unit) {
        downloadedUrls += url
        error?.let { throw it }
        val bytes = bytesByUrl.getValue(url)
        onProgress(bytes.size / 2L, bytes.size.toLong())
        duringDownload?.invoke()
        target.writeBytes(bytes)
        onProgress(bytes.size.toLong(), bytes.size.toLong())
    }
}
