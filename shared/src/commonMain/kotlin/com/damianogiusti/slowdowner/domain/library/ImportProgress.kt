package com.damianogiusti.slowdowner.domain.library

sealed interface ImportProgress {
    data object Resolving : ImportProgress
    data class Downloading(val title: String, val downloadedBytes: Long, val totalBytes: Long) : ImportProgress
}
