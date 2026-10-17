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

/**
 * The last settings used on a track, restored when it is opened again. [settings] holds the region
 * the engine plays: a mirror of the active loop. The loop columns are owned by the loops screen and
 * kept when the auto-save replaces the row.
 */
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
    /** Not a foreign key: a stale id reads as no active loop, and deleting a loop picks the next one. */
    @ColumnInfo(name = "active_loop_id") val activeLoopId: Long? = null,
    /** Set by the user (the manual BPM), null when unknown. It wins over [detectedBpm]. */
    @ColumnInfo(name = "bpm") val bpm: Float? = null,
    /** The track's own EQ curve as JSON; null = the track uses the global EQ. */
    @ColumnInfo(name = "eq_curve") val eqCurve: String? = null,
    /** Found by the tempo analysis; null when not analysed yet or nothing was found. */
    @ColumnInfo(name = "detected_bpm") val detectedBpm: Float? = null,
    /** Where the first beat falls, in seconds of the file; null when unknown. */
    @ColumnInfo(name = "beat_offset_seconds") val beatOffsetSeconds: Float? = null,
    /** The version of the tempo analysis that ran on the track; 0 = never analysed. */
    @ColumnInfo(name = "tempo_analysis_version", defaultValue = "0") val tempoAnalysisVersion: Int = 0,
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
    /** The EQ curve saved with the preset as JSON; null = the preset predates EQ and leaves it alone. */
    @ColumnInfo(name = "eq_curve") val eqCurve: String? = null,
)
