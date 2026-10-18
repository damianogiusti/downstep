package com.damianogiusti.slowdowner.ui.library

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.library.FindImportableUrlUseCase
import com.damianogiusti.slowdowner.domain.library.ImportDocumentUseCase
import com.damianogiusti.slowdowner.domain.library.ImportProgress
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.library.ImportTrackFromUrlUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.ui.common.isDesktop
import com.damianogiusti.slowdowner.ui.common.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Imports from files and links. Scoped to the activity: shares arrive whatever the screen. */
class ImportViewModel(
    private val findImportableUrl: FindImportableUrlUseCase,
    private val importTrackFromUrl: ImportTrackFromUrlUseCase,
    private val importDocument: ImportDocumentUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _importedTracks = Channel<LibraryTrack>(Channel.BUFFERED)
    /** Each successfully imported track, to open it right away. */
    val importedTracks: Flow<LibraryTrack> = _importedTracks.receiveAsFlow()

    private var importJob: Job? = null

    /** Text shared from another app or pasted by the user, e.g. a YouTube link. */
    fun onTextShared(text: String?) {
        val url = findImportableUrl(text)
        if (url == null) {
            _messages.trySend(UiMessage(if (isDesktop) Res.string.error_unsupported_link_desktop else Res.string.error_unsupported_link))
            return
        }
        runImport { importTrackFromUrl(url, ::onImportProgress) }
    }

    fun onDocumentPicked(contentUri: String) {
        runImport { importDocument(contentUri) }
    }

    private fun runImport(block: suspend () -> ImportResult) {
        if (importJob?.isActive == true) {
            _messages.trySend(UiMessage(Res.string.error_import_running))
            return
        }
        importJob = viewModelScope.launch {
            _state.value = ImportUiState.Resolving
            val result = block()
            _state.value = ImportUiState.Idle
            when (result) {
                is ImportResult.Success -> _importedTracks.send(result.track)
                ImportResult.Unavailable -> _messages.send(UiMessage(Res.string.error_unavailable))
                ImportResult.NetworkError -> _messages.send(UiMessage(Res.string.error_network))
                is ImportResult.Failure -> _messages.send(
                    UiMessage(Res.string.error_generic, listOf(result.cause.message.orEmpty())),
                )
            }
        }
    }

    // Called on the repository's IO thread, once per downloaded chunk; StateFlow updates are thread-safe.
    // Only whole-percent steps are published: each one redraws the library.
    private fun onImportProgress(progress: ImportProgress) {
        _state.value = when (progress) {
            ImportProgress.Resolving -> ImportUiState.Resolving
            is ImportProgress.Downloading -> {
                val fraction = progress.totalBytes.takeIf { it > 0 }?.let { progress.downloadedBytes.toFloat() / it }
                val shown = _state.value as? ImportUiState.Downloading
                if (shown != null && shown.title == progress.title && fraction.wholePercent() == shown.fraction.wholePercent()) return
                ImportUiState.Downloading(title = progress.title, fraction = fraction)
            }
        }
    }

    private fun Float?.wholePercent(): Int? = this?.let { (it * 100).toInt() }
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Resolving : ImportUiState
    /** [fraction] is null while the total size is still unknown. */
    data class Downloading(val title: String, val fraction: Float?) : ImportUiState
}
