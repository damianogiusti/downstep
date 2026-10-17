package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.logging.Logger
import com.damianogiusti.slowdowner.ui.navigation.ActivityActions
import com.damianogiusti.slowdowner.ui.navigation.ActivityViewModels
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

/**
 * The desktop side of [ActivityActions]: native macOS file dialogs instead of the Android pickers,
 * and "show in Finder" where Android opens the share sheet.
 */
class DesktopActions(
    private val window: Frame,
    private val viewModels: ActivityViewModels,
    private val logger: Logger,
) {

    fun toActivityActions() = ActivityActions(
        onPickFile = { pickFile(AUDIO_EXTENSIONS)?.let { viewModels.imports.onDocumentPicked(it.path) } },
        onOpenSpotify = null,
        onPlayStop = viewModels.player::onPlayStopClicked,
        // No foreground service on desktop: the process plays while the window is open.
        onPlaybackStarted = {},
        // The whole batch sits in one folder: showing the first file shows them all.
        onShareExport = { files -> files.firstOrNull()?.let { reveal(File(it.path)) } },
        onSaveExport = { files ->
            val single = files.singleOrNull()
            if (single != null) {
                saveAs(single.name)?.let { viewModels.player.onExportSaveTargetPicked(it.path) }
            } else {
                pickFolder()?.let { viewModels.player.onExportSaveFolderPicked(it.path) }
            }
        },
        onPickBackup = { pickFile(setOf(BACKUP_EXTENSION, "zip"))?.let { viewModels.backup.onRestorePicked(it.path) } },
        onShareBackup = { reveal(File(it.path)) },
        onSaveBackup = { file -> saveAs(file.name)?.let { viewModels.backup.onSaveTargetPicked(it.path) } },
        onDonate = { openDonation(DonateSource.LIBRARY_MENU, logger) },
    )

    private fun pickFile(extensions: Set<String>): File? {
        val dialog = FileDialog(window, null, FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
            isVisible = true
        }
        return dialog.selectedFile()
    }

    private fun saveAs(suggestedName: String): File? {
        val dialog = FileDialog(window, null, FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        return dialog.selectedFile()
    }

    /** A folder for a batch of exported files: the native folder picker on macOS, Swing's elsewhere. */
    private fun pickFolder(): File? {
        if (isMacOs) {
            // AWT's dialog picks folders only while this property is set.
            System.setProperty(MAC_FOLDER_DIALOG, "true")
            try {
                return FileDialog(window, null, FileDialog.LOAD).apply { isVisible = true }.selectedFile()
            } finally {
                System.clearProperty(MAC_FOLDER_DIALOG)
            }
        }
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    private fun reveal(file: File) {
        // A failure here only means the Finder does not open: the file is already written.
        runCatching {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(file)
            else desktop.open(file.parentFile)
        }.onFailure { logger.w("Cannot show the file in the Finder", it) }
    }

    private fun FileDialog.selectedFile(): File? {
        val name = file ?: return null
        return File(directory, name)
    }

    private companion object {
        val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "wav", "aac", "flac", "aif", "aiff", "caf", "mp4")
        const val BACKUP_EXTENSION = "sldbackup"
        const val MAC_FOLDER_DIALOG = "apple.awt.fileDialogForDirectories"
    }
}
