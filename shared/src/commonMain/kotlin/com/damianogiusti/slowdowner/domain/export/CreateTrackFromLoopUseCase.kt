package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.library.LibraryRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.practice.PracticeRepository
import com.damianogiusti.slowdowner.domain.practice.readPracticeSettings
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface CreateTrackResult {
    data class Created(val track: LibraryTrack) : CreateTrackResult
    /** No library track is open: the engine has nothing to render. */
    data object NothingOpen : CreateTrackResult
    data object NoLoop : CreateTrackResult
    /** Blank once trimmed. */
    data object InvalidTitle : CreateTrackResult
    data class Failure(val cause: Throwable) : CreateTrackResult
}

/**
 * Makes the loop of the open track a library track of its own: the original sound, so it can be
 * slowed down and transposed like any other. It starts with the speed and pitch of the source
 * track, and no loop.
 */
class CreateTrackFromLoopUseCase(
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
    private val export: ExportRepository,
    private val library: LibraryRepository,
    private val practice: PracticeRepository,
) {
    suspend operator fun invoke(title: String, onProgress: (Float) -> Unit): CreateTrackResult {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return CreateTrackResult.InvalidTitle
        if (nowPlaying.observe().value == null || !engine.isOpen) return CreateTrackResult.NothingOpen
        if (!engine.hasRenderableLoop()) return CreateTrackResult.NoLoop
        // Engine reads stay on the caller's thread, before the render switches dispatcher.
        val settings = engine.readPracticeSettings()
        // The offline render drives the same native player: never run it under live playback.
        if (engine.isPlaying) engine.stop()

        val range = RenderRange(settings.loop.start, settings.loop.end)
        val file = when (val rendered = export.renderOriginal(range, onProgress)) {
            is ExportResult.Success -> rendered.file
            ExportResult.NothingOpen -> return CreateTrackResult.NothingOpen
            ExportResult.NoLoop -> return CreateTrackResult.NoLoop
            is ExportResult.Failure -> return CreateTrackResult.Failure(rendered.cause)
        }
        // Rendered: from here the track must end up whole, with its settings, even if the user
        // cancels meanwhile.
        return withContext(NonCancellable) {
            val track = when (val added = library.addRenderedTrack(file, trimmed)) {
                is ImportResult.Success -> added.track
                is ImportResult.Failure -> return@withContext CreateTrackResult.Failure(added.cause)
                ImportResult.Unavailable, ImportResult.NetworkError ->
                    return@withContext CreateTrackResult.Failure(IllegalStateException("Cannot add the track"))
            }
            practice.saveLastState(track.id, settings.copy(loop = LoopRegion.WHOLE_FILE))
            CreateTrackResult.Created(track)
        }
    }
}
