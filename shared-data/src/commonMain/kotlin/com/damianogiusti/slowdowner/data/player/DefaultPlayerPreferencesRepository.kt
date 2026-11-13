package com.damianogiusti.slowdowner.data.player

import com.damianogiusti.slowdowner.domain.player.PlayerPreferences
import com.damianogiusti.slowdowner.domain.player.PlayerPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultPlayerPreferencesRepository(
    private val store: PlayerPreferencesStore,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
) : PlayerPreferencesRepository {

    private val cache = MutableStateFlow<PlayerPreferences?>(null)
    private val loadLock = Mutex()

    override fun observe(): Flow<PlayerPreferences> =
        cache.onStart { current() }.filterNotNull()

    override suspend fun current(): PlayerPreferences = loadLock.withLock {
        cache.value ?: withContext(dispatcher) {
            store.read()?.let(PlayerPreferencesMapper::toDomain) ?: PlayerPreferences()
        }.also { cache.value = it }
    }

    override suspend fun save(preferences: PlayerPreferences) {
        cache.value = preferences
        withContext(dispatcher) { store.write(PlayerPreferencesMapper.toDto(preferences)) }
    }
}
