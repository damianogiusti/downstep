package com.damianogiusti.slowdowner.domain.playlists

import com.damianogiusti.slowdowner.domain.library.LibraryTrack

/** A playlist and its tracks in playlist order. */
data class PlaylistWithTracks(val playlist: Playlist, val tracks: List<LibraryTrack>)
