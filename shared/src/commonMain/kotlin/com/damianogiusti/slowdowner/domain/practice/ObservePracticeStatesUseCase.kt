package com.damianogiusti.slowdowner.domain.practice

import kotlinx.coroutines.flow.Flow

/** Last practice state per track id, for showing it next to each track. */
class ObservePracticeStatesUseCase(private val repository: PracticeRepository) {
    operator fun invoke(): Flow<Map<String, PracticeSettings>> = repository.observeLastStates()
}
