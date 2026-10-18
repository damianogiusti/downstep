package com.damianogiusti.slowdowner.domain.click

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveClickSettingsUseCase(private val repository: ClickSettingsRepository) {
    operator fun invoke(): Flow<ClickSettings> = repository.observe().map { it.normalized() }.distinctUntilChanged()
}
