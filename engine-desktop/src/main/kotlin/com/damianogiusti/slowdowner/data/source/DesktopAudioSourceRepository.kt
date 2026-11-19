package com.damianogiusti.slowdowner.data.source

import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.FileOpenResult
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemMixer
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local files and the stems of a track: Spotify playback is out of use on every platform. [open] decodes the
 * whole file, which takes seconds for a long song: it runs on [decodeDispatcher] so the caller's (main) thread
 * stays free.
 *
 * It is also the [StemMixer]: it opened the stems, so it knows the engine index of each one (their order in
 * [AudioSource.Stems]).
 */
class DesktopAudioSourceRepository(
    private val engine: DesktopPlaybackEngine,
    private val logger: Logger,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioSourceRepository, StemMixer {

    // Written on the decode thread, read by the mixer on the caller's: a whole value each time.
    @Volatile
    private var current: AudioSource? = null

    override suspend fun open(source: AudioSource): OpenResult = withContext(decodeDispatcher) {
        close()
        val result = when (source) {
            is AudioSource.LocalFile -> openLocal(File(source.path))
            is AudioSource.Stems -> openStems(source)
            is AudioSource.SpotifyTrack -> OpenResult.Failed
        }
        current = if (result == OpenResult.Opened) source else null
        result
    }

    override fun current(): AudioSource? = current

    override fun setGain(stem: StemKind, gain: Float) {
        val stems = (current as? AudioSource.Stems)?.stems ?: return
        val index = stems.indexOfFirst { it.kind == stem }
        if (index >= 0) engine.setStemGain(index, gain)
    }

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
        return report(engine.openFile(file.path), properties) { videoLink(file) }
    }

    private fun openStems(source: AudioSource.Stems): OpenResult {
        val files = source.stems.map { File(it.path) }
        val properties = mapOf(
            "extension" to files.firstOrNull()?.extension?.lowercase().orEmpty(),
            "source" to "stems",
            "size" to sizeBucket(files.sumOf { it.length() }),
            "stems" to files.size.toString(),
        )
        if (files.isEmpty() || files.any { !it.canRead() }) {
            logger.w("Cannot open track", properties = properties + ("reason" to "unreadable"))
            return OpenResult.Failed
        }
        // The engine plays them in this order, so index i of the mixer is source.stems[i].
        return report(engine.openStems(files.map { it.path }), properties) { emptyMap() }
    }

    private fun report(
        opened: FileOpenResult,
        properties: Map<String, String>,
        extra: () -> Map<String, String>,
    ): OpenResult {
        return when (val result = opened) {
            FileOpenResult.Opened -> OpenResult.Opened
            // Not a bug: the decoded track needs more memory than the system can give. Logged to see how often.
            FileOpenResult.TooLong -> {
                logger.w("Cannot open track", properties = properties + ("reason" to "tooLong"))
                OpenResult.TooLong
            }
            // A file the decoder rejects: tells which formats need work, and the native text says why.
            is FileOpenResult.Failed -> {
                val failure = properties + mapOf("reason" to "decoder", "detail" to result.detail) + extra()
                logger.e("Cannot open track", properties = failure)
                OpenResult.Failed
            }
        }
    }

    // Only derived from the name, never logged: the library names its own copies `yt_...` and `local_...`.
    private fun sourceOf(name: String) = when {
        name.startsWith(YOUTUBE_PREFIX) -> "youtube"
        name.startsWith("local_") -> "file"
        else -> "other"
    }

    /**
     * A YouTube import the decoder rejects can only be studied with the same video. Its link is
     * public, and the library names the copy after the video id (`yt_<id>`). A name that is not a
     * video id gives no link, so the name of a user's own file never goes out.
     */
    private fun videoLink(file: File): Map<String, String> {
        val id = file.nameWithoutExtension.removePrefix(YOUTUBE_PREFIX)
        val isVideo = file.name.startsWith(YOUTUBE_PREFIX) && VIDEO_ID.matches(id)
        return if (isVideo) mapOf("url" to "https://youtu.be/$id") else emptyMap()
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
        const val YOUTUBE_PREFIX = "yt_"
        val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
