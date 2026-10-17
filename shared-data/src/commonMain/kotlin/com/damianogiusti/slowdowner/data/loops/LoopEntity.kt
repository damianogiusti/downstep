package com.damianogiusti.slowdowner.data.loops

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.damianogiusti.slowdowner.data.library.TrackEntity

/** Values, not slider positions, like the practice settings. */
data class ProgressionColumns(
    val enabled: Boolean,
    @ColumnInfo(name = "from_percent") val fromPercent: Float,
    @ColumnInfo(name = "to_percent") val toPercent: Float,
    @ColumnInfo(name = "step_percent") val stepPercent: Float,
    @ColumnInfo(name = "every_passes") val everyPasses: Int,
)

/** A named loop of a track. Names may repeat: they are labels, the id is the identity. */
@Entity(
    tableName = "practice_loops",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["track_id"])],
)
data class LoopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: String,
    val name: String,
    // Same columns and units as the region in track_practice_state: fractions 0..1 of the file.
    @ColumnInfo(name = "loop_start") val start: Float,
    @ColumnInfo(name = "loop_end") val end: Float,
    val passes: Int,
    val notes: String,
    @Embedded(prefix = "progression_") val progression: ProgressionColumns,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** The loop columns of track_practice_state. */
data class TrackLoopSettingsRow(
    @ColumnInfo(name = "active_loop_id") val activeLoopId: Long?,
    val bpm: Float?,
    @ColumnInfo(name = "detected_bpm") val detectedBpm: Float? = null,
    @ColumnInfo(name = "beat_offset_seconds") val beatOffsetSeconds: Float? = null,
    @ColumnInfo(name = "tempo_analysis_version") val tempoAnalysisVersion: Int = 0,
)
