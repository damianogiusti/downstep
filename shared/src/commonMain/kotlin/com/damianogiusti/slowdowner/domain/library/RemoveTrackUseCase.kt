package com.damianogiusti.slowdowner.domain.library

import com.damianogiusti.slowdowner.domain.playback.CloseTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.stems.StemsRepository

class RemoveTrackUseCase(
    private val repository: LibraryRepository,
    private val nowPlaying: NowPlayingRepository,
    private val closeTrack: CloseTrackUseCase,
    private val stems: StemsRepository,
) {
    suspend operator fun invoke(track: LibraryTrack) {
        // The engine streams from the file: close it before the file is deleted.
        if (nowPlaying.observe().value?.id == track.id) closeTrack()
        repository.remove(track.id)
        // The rows already went with the track (cascade); this removes the stem files.
        stems.delete(track.id)
    }
}
