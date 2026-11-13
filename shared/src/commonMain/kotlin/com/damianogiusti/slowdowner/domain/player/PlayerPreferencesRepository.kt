package com.damianogiusti.slowdowner.domain.player

import kotlinx.coroutines.flow.Flow

interface PlayerPreferencesRepository {
    fun observe(): Flow<PlayerPreferences>
    suspend fun current(): PlayerPreferences
    suspend fun save(preferences: PlayerPreferences)
}
