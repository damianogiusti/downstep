package com.damianogiusti.slowdowner.ui.playlists

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.library.ObserveLibraryUseCase
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playlists.AddTrackToPlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.CreatePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.DeletePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.ObservePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.domain.playlists.PlaylistResult
import com.damianogiusti.slowdowner.domain.playlists.RemoveTrackFromPlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.RenamePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.ReorderPlaylistUseCase
import com.damianogiusti.slowdowner.domain.practice.ObservePracticeStatesUseCase
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.library.TrackItem
import com.damianogiusti.slowdowner.ui.navigation.PlaylistRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    savedStateHandle: SavedStateHandle,
    observePlaylist: ObservePlaylistUseCase,
    observeLibrary: ObserveLibraryUseCase,
    observePracticeStates: ObservePracticeStatesUseCase,
    observeNowPlaying: ObserveNowPlayingUseCase,
    private val edits: PlaylistEditUseCases,
    private val trackEdits: PlaylistTrackUseCases,
) : ViewModel() {

    private val playlistId = savedStateHandle.toRoute<PlaylistRoute>().playlistId

    val state: StateFlow<PlaylistUiState> = combine(
        observePlaylist(playlistId),
        observeLibrary(),
        observePracticeStates(),
        observeNowPlaying(),
    ) { playlist, library, practice, nowPlaying ->
        PlaylistUiState(
            playlist = playlist?.playlist,
            tracks = playlist?.tracks.orEmpty().map { TrackItem(it, practice[it.id]) },
            libraryTracks = library,
            nowPlayingId = nowPlaying?.id,
            isLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistUiState())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun onRename(name: String) {
        viewModelScope.launch {
            if (edits.rename(playlistId, name) == PlaylistResult.InvalidName) {
                _messages.send(UiMessage(Res.string.error_playlist_name))
            }
        }
    }

    fun onDeleteConfirmed() {
        viewModelScope.launch { edits.delete(playlistId) }
    }

    fun onTrackToggled(track: LibraryTrack, isInPlaylist: Boolean) {
        viewModelScope.launch {
            if (isInPlaylist) {
                trackEdits.remove(playlistId, track.id)
            } else if (!trackEdits.add(playlistId, track.id)) {
                _messages.send(UiMessage(Res.string.error_playlist_add))
            }
        }
    }

    fun onRemoveTrack(track: LibraryTrack) {
        viewModelScope.launch { trackEdits.remove(playlistId, track.id) }
    }

    fun onReorder(trackIds: List<String>) {
        viewModelScope.launch { trackEdits.reorder(playlistId, trackIds) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Playlist edits shared by the library and playlist screens. */
class PlaylistEditUseCases(
    val create: CreatePlaylistUseCase,
    val rename: RenamePlaylistUseCase,
    val delete: DeletePlaylistUseCase,
)

class PlaylistTrackUseCases(
    val add: AddTrackToPlaylistUseCase,
    val remove: RemoveTrackFromPlaylistUseCase,
    val reorder: ReorderPlaylistUseCase,
)

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val tracks: List<TrackItem> = emptyList(),
    /** For the "add tracks" sheet. */
    val libraryTracks: List<LibraryTrack> = emptyList(),
    val nowPlayingId: String? = null,
    val isLoaded: Boolean = false,
) {
    /** Loaded but gone: deleted here or from another screen. */
    val isDeleted get() = isLoaded && playlist == null
    /** Tracks whose saved speed, pitch or loop differ from the defaults (within engine rounding). */
    val customizedTrackCount get() = tracks.count { item -> item.practice?.matches(PracticeSettings()) == false }
    val trackIds get() = tracks.map { it.track.id }.toSet()
}
