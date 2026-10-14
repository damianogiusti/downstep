package com.damianogiusti.slowdowner.domain.effects

import kotlinx.coroutines.flow.Flow

interface EffectsSettingsRepository {
    fun observe(): Flow<EffectsSettings>
    suspend fun current(): EffectsSettings
    suspend fun save(settings: EffectsSettings)
}
