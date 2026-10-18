package com.damianogiusti.slowdowner.domain.click

import kotlinx.coroutines.flow.Flow

interface ClickSettingsRepository {
    /** Emits the saved settings, the defaults when none were saved, then every change. */
    fun observe(): Flow<ClickSettings>
    suspend fun save(settings: ClickSettings)
}
