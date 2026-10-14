package com.damianogiusti.slowdowner.domain.library

import kotlinx.coroutines.flow.Flow

class ObserveLibraryUseCase(private val repository: LibraryRepository) {
    operator fun invoke(): Flow<List<LibraryTrack>> = repository.observeTracks()
}
