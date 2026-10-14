package com.damianogiusti.slowdowner.data.library

data class LibraryTrackDto(
    val id: String,
    val title: String,
    val filePath: String,
    val sourceUrl: String?,
    val addedAt: Long,
)
