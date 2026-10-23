package com.damianogiusti.slowdowner.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.support.ObserveSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.update.AppRelease
import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.update.InstallUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.ObserveAvailableUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.ReportUpdatePromptShownUseCase
import com.damianogiusti.slowdowner.domain.update.RestartIntoUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.UpdateInstallResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The "new version" dialog and banner. The dialog opens by itself once per launch, after the support
 * prompt if that one is due; the banner opens it again. Desktop only, like the support prompt.
 */
class UpdateViewModel(
    observeAvailableUpdate: ObserveAvailableUpdateUseCase,
    observeSupportPrompt: ObserveSupportPromptUseCase,
    private val installUpdate: InstallUpdateUseCase,
    private val restartIntoUpdate: RestartIntoUpdateUseCase,
    private val reportUpdatePromptShown: ReportUpdatePromptShownUseCase,
) : ViewModel() {

    // Open at the start: the first update found shows itself.
    private val isDialogRequested = MutableStateFlow(true)
    private val install = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    private var installJob: Job? = null
    private var restartJob: Job? = null
    private var isShownReported = false
    private var availableRelease: AppRelease? = null
    // The version the ready update is: a newer release found later must not swap in this one.
    private var preparedVersion: AppVersion? = null

    val state: StateFlow<UpdateUiState> = combine(
        observeAvailableUpdate().onEach { update ->
            availableRelease = update?.release
            if (install.value == UpdateInstallState.ReadyToRestart && update?.release?.version != preparedVersion) {
                install.value = UpdateInstallState.Idle
            }
        },
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
            isDialogOpen = isDialogOpen,
            install = install,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateUiState.None)

    fun onBannerClicked() {
        isDialogRequested.value = true
    }

    /**
     * "Later", Esc or a click outside. A download keeps the dialog open: it has its own Cancel. A
     * downloaded update stays ready: the banner opens the dialog on "Restart now" again.
     */
    fun onLaterClicked() {
        if (install.value is UpdateInstallState.Downloading || install.value == UpdateInstallState.Restarting) return
        // The swap is starting: the app quits in a moment, the dialog stays until then.
        if (restartJob?.isActive == true) return
        isDialogRequested.value = false
        if (install.value != UpdateInstallState.ReadyToRestart) install.value = UpdateInstallState.Idle
    }

    /** The page has the download: the dialog has nothing more to do. */
    fun onDownloadPageOpened() {
        isDialogRequested.value = false
        install.value = UpdateInstallState.Idle
    }

    fun onInstallClicked() {
        val release = availableRelease ?: return
        val current = install.value
        val canStart = current == UpdateInstallState.Idle || current == UpdateInstallState.NetworkError ||
            current == UpdateInstallState.Failed
        if (!canStart || installJob?.isActive == true) return
        install.value = UpdateInstallState.Downloading(0)
        val cancelled = installJob
        installJob = viewModelScope.launch {
            // A cancelled download may still be writing its file: the new one starts after it is gone.
            cancelled?.cancelAndJoin()
            val result = installUpdate(release) { progress ->
                // From the IO thread, and maybe after Cancel: only a download in progress moves on.
                install.update { if (it is UpdateInstallState.Downloading) UpdateInstallState.Downloading((progress * 100).toInt()) else it }
            }
            if (result == UpdateInstallResult.ReadyToRestart) preparedVersion = release.version
            install.value = when (result) {
                UpdateInstallResult.ReadyToRestart -> UpdateInstallState.ReadyToRestart
                UpdateInstallResult.NetworkError -> UpdateInstallState.NetworkError
                UpdateInstallResult.Failure -> UpdateInstallState.Failed
            }
        }
    }

    /**
     * The user read that the app reopens by itself. Only now the swap starts, and only once it runs the
     * state turns to Restarting, which quits the app: quitting first would leave nothing to swap it.
     */
    fun onRestartClicked() {
        if (install.value != UpdateInstallState.ReadyToRestart || restartJob?.isActive == true) return
        restartJob = viewModelScope.launch {
            install.value = if (restartIntoUpdate()) UpdateInstallState.Restarting else UpdateInstallState.Failed
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
