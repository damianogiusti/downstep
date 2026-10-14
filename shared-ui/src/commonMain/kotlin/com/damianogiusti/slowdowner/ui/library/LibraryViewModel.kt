package com.damianogiusti.slowdowner.ui.library

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.library.ObserveLibraryUseCase
import com.damianogiusti.slowdowner.domain.library.RemoveTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playlists.ObservePlaylistsUseCase
import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.domain.playlists.PlaylistResult
import com.damianogiusti.slowdowner.domain.practice.ObservePracticeStatesUseCase
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.playlists.PlaylistEditUseCases
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    observeLibrary: ObserveLibraryUseCase,
    observePracticeStates: ObservePracticeStatesUseCase,
    observePlaylists: ObservePlaylistsUseCase,
    observeNowPlaying: ObserveNowPlayingUseCase,
    private val removeTrack: RemoveTrackUseCase,
    private val playlists: PlaylistEditUseCases,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<LibraryUiState> = combine(
        observeLibrary(),
        observePracticeStates(),
        observePlaylists(),
        observeNowPlaying(),
        query,
    ) { tracks, practice, playlists, nowPlaying, query ->
        LibraryUiState(
            tracks = tracks
                .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
                .map { TrackItem(it, practice[it.id]) },
            totalTracks = tracks.size,
            playlists = playlists,
            query = query,
            nowPlayingId = nowPlaying?.id,
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun onQueryChanged(text: String) {
        query.value = text
    }

    fun onTrackRemoveConfirmed(track: LibraryTrack) {
        viewModelScope.launch { removeTrack(track) }
    }

    fun onCreatePlaylist(name: String) {
        viewModelScope.launch { reportInvalidName(playlists.create(name)) }
    }

    fun onRenamePlaylist(playlist: Playlist, name: String) {
        viewModelScope.launch { reportInvalidName(playlists.rename(playlist.id, name)) }
    }

    fun onDeletePlaylistConfirmed(playlist: Playlist) {
        viewModelScope.launch { playlists.delete(playlist.id) }
    }

    private suspend fun reportInvalidName(result: PlaylistResult) {
        if (result == PlaylistResult.InvalidName) _messages.send(UiMessage(Res.string.error_playlist_name))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class LibraryUiState(
    val tracks: List<TrackItem> = emptyList(),
    val totalTracks: Int = 0,
    val playlists: List<Playlist> = emptyList(),
    val query: String = "",
    val nowPlayingId: String? = null,
    /** False until the first emission, so the empty state does not flash at launch. */
    val isLoaded: Boolean = false,
)

data class TrackItem(val track: LibraryTrack, val practice: PracticeSettings?)
