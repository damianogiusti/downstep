package com.damianogiusti.slowdowner.domain.backup

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playlists.PlaylistName
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.practice.SavePresetUseCase

/** How a backup merges into an existing library. Pure: the repository applies them. */
object RestoreRules {
    const val DEFAULT_PLAYLIST_NAME = "Playlist"

    /** The name a backup playlist gets in the library, before any clash. */
    fun playlistName(name: String): String = PlaylistName.normalize(name) ?: DEFAULT_PLAYLIST_NAME

    /** [name] if free, else "name (2)", "name (3)"… within the playlist name limit. */
    fun uniquePlaylistName(name: String, taken: Set<String>): String {
        val base = playlistName(name)
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }
            .map { number ->
                val suffix = " ($number)"
                base.take(PlaylistName.MAX_LENGTH - suffix.length).trimEnd() + suffix
            }
            .first { it !in taken }
    }

    /** The trimmed preset name, or null if the app could not have saved it. */
    fun presetName(name: String): String? =
        name.trim().takeIf { it.isNotEmpty() && it.length <= SavePresetUseCase.MAX_NAME_LENGTH }

    /**
     * A backup is a file from outside: clamp its values to what the engine accepts, so a
     * hand-edited or damaged manifest cannot push the engine out of range.
     */
    fun sanitize(settings: PracticeSettings): PracticeSettings {
        val start = settings.loop.start.finiteOr(0f).coerceIn(0f, 1f)
        val end = settings.loop.end.finiteOr(1f).coerceIn(0f, 1f)
        val loop = if (start < end) LoopRegion(start, end, settings.loop.isOn) else LoopRegion.WHOLE_FILE
        return PracticeSettings(
            speedPercent = settings.speedPercent.finiteOr(DEFAULT_SPEED)
                .coerceIn(PlaybackEngine.MIN_SPEED_PERCENT, PlaybackEngine.MAX_SPEED_PERCENT),
            pitchSemitones = settings.pitchSemitones.finiteOr(0f)
                .coerceIn(PlaybackEngine.MIN_PITCH_SEMITONES, PlaybackEngine.MAX_PITCH_SEMITONES),
            loop = loop,
        )
    }

    private fun Float.finiteOr(fallback: Float) = if (isFinite()) this else fallback

    private const val DEFAULT_SPEED = 100f
}
