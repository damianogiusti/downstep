package com.damianogiusti.slowdowner.data.update

import java.io.File

interface ReleaseDataSource {
    /** The newest published release, or null if there is none yet. Blocking; throws IOException. */
    fun latest(): ReleaseDto?

    /** Blocking, but checks for coroutine cancellation while it copies. */
    suspend fun download(url: String, target: File, onProgress: (downloaded: Long, total: Long) -> Unit)
}
