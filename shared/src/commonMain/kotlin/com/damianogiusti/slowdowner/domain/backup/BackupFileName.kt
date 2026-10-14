package com.damianogiusti.slowdowner.domain.backup

import kotlinx.datetime.LocalDate

internal object BackupFileName {
    const val EXTENSION = "sldbackup"
    /** The app is Italian only; the library backup has no title of its own. */
    const val LIBRARY_LABEL = "Libreria"

    private val UNSAFE_CHARS = Regex("""[\\/:*?"<>|\x00-\x1F\x7F]""")
    private const val MAX_LABEL_LENGTH = 80

    /** "Downstep - Autumn Leaves - 2026-10-13.sldbackup" */
    fun of(label: String, date: LocalDate): String {
        val safeLabel = label.replace(UNSAFE_CHARS, "_").trim().take(MAX_LABEL_LENGTH).trim().ifEmpty { "backup" }
        return "Downstep - $safeLabel - $date.$EXTENSION"
    }
}
