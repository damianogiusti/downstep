package com.damianogiusti.slowdowner.domain.source

sealed class AudioSource {
    data class LocalFile(val path: String) : AudioSource()
    data class SpotifyTrack(val uri: String) : AudioSource()
}
