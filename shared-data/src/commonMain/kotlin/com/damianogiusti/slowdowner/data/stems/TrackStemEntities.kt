package com.damianogiusti.slowdowner.data.stems

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.damianogiusti.slowdowner.data.library.TrackEntity

/**
 * A separated track: which model made the stems, which mode it plays. [model] and [mode] are text
 * (the enum name), so a later model or mode never needs a schema change.
 */
@Entity(
    tableName = "track_stem_state",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackStemStateEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    val model: String,
    val mode: String,
)

/** One stem of a track: a file inside the track's stem folder and its gain. */
@Entity(
    tableName = "track_stems",
    primaryKeys = ["track_id", "stem"],
    foreignKeys = [
        ForeignKey(
            entity = TrackStemStateEntity::class,
            parentColumns = ["track_id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackStemEntity(
    @ColumnInfo(name = "track_id") val trackId: String,
    val stem: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val gain: Float,
)

/** The state and all its stems, read in one transaction so an observer never sees them half written. */
data class TrackStemsRelation(
    @Embedded val state: TrackStemStateEntity,
    @Relation(parentColumn = "track_id", entityColumn = "track_id") val stems: List<TrackStemEntity>,
)
