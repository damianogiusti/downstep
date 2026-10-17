package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.domain.loops.Loop
import com.damianogiusti.slowdowner.domain.loops.LoopRepository
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Exports the open track with the current speed, pitch, mix and EQ: the whole track, one file per
 * loop (its A→B slice), or both, as one batch. Exports never carry the metronome, the count-in or
 * Isolate: those are practice aids, not part of the music.
 */
class ExportTrackUseCase(
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
    private val loops: LoopRepository,
    private val repository: ExportRepository,
) {
    suspend operator fun invoke(scope: ExportScope = ExportScope.FULL_TRACK, onProgress: (Float) -> Unit): ExportResult {
        // Spotify tracks have no library entry and cannot be rendered offline.
        val track = nowPlaying.observe().value
        if (track == null || !engine.isOpen) return ExportResult.NothingOpen
        // The chip strip's order (by start), whatever order the repository returns them in.
        val trackLoops = if (scope.includesLoops) loops.trackLoops(track.id).loops.sortedWith(LOOP_ORDER) else emptyList()
        val items = items(scope, track.title, engine.speedPercent, engine.pitchSemitones, trackLoops)
        if (items.isEmpty()) return ExportResult.NoLoops
        // The offline render drives the same native player: never run it under live playback.
        if (engine.isPlaying) engine.stop()
        return repository.exportOpenTrack(items, onProgress)
    }

    internal companion object {
        private val LOOP_ORDER = compareBy<Loop>({ it.start }, { it.id })

        /** "Title 75%.mp3", plus the pitch when it is not zero: "Title 75% -2st.mp3". */
        fun fileName(title: String, speedPercent: Float, pitchSemitones: Float): String =
            ExportFileNames.fullTrack(title, speedPercent, pitchSemitones)

        /**
         * The batch for [scope]: the full track first, then each of [loops] in the track's order
         * (by start). Both with no loops is the full track alone; Loops with no loops is empty.
         */
        fun items(scope: ExportScope, title: String, speedPercent: Float, pitchSemitones: Float, loops: List<Loop>): List<ExportItem> {
            val full = if (scope.includesFullTrack) listOf(ExportItem(fileName(title, speedPercent, pitchSemitones))) else emptyList()
            val slices = if (scope.includesLoops) {
                loops.mapIndexed { i, loop -> ExportItem(ExportFileNames.loop(title, i + 1, loops.size, loop.name), loop.start, loop.end) }
            } else {
                emptyList()
            }
            val all = full + slices
            return all.zip(ExportFileNames.deduplicate(all.map { it.fileName })) { item, name -> item.copy(fileName = name) }
        }
    }
}
