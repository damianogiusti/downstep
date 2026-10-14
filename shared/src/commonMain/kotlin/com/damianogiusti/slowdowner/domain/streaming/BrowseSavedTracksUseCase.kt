package com.damianogiusti.slowdowner.domain.streaming

class BrowseSavedTracksUseCase(private val repository: StreamingCatalogRepository) {
    suspend operator fun invoke(): List<StreamingTrack> = repository.getSavedTracks()
}
