package com.damianogiusti.slowdowner.domain.backup

/** What a restore does with a backup playlist whose name is taken by a different playlist. */
enum class PlaylistConflictChoice {
    /** The library playlist gets the backup's tracks and order. */
    Overwrite,
    /** The backup playlist is added with a number: "Jazz (2)". */
    Copy,
}
