package com.damianogiusti.slowdowner.data.backup

import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.loops.LoopEntity
import com.damianogiusti.slowdowner.data.loops.LoopMapper
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsColumns
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsMapper
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity
import com.damianogiusti.slowdowner.domain.backup.BackupScope
import com.damianogiusti.slowdowner.domain.backup.RestoreRules
import com.damianogiusti.slowdowner.domain.loops.BeatGrid
import com.damianogiusti.slowdowner.domain.loops.Loop
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings

/** The rows of one track that go into a backup. */
data class TrackBackupRows(
    val track: TrackEntity,
    val state: PracticeStateEntity?,
    val presets: List<PracticePresetEntity>,
    /** Sorted like the player shows them: by start. */
    val loops: List<LoopEntity> = emptyList(),
)

object BackupManifestMapper {
    fun toDto(rows: TrackBackupRows, audioEntry: String) = BackupTrackDto(
        id = rows.track.id,
        title = rows.track.title,
        sourceUrl = rows.track.sourceUrl,
        addedAt = rows.track.addedAt,
        audio = audioEntry,
        practiceState = rows.state?.let { toDto(it.settings) },
        presets = rows.presets.map { BackupPresetDto(name = it.name, settings = toDto(it.settings)) },
        loops = rows.loops.map(::toDto),
        // Ids are local to a database: the backup points at the active loop by position.
        activeLoop = rows.state?.activeLoopId?.let { id -> rows.loops.indexOfFirst { it.id == id } }?.takeIf { it >= 0 },
        bpm = rows.state?.bpm,
        detectedBpm = rows.state?.detectedBpm,
        beatOffsetSeconds = rows.state?.beatOffsetSeconds,
    )

    fun toDto(loop: LoopEntity) = BackupLoopDto(
        name = loop.name,
        start = loop.start,
        end = loop.end,
        passes = loop.passes,
        notes = loop.notes,
        progression = loop.progression.let {
            BackupProgressionDto(it.enabled, it.fromPercent, it.toPercent, it.stepPercent, it.everyPasses)
        },
    )

    fun toDto(columns: PracticeSettingsColumns) = BackupSettingsDto(
        speedPercent = columns.speedPercent,
        pitchSemitones = columns.pitchSemitones,
        loopStart = columns.loopStart,
        loopEnd = columns.loopEnd,
        isLoopOn = columns.isLoopOn,
    )

    fun toDto(playlist: PlaylistEntity, trackIds: List<String>) = BackupPlaylistDto(name = playlist.name, trackIds = trackIds)

    fun toKind(scope: BackupScope) = when (scope) {
        is BackupScope.Track -> BackupKindDto.Track
        BackupScope.Library -> BackupKindDto.Library
        is BackupScope.Playlist -> BackupKindDto.Playlist
    }

    /** [filePath] is where the restore put the audio, not a path from the backup. */
    fun toTrackEntity(dto: BackupTrackDto, filePath: String) = TrackEntity(
        id = dto.id,
        title = dto.title.trim().ifEmpty { dto.id },
        filePath = filePath,
        sourceUrl = dto.sourceUrl,
        addedAt = dto.addedAt,
    )

    /** Values from a file are clamped to what the engine accepts. */
    fun toColumns(dto: BackupSettingsDto): PracticeSettingsColumns {
        val settings = PracticeSettings(
            speedPercent = dto.speedPercent,
            pitchSemitones = dto.pitchSemitones,
            loop = LoopRegion(dto.loopStart, dto.loopEnd, dto.isLoopOn),
        )
        return PracticeSettingsMapper.toColumns(RestoreRules.sanitize(settings))
    }

    /** The state row of a restored track; the active loop is set once its loops have ids. */
    fun toStateEntity(dto: BackupTrackDto, settings: BackupSettingsDto, updatedAt: Long) = PracticeStateEntity(
        trackId = dto.id,
        settings = toColumns(settings),
        updatedAt = updatedAt,
        bpm = dto.bpm?.takeIf(BeatGrid::isValidBpm),
        detectedBpm = dto.detectedBpm?.takeIf(BeatGrid::isValidBpm),
        beatOffsetSeconds = dto.beatOffsetSeconds?.takeIf { it.isFinite() && it >= 0f },
    )

    /**
     * Same clamping as the settings. Null for a loop the app could not have saved (no length).
     * [position] numbers a loop whose name is unusable.
     */
    fun toLoopEntity(dto: BackupLoopDto, trackId: String, position: Int, createdAt: Long): LoopEntity? {
        if (!dto.start.isFinite() || !dto.end.isFinite()) return null
        val start = dto.start.coerceIn(0f, 1f)
        val end = dto.end.coerceIn(0f, 1f)
        if (start >= end) return null
        val progression = dto.progression
            ?.let { Progression(it.enabled, it.fromPercent, it.toPercent, it.stepPercent, it.everyPasses).coerced() }
            ?: Progression.DEFAULT
        return LoopEntity(
            trackId = trackId,
            name = Loop.normalizeName(dto.name) ?: "Loop ${position + 1}",
            start = start,
            end = end,
            passes = dto.passes.coerceAtLeast(0),
            notes = dto.notes.take(Loop.MAX_NOTES_LENGTH),
            progression = LoopMapper.toColumns(progression),
            createdAt = createdAt,
        )
    }
}
