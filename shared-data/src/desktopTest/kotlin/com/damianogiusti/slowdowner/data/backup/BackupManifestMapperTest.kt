package com.damianogiusti.slowdowner.data.backup

import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsColumns
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity
import com.damianogiusti.slowdowner.domain.backup.BackupScope
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupManifestMapperTest {

    private val columns = PracticeSettingsColumns(75f, -2f, 0.2f, 0.4f, isLoopOn = true)
    private val track = TrackEntity("yt_abc", "Autumn Leaves", "/files/library/yt_abc.m4a", "https://youtu.be/abc", 7)

    @Test
    fun `when a track is mapped then its rows become one manifest entry`() {
        val rows = TrackBackupRows(
            track = track,
            state = PracticeStateEntity("yt_abc", columns, updatedAt = 1),
            presets = listOf(PracticePresetEntity(3, "yt_abc", "Solo", columns, createdAt = 2)),
        )

        val dto = BackupManifestMapper.toDto(rows, "audio/yt_abc.m4a")

        val settings = BackupSettingsDto(75f, -2f, 0.2f, 0.4f, isLoopOn = true)
        assertEquals(
            BackupTrackDto(
                id = "yt_abc",
                title = "Autumn Leaves",
                sourceUrl = "https://youtu.be/abc",
                addedAt = 7,
                audio = "audio/yt_abc.m4a",
                practiceState = settings,
                presets = listOf(BackupPresetDto("Solo", settings)),
            ),
            dto,
        )
    }

    @Test
    fun `when a track has no state then the manifest entry has none`() {
        val dto = BackupManifestMapper.toDto(TrackBackupRows(track, null, emptyList()), "audio/yt_abc.m4a")

        assertEquals(null, dto.practiceState)
        assertEquals(emptyList<BackupPresetDto>(), dto.presets)
    }

    @Test
    fun `when an entry is restored then the track points at the new file`() {
        val dto = BackupManifestMapper.toDto(TrackBackupRows(track, null, emptyList()), "audio/yt_abc.m4a")

        val entity = BackupManifestMapper.toTrackEntity(dto, "/other/library/yt_abc.m4a")

        assertEquals(track.copy(filePath = "/other/library/yt_abc.m4a"), entity)
    }

    @Test
    fun `when the title is blank then the id is used`() {
        val dto = BackupTrackDto(id = "local_1", title = " ", addedAt = 0, audio = "audio/local_1.mp3")

        assertEquals("local_1", BackupManifestMapper.toTrackEntity(dto, "/f").title)
    }

    @Test
    fun `when settings round trip then the values are the same`() {
        assertEquals(columns, BackupManifestMapper.toColumns(BackupManifestMapper.toDto(columns)))
    }

    @Test
    fun `when restored settings are out of range then they are clamped`() {
        val dto = BackupSettingsDto(speedPercent = 1000f, pitchSemitones = 30f, loopStart = 0.1f, loopEnd = 0.3f, isLoopOn = true)

        assertEquals(PracticeSettingsColumns(200f, 12f, 0.1f, 0.3f, isLoopOn = true), BackupManifestMapper.toColumns(dto))
    }

    @Test
    fun `when a playlist is mapped then name and order are kept`() {
        val dto = BackupManifestMapper.toDto(PlaylistEntity(1, "Jazz", 0, 0), listOf("b", "a"))

        assertEquals(BackupPlaylistDto("Jazz", listOf("b", "a")), dto)
    }

    @Test
    fun `when a scope is mapped then the kind matches`() {
        assertEquals(BackupKindDto.Track, BackupManifestMapper.toKind(BackupScope.Track("a")))
        assertEquals(BackupKindDto.Library, BackupManifestMapper.toKind(BackupScope.Library))
        assertEquals(BackupKindDto.Playlist, BackupManifestMapper.toKind(BackupScope.Playlist(1)))
    }
}
