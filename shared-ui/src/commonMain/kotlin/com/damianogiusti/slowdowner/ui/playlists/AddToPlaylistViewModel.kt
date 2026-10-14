package com.damianogiusti.slowdowner.ui.playlists

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.playlists.CreatePlaylistUseCase
import com.damianogiusti.slowdowner.domain.playlists.ObservePlaylistMembershipUseCase
import com.damianogiusti.slowdowner.domain.playlists.ObservePlaylistsUseCase
import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.domain.playlists.PlaylistResult
import com.damianogiusti.slowdowner.ui.common.UiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which playlists hold a track, with toggles to change that. One instance for the whole
 * activity: the sheet sets the track instead of creating a ViewModel per track.
 */
class AddToPlaylistViewModel(
    observePlaylists: ObservePlaylistsUseCase,
    private val observeMembership: ObservePlaylistMembershipUseCase,
    private val createPlaylist: CreatePlaylistUseCase,
    private val trackEdits: PlaylistTrackUseCases,
) : ViewModel() {

    private val trackId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AddToPlaylistUiState> = combine(
        observePlaylists(),
        trackId.flatMapLatest { id -> id?.let { observeMembership(it) } ?: flowOf(emptySet()) },
    ) { playlists, ids ->
        AddToPlaylistUiState(playlists = playlists, memberOf = ids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddToPlaylistUiState())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun onTrackChanged(trackId: String) {
        this.trackId.value = trackId
    }

    fun onToggled(playlist: Playlist) {
        val track = trackId.value ?: return
        val isMember = playlist.id in state.value.memberOf
        viewModelScope.launch {
            if (isMember) {
                trackEdits.remove(playlist.id, track)
            } else if (!trackEdits.add(playlist.id, track)) {
                _messages.send(UiMessage(Res.string.error_playlist_add))
            }
        }
    }

    /** Creates the playlist with the track already in it. */
    fun onCreate(name: String) {
        val track = trackId.value ?: return
        viewModelScope.launch {
            when (val result = createPlaylist(name)) {
                is PlaylistResult.Done -> if (!trackEdits.add(result.playlistId, track)) {
                    _messages.send(UiMessage(Res.string.error_playlist_add))
                }
                PlaylistResult.InvalidName -> _messages.send(UiMessage(Res.string.error_playlist_name))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class AddToPlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val memberOf: Set<Long> = emptySet(),
)
