package com.damianogiusti.slowdowner.data.loops

import com.damianogiusti.slowdowner.domain.loops.Loop
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.loops.TrackLoops

object LoopMapper {
    fun toDomain(entity: LoopEntity) = Loop(
        id = entity.id,
        trackId = entity.trackId,
        name = entity.name,
        start = entity.start,
        end = entity.end,
        passes = entity.passes,
        notes = entity.notes,
        progression = toDomain(entity.progression),
        createdAt = entity.createdAt,
    )

    fun toDomain(columns: ProgressionColumns) = Progression(
        enabled = columns.enabled,
        fromPercent = columns.fromPercent,
        toPercent = columns.toPercent,
        stepPercent = columns.stepPercent,
        everyPasses = columns.everyPasses,
    )

    fun toColumns(progression: Progression) = ProgressionColumns(
        enabled = progression.enabled,
        fromPercent = progression.fromPercent,
        toPercent = progression.toPercent,
        stepPercent = progression.stepPercent,
        everyPasses = progression.everyPasses,
    )

    /** A track without a practice state row has the defaults. A stale active id reads as none. */
    fun toDomain(trackId: String, loops: List<LoopEntity>, settings: TrackLoopSettingsRow?): TrackLoops {
        val domainLoops = loops.map(::toDomain)
        return TrackLoops(
            trackId = trackId,
            loops = domainLoops,
            activeLoopId = settings?.activeLoopId?.takeIf { id -> domainLoops.any { it.id == id } },
            bpm = settings?.bpm,
            snapToBeat = settings?.snapToBeat ?: true,
        )
    }
}
