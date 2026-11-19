package com.damianogiusti.slowdowner.domain.stems

import kotlinx.coroutines.flow.StateFlow

class ObserveModeSwitchUseCase(private val repository: ModeSwitchRepository) {
    operator fun invoke(): StateFlow<Boolean> = repository.observe()
}
