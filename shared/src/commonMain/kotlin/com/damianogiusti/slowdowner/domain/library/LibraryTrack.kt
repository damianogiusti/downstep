package com.damianogiusti.slowdowner.domain.library

data class LibraryTrack(
    val id: String,
    val title: String,
    val filePath: String,
    val sourceUrl: String?,
    val addedAt: Long,
)
