package com.damianogiusti.slowdowner.data.library

import java.io.File

interface HttpFileDataSource {
    /** Blocking, but checks for coroutine cancellation between chunks. */
    suspend fun download(url: String, target: File, onProgress: (downloaded: Long, total: Long) -> Unit)
}
