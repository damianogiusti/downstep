package com.damianogiusti.slowdowner.ui.backup

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.backup.BackupExportResult
import com.damianogiusti.slowdowner.domain.backup.BackupFile
import com.damianogiusti.slowdowner.domain.backup.BackupProgress
import com.damianogiusti.slowdowner.domain.backup.ExportLibraryBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.ExportPlaylistBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.ExportTrackBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.FindPlaylistConflictsUseCase
import com.damianogiusti.slowdowner.domain.backup.PlaylistConflictChoice
import com.damianogiusti.slowdowner.domain.backup.RestoreBackupUseCase
import com.damianogiusti.slowdowner.domain.backup.RestoreResult
import com.damianogiusti.slowdowner.domain.backup.SaveBackupUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.common.UiPlural
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backup export and restore. Scoped to the activity: the pickers answer there, and a backup
 * opened from another app arrives whatever the screen.
 */
class BackupViewModel(private val backup: BackupUseCases) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private var job: Job? = null

    fun onExportTrack(track: LibraryTrack) = export { backup.exportTrack(track, it) }

    fun onExportLibrary() = export { backup.exportLibrary(it) }

    fun onExportPlaylist(playlist: Playlist) = export { backup.exportPlaylist(playlist, it) }

    fun onExportCancelClicked() {
        job?.cancel()
        _state.update { if (it is BackupUiState.Exporting) BackupUiState.Idle else it }
    }

    /** The share sheet took over, or the user closed the dialog. */
    fun onReadyDismissed() {
        _state.update { if (it is BackupUiState.Ready) BackupUiState.Idle else it }
    }

    /** The dialog stays up while the picker is open, so a cancelled pick returns to it. */
    fun onSaveTargetPicked(contentUri: String) {
        val file = (_state.value as? BackupUiState.Ready)?.file ?: return
        _state.value = BackupUiState.Idle
        viewModelScope.launch {
            val message = if (backup.save(file, contentUri)) Res.string.export_saved else Res.string.error_export_save
            _messages.send(UiMessage(message, listOf(file.name)))
        }
    }

    /**
     * A file opened from another app. Any app can send one, and the intent filter also
     * matches generic binary files: ask before adding anything to the library.
     */
    fun onBackupOpened(contentUri: String) {
        if (isBusy()) return
        _state.value = BackupUiState.ConfirmRestore(contentUri)
    }

    fun onRestoreConfirmed() {
        val pending = _state.value as? BackupUiState.ConfirmRestore ?: return
        _state.value = BackupUiState.Idle
        onRestorePicked(pending.contentUri)
    }

    fun onRestoreDismissed() {
        _state.update { if (it is BackupUiState.ConfirmRestore) BackupUiState.Idle else it }
    }

    /** A backup the user picked in the import sheet. Asks first if a playlist would clash. */
    fun onRestorePicked(contentUri: String) {
        if (isBusy()) return
        _state.value = BackupUiState.Restoring(done = 0, total = 0)
        job = viewModelScope.launch {
            val conflicts = backup.findPlaylistConflicts(contentUri)
            if (conflicts.isEmpty()) {
                // Nothing to ask. Copy still guards a playlist created in the meantime.
                restore(contentUri, PlaylistConflictChoice.Copy)
            } else {
                _state.value = BackupUiState.PlaylistConflict(contentUri, conflicts)
            }
        }
    }

    fun onPlaylistConflictResolved(choice: PlaylistConflictChoice) {
        val pending = _state.value as? BackupUiState.PlaylistConflict ?: return
        _state.value = BackupUiState.Restoring(done = 0, total = 0)
        job = viewModelScope.launch { restore(pending.contentUri, choice) }
    }

    /** Cancel: the restore has not started, so nothing changes. */
    fun onPlaylistConflictDismissed() {
        _state.update { if (it is BackupUiState.PlaylistConflict) BackupUiState.Idle else it }
    }

    private suspend fun restore(contentUri: String, onConflict: PlaylistConflictChoice) {
        val result = backup.restore(contentUri, onConflict) { progress ->
            _state.update { if (it is BackupUiState.Restoring) progress.toRestoring() else it }
        }
        _state.value = BackupUiState.Idle
        _messages.send(messageFor(result))
    }

    private fun export(run: suspend (onProgress: (BackupProgress) -> Unit) -> BackupExportResult) {
        if (isBusy()) return
        val cancelled = job
        _state.value = BackupUiState.Exporting(done = 0, total = 0)
        job = viewModelScope.launch {
            // A cancelled export stops only between files: let it close its zip first.
            cancelled?.cancelAndJoin()
            // Progress comes from the IO thread; StateFlow updates are thread-safe.
            val result = run { progress ->
                _state.update { if (it is BackupUiState.Exporting) progress.toExporting() else it }
            }
            when (result) {
                is BackupExportResult.Success -> _state.value = BackupUiState.Ready(result.file)
                BackupExportResult.NothingToExport -> exportFailed(UiMessage(Res.string.error_backup_nothing))
                is BackupExportResult.Failure -> exportFailed(
                    UiMessage(Res.string.error_backup_export, listOf(result.cause.message.orEmpty())),
                )
            }
        }
    }

    private suspend fun exportFailed(message: UiMessage) {
        _state.value = BackupUiState.Idle
        _messages.send(message)
    }

    private fun isBusy(): Boolean {
        val state = _state.value
        val isBusy = state is BackupUiState.Exporting || state is BackupUiState.Restoring
        if (isBusy) _messages.trySend(UiMessage(Res.string.error_backup_running))
        return isBusy
    }

    private fun messageFor(result: RestoreResult): UiMessage = when (result) {
        is RestoreResult.Success -> restoredMessage(result)
        RestoreResult.InvalidFile -> UiMessage(Res.string.error_backup_invalid)
        is RestoreResult.UnsupportedVersion -> UiMessage(Res.string.error_backup_version)
        is RestoreResult.Failure -> UiMessage(Res.string.error_backup_restore, listOf(result.cause.message.orEmpty()))
    }

    /** "Restored 12 tracks (3 already in the library) and 1 playlist." Empty parts are left out. */
    private fun restoredMessage(result: RestoreResult.Success): UiMessage {
        val tracks = UiPlural(Res.plurals.backup_restored_tracks, result.tracksAdded)
        val playlists = UiPlural(Res.plurals.playlists_count, result.playlistsRestored)
        val hasSkipped = result.tracksSkipped > 0
        val hasPlaylists = result.playlistsRestored > 0
        return when {
            hasSkipped && hasPlaylists ->
                UiMessage(Res.string.backup_restored_skipped_playlists, listOf(tracks, result.tracksSkipped, playlists))
            hasSkipped -> UiMessage(Res.string.backup_restored_skipped, listOf(tracks, result.tracksSkipped))
            hasPlaylists -> UiMessage(Res.string.backup_restored_playlists, listOf(tracks, playlists))
            else -> UiMessage(Res.string.backup_restored, listOf(tracks))
        }
    }

    private fun BackupProgress.toExporting() = BackupUiState.Exporting(done, total)

    private fun BackupProgress.toRestoring() = BackupUiState.Restoring(done, total)
}

/** Backup use-cases, grouped to keep the constructor readable. */
class BackupUseCases(
    val exportTrack: ExportTrackBackupUseCase,
    val exportLibrary: ExportLibraryBackupUseCase,
    val exportPlaylist: ExportPlaylistBackupUseCase,
    val save: SaveBackupUseCase,
    val findPlaylistConflicts: FindPlaylistConflictsUseCase,
    val restore: RestoreBackupUseCase,
)

sealed interface BackupUiState {
    data object Idle : BackupUiState
    /** [total] tracks to copy; 0 while counting, or when there is no audio. */
    data class Exporting(val done: Int, val total: Int) : BackupUiState
    data class Restoring(val done: Int, val total: Int) : BackupUiState
    data class Ready(val file: BackupFile) : BackupUiState
    /** Opened from another app, waiting for the user to confirm. */
    data class ConfirmRestore(val contentUri: String) : BackupUiState
    /** [names]: backup playlists whose name is taken by a different playlist. Waiting for a choice. */
    data class PlaylistConflict(val contentUri: String, val names: List<String>) : BackupUiState
}
