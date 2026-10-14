package com.damianogiusti.slowdowner.data.practice

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.damianogiusti.slowdowner.data.library.TrackEntity

/** Values, not slider positions: readable, and independent of the engine's slider mapping. */
data class PracticeSettingsColumns(
    @ColumnInfo(name = "speed_percent") val speedPercent: Float,
    @ColumnInfo(name = "pitch_semitones") val pitchSemitones: Float,
    @ColumnInfo(name = "loop_start") val loopStart: Float,
    @ColumnInfo(name = "loop_end") val loopEnd: Float,
    @ColumnInfo(name = "is_loop_on") val isLoopOn: Boolean,
)

/** The last settings used on a track, restored when it is opened again. */
@Entity(
    tableName = "track_practice_state",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PracticeStateEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    @Embedded val settings: PracticeSettingsColumns,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "practice_presets",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["track_id", "name"], unique = true)],
)
data class PracticePresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: String,
    val name: String,
    @Embedded val settings: PracticeSettingsColumns,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
