package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import kotlin.math.roundToInt

class ExportTrackUseCase(
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
    private val repository: ExportRepository,
) {
    suspend operator fun invoke(onProgress: (Float) -> Unit): ExportResult {
        // Spotify tracks have no library entry and cannot be rendered offline.
        val track = nowPlaying.observe().value
        if (track == null || !engine.isOpen) return ExportResult.NothingOpen
        // The offline render drives the same native player: never run it under live playback.
        if (engine.isPlaying) engine.stop()
        val name = fileName(track.title, engine.speedPercent, engine.pitchSemitones)
        return repository.exportOpenTrack(name, onProgress)
    }

    internal companion object {
        private val UNSAFE_CHARS = Regex("""[\\/:*?"<>|\x00-\x1F\x7F]""")
        private const val MAX_TITLE_LENGTH = 80

        /** "Title 75%.mp3", plus the pitch when it is not zero: "Title 75% -2st.mp3". */
        fun fileName(title: String, speedPercent: Float, pitchSemitones: Float): String {
            val safeTitle = title.replace(UNSAFE_CHARS, "_").trim().take(MAX_TITLE_LENGTH).ifEmpty { "audio" }
            val speed = "${speedPercent.roundToInt()}%"
            val semitones = pitchSemitones.roundToInt()
            val pitch = when {
                semitones > 0 -> " +${semitones}st"
                semitones < 0 -> " ${semitones}st"
                else -> ""
            }
            return "$safeTitle $speed$pitch.mp3"
        }
    }
}
