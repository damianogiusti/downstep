package com.damianogiusti.slowdowner.ui.stems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.stems.AcknowledgeStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.CancelStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.DeleteTrackStemsUseCase
import com.damianogiusti.slowdowner.domain.stems.DownloadStemModelUseCase
import com.damianogiusti.slowdowner.domain.stems.ModelDownloadResult
import com.damianogiusti.slowdowner.domain.stems.ObserveSeparatedTracksUseCase
import com.damianogiusti.slowdowner.domain.stems.ObserveStemModelUseCase
import com.damianogiusti.slowdowner.domain.stems.ObserveStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.SeparationFailureReason
import com.damianogiusti.slowdowner.domain.stems.SeparationJobState
import com.damianogiusti.slowdowner.domain.stems.SeparationResult
import com.damianogiusti.slowdowner.domain.stems.SeparationStart
import com.damianogiusti.slowdowner.domain.stems.StartStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.StemModelState
import com.damianogiusti.slowdowner.domain.stems.StemPlatform
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Separates library tracks into instruments: the first-use download of the model, the one running job,
 * its banner and its outcome. Scoped to the activity like [com.damianogiusti.slowdowner.ui.library.ImportViewModel]:
 * a job outlives the screen that started it. Only apps that bind the stem repositories create it.
 */
class StemSeparationViewModel(
    private val useCases: StemUseCases,
    private val platform: StemPlatform,
) : ViewModel() {

    private val dialog = MutableStateFlow<StemDialog>(StemDialog.None)
    private val downloadPercent = MutableStateFlow<Int?>(null)

    // The track the open dialog was asked for, and the job that downloads the model.
    private var pendingTrackId: String? = null
    private var downloadJob: Job? = null

    // The notice is asked once per launch; a download that showed it counts too.
    private var isDeprecationAccepted = false
    private var lastJob: SeparationJobState = SeparationJobState.Idle
    private var isFinishedHandled = false

    private val _messages = MutableSharedFlow<UiMessage>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val state: StateFlow<StemSeparationUiState> = combine(
        useCases.observeSeparatedTracks(),
        useCases.observeSeparation().onEach(::onSeparationState),
        downloadPercent,
        dialog,
    ) { separatedTrackIds, separation, downloadPercent, dialog ->
        StemSeparationUiState(
            separatedTrackIds = separatedTrackIds,
            progress = when {
                downloadPercent != null -> StemProgress.DownloadingModel(downloadPercent)
                separation is SeparationJobState.Running -> StemProgress.Separating((separation.progress * PERCENT).roundToInt())
                else -> StemProgress.None
            },
            dialog = dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StemSeparationUiState())

    /** "Separate instruments" or "Separate again" in a track's menu. */
    fun onSeparateClicked(trackId: String) {
        if (isBusy()) {
            _messages.tryEmit(UiMessage(Res.string.stems_busy))
            return
        }
        viewModelScope.launch {
            when {
                useCases.observeModel().first() == StemModelState.NotDownloaded -> openModelDialog(trackId)
                platform.isDeprecated && !isDeprecationAccepted -> {
                    pendingTrackId = trackId
                    dialog.value = StemDialog.ConfirmDeprecatedPlatform
                }
                else -> start(trackId)
            }
        }
    }

    /** "Download" in the model dialog. The separation of the chosen track starts when the model is there. */
    fun onModelDownloadConfirmed() {
        val trackId = pendingTrackId ?: return
        closeDialog()
        // The dialog showed the notice, if there is one.
        isDeprecationAccepted = true
        downloadPercent.value = 0
        downloadJob = viewModelScope.launch {
            val result = try {
                // Called on the repository's IO thread: StateFlow updates are thread-safe, and equal percents are dropped.
                useCases.downloadModel { progress -> downloadPercent.value = (progress * PERCENT).toInt() }
            } finally {
                downloadPercent.value = null
            }
            when (result) {
                ModelDownloadResult.Success -> start(trackId)
                ModelDownloadResult.NetworkError -> _messages.tryEmit(UiMessage(Res.string.stems_download_error_network))
                ModelDownloadResult.Corrupted -> _messages.tryEmit(UiMessage(Res.string.stems_download_error_corrupted))
                is ModelDownloadResult.Failure -> _messages.tryEmit(UiMessage(Res.string.stems_download_error_generic))
            }
        }
    }

    /** "Cancel", Esc or a click outside the model dialog. */
    fun onModelDownloadDismissed() = closeDialog()

    /** "Separate anyway" in the Intel Mac notice. */
    fun onDeprecationAccepted() {
        val trackId = pendingTrackId ?: return
        closeDialog()
        isDeprecationAccepted = true
        viewModelScope.launch { start(trackId) }
    }

    fun onDeprecationDismissed() = closeDialog()

    /** The banner's Cancel: stops the download or the separation, whichever runs. */
    fun onCancelClicked() {
        val download = downloadJob
        if (download?.isActive == true) {
            download.cancel()
            return
        }
        viewModelScope.launch { useCases.cancelSeparation() }
    }

    /** Removes the stems of a track once the user confirmed. */
    fun onDeleteStemsConfirmed(trackId: String) {
        viewModelScope.launch { useCases.deleteTrackStems(trackId) }
    }

    private suspend fun start(trackId: String) {
        when (useCases.startSeparation(trackId)) {
            SeparationStart.Started -> Unit
            SeparationStart.Busy -> _messages.tryEmit(UiMessage(Res.string.stems_busy))
            // The model went away since it was checked.
            SeparationStart.ModelMissing -> openModelDialog(trackId)
            SeparationStart.TrackMissing -> _messages.tryEmit(UiMessage(Res.string.stems_error_removed))
        }
    }

    private fun openModelDialog(trackId: String) {
        pendingTrackId = trackId
        dialog.value = StemDialog.DownloadModel(isDeprecatedPlatform = platform.isDeprecated)
    }

    private fun closeDialog() {
        pendingTrackId = null
        dialog.value = StemDialog.None
    }

    private fun isBusy() = downloadJob?.isActive == true || lastJob is SeparationJobState.Running

    // The outcome is told once, then acknowledged so a screen that opens later does not tell it again.
    private fun onSeparationState(state: SeparationJobState) {
        lastJob = state
        if (state !is SeparationJobState.Finished) {
            isFinishedHandled = false
            return
        }
        if (isFinishedHandled) return
        isFinishedHandled = true
        when (val result = state.result) {
            SeparationResult.Separated -> _messages.tryEmit(UiMessage(Res.string.stems_ready))
            is SeparationResult.Failed -> _messages.tryEmit(UiMessage(result.reason.text()))
            // The user asked for it: the banner going away is the answer.
            SeparationResult.Cancelled -> Unit
        }
        viewModelScope.launch { useCases.acknowledgeSeparation() }
    }

    private fun SeparationFailureReason.text() = when (this) {
        SeparationFailureReason.UNREADABLE_TRACK -> Res.string.stems_error_unreadable
        SeparationFailureReason.TOO_LONG -> Res.string.stems_error_too_long
        SeparationFailureReason.MODEL_ERROR -> Res.string.stems_error_model
        SeparationFailureReason.WRITE_ERROR -> Res.string.stems_error_write
        SeparationFailureReason.TRACK_REMOVED -> Res.string.stems_error_removed
        SeparationFailureReason.UNEXPECTED -> Res.string.stems_error_unexpected
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PERCENT = 100
    }
}

/** The stem use-cases of [StemSeparationViewModel], grouped to keep its constructor readable. */
class StemUseCases(
    val observeSeparatedTracks: ObserveSeparatedTracksUseCase,
    val observeModel: ObserveStemModelUseCase,
    val observeSeparation: ObserveStemSeparationUseCase,
    val startSeparation: StartStemSeparationUseCase,
    val cancelSeparation: CancelStemSeparationUseCase,
    val acknowledgeSeparation: AcknowledgeStemSeparationUseCase,
    val downloadModel: DownloadStemModelUseCase,
    val deleteTrackStems: DeleteTrackStemsUseCase,
)
