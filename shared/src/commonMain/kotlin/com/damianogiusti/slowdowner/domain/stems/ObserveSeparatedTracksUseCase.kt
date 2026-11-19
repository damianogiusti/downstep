package com.damianogiusti.slowdowner.domain.stems

import kotlinx.coroutines.flow.Flow

class ObserveSeparatedTracksUseCase(private val repository: StemsRepository) {
    operator fun invoke(): Flow<Set<String>> = repository.observeSeparatedTrackIds()
}
