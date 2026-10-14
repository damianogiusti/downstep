package com.damianogiusti.slowdowner.data.source

import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import java.io.File

/**
 * Local files only: Spotify playback is out of use on every platform. [open] decodes the whole file
 * and blocks, like the Android repository: callers run it on an IO dispatcher.
 */
class DesktopAudioSourceRepository(private val engine: DesktopPlaybackEngine) : AudioSourceRepository {

    private var current: AudioSource? = null

    override fun open(source: AudioSource): Boolean {
        close()
        val opened = when (source) {
            is AudioSource.LocalFile -> File(source.path).canRead() && engine.openFile(source.path)
            is AudioSource.SpotifyTrack -> false
        }
        current = if (opened) source else null
        return opened
    }

    override fun current(): AudioSource? = current

    override fun close() {
        if (engine.isOpen) engine.closeFile()
        current = null
    }
}
