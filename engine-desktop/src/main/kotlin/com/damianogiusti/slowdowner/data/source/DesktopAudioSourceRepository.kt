package com.damianogiusti.slowdowner.data.source

import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local files only: Spotify playback is out of use on every platform. [open] decodes the whole file,
 * which takes seconds for a long song: it runs on [decodeDispatcher] so the caller's (main) thread stays free.
 */
class DesktopAudioSourceRepository(
    private val engine: DesktopPlaybackEngine,
    private val logger: Logger,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioSourceRepository {

    private var current: AudioSource? = null

    override suspend fun open(source: AudioSource): Boolean = withContext(decodeDispatcher) {
        close()
        val opened = when (source) {
            is AudioSource.LocalFile -> openLocal(File(source.path))
            is AudioSource.SpotifyTrack -> false
        }
        current = if (opened) source else null
        opened
    }

    override fun current(): AudioSource? = current

    private fun openLocal(file: File): Boolean {
        val extension = "extension" to file.extension.lowercase()
        // Deleted or moved behind our back: not a bug, but worth seeing if it happens often.
        if (!file.canRead()) {
            logger.w("Cannot open track", properties = mapOf(extension, "reason" to "unreadable"))
            return false
        }
        // A file the decoder rejects: tells which formats need work.
        return engine.openFile(file.path).also { opened ->
            if (!opened) logger.e("Cannot open track", properties = mapOf(extension, "reason" to "decoder"))
        }
    }

    override fun close() {
        if (engine.isOpen) engine.closeFile()
        current = null
    }
}
