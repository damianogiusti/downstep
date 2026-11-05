package com.damianogiusti.slowdowner.data.source

import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.FileOpenResult
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult
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

    override suspend fun open(source: AudioSource): OpenResult = withContext(decodeDispatcher) {
        close()
        val result = when (source) {
            is AudioSource.LocalFile -> openLocal(File(source.path))
            is AudioSource.SpotifyTrack -> OpenResult.Failed
        }
        current = if (result == OpenResult.Opened) source else null
        result
    }

    override fun current(): AudioSource? = current

    private fun openLocal(file: File): OpenResult {
        val properties = mapOf(
            "extension" to file.extension.lowercase(),
            "source" to sourceOf(file.name),
            "size" to sizeBucket(file.length()),
        )
        // Deleted or moved behind our back: not a bug, but worth seeing if it happens often.
        if (!file.canRead()) {
            logger.w("Cannot open track", properties = properties + ("reason" to "unreadable"))
            return OpenResult.Failed
        }
        return when (val result = engine.openFile(file.path)) {
            FileOpenResult.Opened -> OpenResult.Opened
            // Not a bug: the decoded track needs more memory than the system can give. Logged to see how often.
            FileOpenResult.TooLong -> {
                logger.w("Cannot open track", properties = properties + ("reason" to "tooLong"))
                OpenResult.TooLong
            }
            // A file the decoder rejects: tells which formats need work, and the native text says why.
            is FileOpenResult.Failed -> {
                val failure = properties + mapOf("reason" to "decoder", "detail" to result.detail)
                logger.e("Cannot open track", properties = failure)
                OpenResult.Failed
            }
        }
    }

    // Only derived from the name, never logged: the library names its own copies `yt_...` and `local_...`.
    private fun sourceOf(name: String) = when {
        name.startsWith("yt_") -> "youtube"
        name.startsWith("local_") -> "file"
        else -> "other"
    }

    private fun sizeBucket(bytes: Long) = when {
        bytes < 10 * MEGABYTE -> "<10MB"
        bytes < 50 * MEGABYTE -> "10-50MB"
        bytes < 200 * MEGABYTE -> "50-200MB"
        else -> ">200MB"
    }

    override fun close() {
        if (engine.isOpen) engine.closeFile()
        current = null
    }

    private companion object {
        const val MEGABYTE = 1024L * 1024
    }
}
