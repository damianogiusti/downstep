package com.damianogiusti.slowdowner.data.click

import com.damianogiusti.slowdowner.domain.click.ClickSettings
import com.damianogiusti.slowdowner.domain.click.ClickSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultClickSettingsRepository(
    private val store: ClickSettingsStore,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
) : ClickSettingsRepository {

    private val cache = MutableStateFlow<ClickSettings?>(null)
    private val loadLock = Mutex()

    override fun observe(): Flow<ClickSettings> = cache.onStart { load() }.filterNotNull()

    override suspend fun save(settings: ClickSettings) {
        cache.value = settings
        withContext(dispatcher) { store.write(settings.toDto()) }
    }

    private suspend fun load() = loadLock.withLock {
        if (cache.value != null) return@withLock
        val saved = withContext(dispatcher) { store.read() }
        cache.value = saved.toDomain()
    }

    internal companion object {
        // A value never saved keeps its default.
        fun ClickSettingsDto.toDomain(): ClickSettings {
            val defaults = ClickSettings()
            return ClickSettings(
                isMetronomeOn = metronomeEnabled ?: defaults.isMetronomeOn,
                volume = volume ?: defaults.volume,
                isCountInOn = countInEnabled ?: defaults.isCountInOn,
                countInBeats = countInBeats ?: defaults.countInBeats,
                countInEveryLoop = countInEveryLoop ?: defaults.countInEveryLoop,
            ).normalized()
        }

        fun ClickSettings.toDto() = ClickSettingsDto(isMetronomeOn, volume, isCountInOn, countInBeats, countInEveryLoop)
    }
}
