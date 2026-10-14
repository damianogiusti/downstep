package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EffectsSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultEffectsSettingsRepository(
    private val store: EffectsSettingsStore,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
) : EffectsSettingsRepository {

    private val cache = MutableStateFlow<EffectsSettings?>(null)
    private val loadLock = Mutex()

    override fun observe(): Flow<EffectsSettings> =
        cache.onStart { current() }.filterNotNull()

    override suspend fun current(): EffectsSettings = loadLock.withLock {
        cache.value ?: withContext(dispatcher) {
            store.read()?.let(EffectsSettingsMapper::toDomain) ?: EffectsSettings()
        }.also { cache.value = it }
    }

    override suspend fun save(settings: EffectsSettings) {
        cache.value = settings
        withContext(dispatcher) { store.write(EffectsSettingsMapper.toDto(settings)) }
    }
}
