package com.damianogiusti.slowdowner.domain.backup

/** What goes into a backup. Every scope carries the audio, the practice state and the presets. */
sealed interface BackupScope {
    data class Track(val trackId: String) : BackupScope
    /** Every track and every playlist. */
    data object Library : BackupScope
    /** The playlist with its order, plus its tracks. */
    data class Playlist(val playlistId: Long) : BackupScope
}
