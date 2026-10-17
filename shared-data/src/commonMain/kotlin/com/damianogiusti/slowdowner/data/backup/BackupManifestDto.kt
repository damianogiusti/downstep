package com.damianogiusti.slowdowner.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `manifest.json`, the first entry of a `.sldbackup` zip. Field names are the file format. */
@Serializable
data class BackupManifestDto(
    val format: String,
    val version: Int,
    val createdAt: Long,
    val kind: BackupKindDto,
    val tracks: List<BackupTrackDto> = emptyList(),
    val playlists: List<BackupPlaylistDto> = emptyList(),
) {
    companion object {
        const val FORMAT = "sldbackup"
        const val VERSION = 1
    }
}

@Serializable
enum class BackupKindDto {
    @SerialName("track") Track,
    @SerialName("library") Library,
    @SerialName("playlist") Playlist,
}

@Serializable
data class BackupTrackDto(
    val id: String,
    val title: String,
    val sourceUrl: String? = null,
    val addedAt: Long,
    /** Zip entry of the audio file: `audio/<id>.<ext>`. */
    val audio: String,
    val practiceState: BackupSettingsDto? = null,
    val presets: List<BackupPresetDto> = emptyList(),
    // Named loops came after version 1 of the format: every field is optional, so older backups
    // restore without them and older apps skip them (unknown keys are ignored).
    val loops: List<BackupLoopDto> = emptyList(),
    /** Index in [loops] of the active one. */
    val activeLoop: Int? = null,
    val bpm: Float? = null,
    // "snapToBeat" (Snap to beat, removed after 2.0) is no longer written; backups that still carry
    // it restore fine, the key is ignored like any unknown one.
)

@Serializable
data class BackupSettingsDto(
    val speedPercent: Float,
    val pitchSemitones: Float,
    val loopStart: Float,
    val loopEnd: Float,
    val isLoopOn: Boolean,
)

@Serializable
data class BackupPresetDto(val name: String, val settings: BackupSettingsDto)

@Serializable
data class BackupPlaylistDto(val name: String, val trackIds: List<String> = emptyList())

@Serializable
data class BackupLoopDto(
    val name: String,
    val start: Float,
    val end: Float,
    val passes: Int = 0,
    val notes: String = "",
    val progression: BackupProgressionDto? = null,
)

@Serializable
data class BackupProgressionDto(
    val enabled: Boolean,
    val fromPercent: Float,
    val toPercent: Float,
    val stepPercent: Float,
    val everyPasses: Int,
)
