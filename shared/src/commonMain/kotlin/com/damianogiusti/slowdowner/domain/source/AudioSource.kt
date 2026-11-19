package com.damianogiusti.slowdowner.domain.source

import com.damianogiusti.slowdowner.domain.stems.StemKind

sealed class AudioSource {
    data class LocalFile(val path: String) : AudioSource()
    data class SpotifyTrack(val uri: String) : AudioSource()

    /**
     * The stems of one track, played together as one source. The list order is the order the platform
     * keeps for them, so a [StemMixer] gain goes to the right stem.
     */
    data class Stems(val stems: List<StemSource>) : AudioSource()
}

/** One stem file of an [AudioSource.Stems]; [path] is absolute. */
data class StemSource(val kind: StemKind, val path: String)
