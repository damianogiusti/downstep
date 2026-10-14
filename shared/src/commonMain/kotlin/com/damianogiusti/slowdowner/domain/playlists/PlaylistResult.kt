package com.damianogiusti.slowdowner.domain.playlists

sealed interface PlaylistResult {
    data class Done(val playlistId: Long) : PlaylistResult
    data object InvalidName : PlaylistResult
}
