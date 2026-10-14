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
