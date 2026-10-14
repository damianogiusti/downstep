package com.damianogiusti.slowdowner.data.library

object TrackEntityMapper {
    fun toDto(entity: TrackEntity) = LibraryTrackDto(
        id = entity.id,
        title = entity.title,
        filePath = entity.filePath,
        sourceUrl = entity.sourceUrl,
        addedAt = entity.addedAt,
    )

    fun toEntity(dto: LibraryTrackDto) = TrackEntity(
        id = dto.id,
        title = dto.title,
        filePath = dto.filePath,
        sourceUrl = dto.sourceUrl,
        addedAt = dto.addedAt,
    )
}
