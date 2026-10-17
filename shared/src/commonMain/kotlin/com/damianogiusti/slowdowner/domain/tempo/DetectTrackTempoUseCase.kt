package com.damianogiusti.slowdowner.domain.tempo

import com.damianogiusti.slowdowner.domain.loops.BeatGrid
import com.damianogiusti.slowdowner.domain.loops.LoopRepository
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository

/**
 * The tempo analysis of the open track, cached per track: it runs once when a track is first opened
 * (and again only after [ANALYSIS_VERSION] goes up), and its result, "nothing found" included, is
 * saved with the track's loops. [detector] is null where the platform has none.
 */
class DetectTrackTempoUseCase(
    private val detector: TempoDetector?,
    private val repository: LoopRepository,
    private val nowPlaying: NowPlayingRepository,
) {

    val isAvailable: Boolean get() = detector != null

    /** Whether [trackId] still needs the analysis: never analysed, or by an older one. */
    suspend fun isPending(trackId: String): Boolean =
        detector != null && repository.trackLoops(trackId).tempoAnalysisVersion < ANALYSIS_VERSION

    /**
     * Analyses [trackId], the open track, unless it already was. True when it ran and was saved; false
     * when cached, unavailable, or the track was replaced meanwhile (its result would be another's).
     */
    suspend operator fun invoke(trackId: String): Boolean {
        val detector = detector ?: return false
        if (!isPending(trackId) || !isOpen(trackId)) return false
        val estimate = detector.detect()?.takeIf { BeatGrid.isValidBpm(it.bpm) }
        if (!isOpen(trackId)) return false
        repository.setDetectedTempo(trackId, estimate, ANALYSIS_VERSION)
        // A tempo the user typed before the analysis keeps its own beats.
        if (repository.trackLoops(trackId).bpm != null) alignBeats(trackId)
        return true
    }

    /**
     * Finds where the beats of the track's effective tempo fall, after the user typed or tapped one
     * (or cleared theirs, back to the detected one). Without detection the first beat stays unknown.
     */
    suspend fun alignBeats(trackId: String): Boolean {
        val detector = detector ?: return false
        if (!isOpen(trackId)) return false
        val bpm = repository.trackLoops(trackId).effectiveBpm
        val offset = bpm?.let { detector.detect(hintBpm = it)?.beatOffsetSeconds }
        if (!isOpen(trackId) || repository.trackLoops(trackId).effectiveBpm != bpm) return false
        repository.setBeatOffset(trackId, offset)
        return true
    }

    private fun isOpen(trackId: String) = nowPlaying.observe().value?.id == trackId

    companion object {
        /** Raise it when the analysis improves: every track is then analysed again, once. */
        const val ANALYSIS_VERSION = 1
    }
}
