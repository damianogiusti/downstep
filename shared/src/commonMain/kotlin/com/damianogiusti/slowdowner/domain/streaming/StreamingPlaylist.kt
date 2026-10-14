package com.damianogiusti.slowdowner.domain.streaming

data class StreamingPlaylist(
    val id: String,
    val ownerId: String,
    val name: String,
    val trackCount: Int,
)
