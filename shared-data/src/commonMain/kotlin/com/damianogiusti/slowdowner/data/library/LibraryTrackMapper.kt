package com.damianogiusti.slowdowner.data.library

import com.damianogiusti.slowdowner.domain.library.LibraryTrack

object LibraryTrackMapper {
    fun toDomain(dto: LibraryTrackDto) = LibraryTrack(
        id = dto.id,
        title = dto.title,
        filePath = dto.filePath,
        sourceUrl = dto.sourceUrl,
        addedAt = dto.addedAt,
    )
}
