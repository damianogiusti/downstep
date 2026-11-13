package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.player.PlayerPreferences
import com.damianogiusti.slowdowner.domain.player.PlayerPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlayerPreferencesRepository(initial: PlayerPreferences = PlayerPreferences()) : PlayerPreferencesRepository {

    val preferences = MutableStateFlow(initial)

    override fun observe(): Flow<PlayerPreferences> = preferences

    override suspend fun current() = preferences.value

    override suspend fun save(preferences: PlayerPreferences) {
        this.preferences.value = preferences
    }
}
