package com.damianogiusti.slowdowner.data.theme

import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.domain.theme.ThemeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultThemeRepository(
    private val store: ThemeStore,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
) : ThemeRepository {

    private val cache = MutableStateFlow<ThemeMode?>(null)
    private val loadLock = Mutex()

    override fun observe(): Flow<ThemeMode> = cache.onStart { load() }.filterNotNull()

    override suspend fun save(mode: ThemeMode) {
        cache.value = mode
        withContext(dispatcher) { store.write(mode.name) }
    }

    private suspend fun load() = loadLock.withLock {
        if (cache.value != null) return@withLock
        // An unknown name (a newer app's theme) falls back to the default rather than failing.
        val saved = withContext(dispatcher) { store.read() }
        cache.value = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.DEFAULT
    }
}
