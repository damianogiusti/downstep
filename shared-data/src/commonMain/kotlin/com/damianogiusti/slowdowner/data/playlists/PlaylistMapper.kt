package com.damianogiusti.slowdowner.data.playlists

import com.damianogiusti.slowdowner.domain.playlists.Playlist

object PlaylistMapper {
    fun toDomain(row: PlaylistSummaryRow) = Playlist(id = row.id, name = row.name, trackCount = row.trackCount)
}
