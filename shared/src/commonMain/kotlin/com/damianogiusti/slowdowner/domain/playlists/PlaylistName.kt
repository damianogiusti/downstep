package com.damianogiusti.slowdowner.domain.playlists

internal object PlaylistName {
    const val MAX_LENGTH = 60

    /** The trimmed name, or null if it cannot be used. */
    fun normalize(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
}
