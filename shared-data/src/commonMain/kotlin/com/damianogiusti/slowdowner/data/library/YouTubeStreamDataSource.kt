package com.damianogiusti.slowdowner.data.library


interface YouTubeStreamDataSource {
    /** Extracts a YouTube link from free text, e.g. what the share sheet sends. */
    fun findSupportedUrl(text: String?): String?

    /** Blocking. Throws [com.damianogiusti.slowdowner.data.library.StreamUnavailableException]. */
    fun resolve(url: String): ResolvedAudioDto
}
