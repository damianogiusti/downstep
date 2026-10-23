package com.damianogiusti.slowdowner.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.support.ObserveSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.update.AppRelease
import com.damianogiusti.slowdowner.domain.update.InstallUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.ObserveAvailableUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.ReportUpdatePromptShownUseCase
import com.damianogiusti.slowdowner.domain.update.UpdateInstallResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The "new version" dialog and banner. The dialog opens by itself once per launch, after the support
 * prompt if that one is due; the banner opens it again. Desktop only, like the support prompt.
 */
class UpdateViewModel(
    observeAvailableUpdate: ObserveAvailableUpdateUseCase,
    observeSupportPrompt: ObserveSupportPromptUseCase,
    private val installUpdate: InstallUpdateUseCase,
    private val reportUpdatePromptShown: ReportUpdatePromptShownUseCase,
) : ViewModel() {

    // Open at the start: the first update found shows itself.
    private val isDialogRequested = MutableStateFlow(true)
    private val install = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    private var installJob: Job? = null
    private var isShownReported = false
    private var availableRelease: AppRelease? = null

    val state: StateFlow<UpdateUiState> = combine(
        observeAvailableUpdate().onEach { availableRelease = it?.release },
        observeSupportPrompt(),
        isDialogRequested,
        install,
    ) { update, supportPrompt, isDialogRequested, install ->
        if (update == null) return@combine UpdateUiState.None
        val release = update.release
        val isDialogOpen = isDialogRequested && supportPrompt == null
        if (isDialogOpen) reportShownOnce(release)
        UpdateUiState.Available(
            version = release.version.toString(),
            installedVersion = update.installed.toString(),
            pageUrl = release.pageUrl,
            canInstall = release.canInstall,
            notes = release.notes,
            isDialogOpen = isDialogOpen,
            install = install,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateUiState.None)

    fun onBannerClicked() {
        isDialogRequested.value = true
    }

    /** "Later", Esc or a click outside. A download keeps the dialog open: it has its own Cancel. */
    fun onLaterClicked() {
        if (install.value is UpdateInstallState.Downloading || install.value == UpdateInstallState.Restarting) return
        isDialogRequested.value = false
        install.value = UpdateInstallState.Idle
    }

    /** The page has the download: the dialog has nothing more to do. */
    fun onDownloadPageOpened() {
        isDialogRequested.value = false
        install.value = UpdateInstallState.Idle
    }

    fun onInstallClicked() {
        val release = availableRelease ?: return
        if (installJob?.isActive == true) return
        install.value = UpdateInstallState.Downloading(0)
        installJob = viewModelScope.launch {
            val result = installUpdate(release) { progress ->
                install.value = UpdateInstallState.Downloading((progress * 100).toInt())
            }
            install.value = when (result) {
                UpdateInstallResult.ReadyToRestart -> UpdateInstallState.Restarting
                UpdateInstallResult.NetworkError -> UpdateInstallState.NetworkError
                UpdateInstallResult.Failure -> UpdateInstallState.Failed
            }
        }
    }

    fun onCancelClicked() {
        installJob?.cancel()
        install.value = UpdateInstallState.Idle
    }

    private fun reportShownOnce(release: AppRelease) {
        if (isShownReported) return
        isShownReported = true
        reportUpdatePromptShown(release)
    }
}
