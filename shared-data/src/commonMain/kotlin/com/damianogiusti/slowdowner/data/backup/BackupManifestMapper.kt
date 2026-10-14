package com.damianogiusti.slowdowner.data.backup

import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsColumns
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsMapper
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity
import com.damianogiusti.slowdowner.domain.backup.BackupScope
import com.damianogiusti.slowdowner.domain.backup.RestoreRules
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings

/** The rows of one track that go into a backup. */
data class TrackBackupRows(
    val track: TrackEntity,
    val state: PracticeStateEntity?,
    val presets: List<PracticePresetEntity>,
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
}
