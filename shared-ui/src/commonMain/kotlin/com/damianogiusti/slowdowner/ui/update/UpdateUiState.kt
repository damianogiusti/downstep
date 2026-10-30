package com.damianogiusti.slowdowner.ui.update

sealed interface UpdateUiState {
    data object None : UpdateUiState

    /**
     * A newer version exists: the banner shows as long as this lasts. [isDialogOpen]: the dialog is on
     * screen, never at the same time as the support prompt. [canInstall]: false means the app
     * cannot replace itself here, and the dialog links [pageUrl] instead. [notes]: what changed.
     */
    data class Available(
        val version: String,
        val installedVersion: String,
        val pageUrl: String,
        val canInstall: Boolean,
        val notes: List<String>,
        val isDialogOpen: Boolean,
        val install: UpdateInstallState,
    ) : UpdateUiState
}

sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    /** [percent] reaches 100 while the new app is unpacked and checked. */
    data class Downloading(val percent: Int) : UpdateInstallState
    data object NetworkError : UpdateInstallState
    /** The update did not work: the dialog offers the download page. */
    data object Failed : UpdateInstallState
    /**
     * Downloaded and checked. The dialog says the app closes and opens again by itself, so nobody
     * opens it by hand halfway (on Windows that copy would lock the files the swap moves).
     */
    data object ReadyToRestart : UpdateInstallState
    /** The app quits now, and the new one opens. */
    data object Restarting : UpdateInstallState
}
