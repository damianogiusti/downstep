package com.damianogiusti.slowdowner.domain.click

import kotlinx.coroutines.flow.first

/** Changes the saved click settings from their current value; the result is kept in range. */
class UpdateClickSettingsUseCase(private val repository: ClickSettingsRepository) {
    suspend operator fun invoke(change: (ClickSettings) -> ClickSettings): ClickSettings {
        val updated = change(repository.observe().first()).normalized()
        repository.save(updated)
        return updated
    }
}
