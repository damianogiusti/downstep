package com.damianogiusti.slowdowner.data.backup

import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.loops.LoopEntity
import com.damianogiusti.slowdowner.data.loops.ProgressionColumns
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsColumns
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity
import com.damianogiusti.slowdowner.domain.backup.BackupScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                snapToBeat = true,
            ),
            dto,
        )
    }

    @Test
    fun `when a track has loops then they go in order with the active one by position`() {
        val verse = loop(id = 8, name = "Verse", start = 0.1f, end = 0.2f)
        val chorus = loop(id = 3, name = "Chorus", start = 0.5f, end = 0.6f)
        val rows = TrackBackupRows(
            track = track,
            state = PracticeStateEntity("yt_abc", columns, updatedAt = 1, activeLoopId = 3, bpm = 92f, snapToBeat = false),
            presets = emptyList(),
            loops = listOf(verse, chorus),
        )

        val dto = BackupManifestMapper.toDto(rows, "audio/yt_abc.m4a")

        val progression = BackupProgressionDto(true, 70f, 100f, 3f, 2)
        assertEquals(
            listOf(
                BackupLoopDto("Verse", 0.1f, 0.2f, passes = 4, notes = "Am", progression = progression),
                BackupLoopDto("Chorus", 0.5f, 0.6f, passes = 4, notes = "Am", progression = progression),
            ),
            dto.loops,
        )
        assertEquals(1, dto.activeLoop)
        assertEquals(92f, dto.bpm)
        assertEquals(false, dto.snapToBeat)
    }

    @Test
    fun `when a restored loop is out of range then it is clamped or dropped`() {
        val wild = BackupLoopDto(" ", start = -1f, end = 0.3f, passes = -5, progression = BackupProgressionDto(true, 1f, 999f, 0f, 0))

        val entity = BackupManifestMapper.toLoopEntity(wild, "t", position = 2, createdAt = 9)!!

        assertEquals("Loop 3", entity.name)
        assertEquals(0f, entity.start)
        assertEquals(0, entity.passes)
        assertEquals(ProgressionColumns(true, 25f, 200f, 1f, 1), entity.progression)
        assertNull(BackupManifestMapper.toLoopEntity(BackupLoopDto("Empty", 0.4f, 0.4f), "t", 0, 9))
        assertNull(BackupManifestMapper.toLoopEntity(BackupLoopDto("NaN", Float.NaN, 0.4f), "t", 0, 9))
    }

    @Test
    fun `when a restored tempo is out of range then it is dropped`() {
        val dto = BackupTrackDto(id = "t", title = "T", addedAt = 0, audio = "audio/t.mp3", bpm = 9000f)

        val state = BackupManifestMapper.toStateEntity(dto, BackupManifestMapper.toDto(columns), updatedAt = 1)

        assertNull(state.bpm)
        assertEquals(true, state.snapToBeat)
    }

    @Test
    fun `when a manifest from before loops is read then the loop fields are empty`() {
        val json = """{"format":"sldbackup","version":1,"createdAt":1,"kind":"track","tracks":[""" +
            """{"id":"t","title":"T","addedAt":0,"audio":"audio/t.mp3",""" +
            """"practiceState":{"speedPercent":75,"pitchSemitones":0,"loopStart":0.2,"loopEnd":0.4,"isLoopOn":true}}]}"""

        val track = BackupArchive.json.decodeFromString(BackupManifestDto.serializer(), json).tracks.single()

        assertEquals(emptyList<BackupLoopDto>(), track.loops)
        assertNull(track.activeLoop)
        assertNull(track.bpm)
        assertNull(track.snapToBeat)
    }

    private fun loop(id: Long, name: String, start: Float, end: Float) = LoopEntity(
        id = id,
        trackId = "yt_abc",
        name = name,
        start = start,
        end = end,
        passes = 4,
        notes = "Am",
        progression = ProgressionColumns(true, 70f, 100f, 3f, 2),
        createdAt = 1,
    )

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
