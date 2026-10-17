package com.damianogiusti.slowdowner.data.theme

import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.domain.theme.ThemeRepository
import com.damianogiusti.slowdowner.domain.theme.ThemeSelection
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

    private val cache = MutableStateFlow<ThemeSelection?>(null)
    private val loadLock = Mutex()

    override fun observe(): Flow<ThemeSelection> = cache.onStart { load() }.filterNotNull()

    override suspend fun save(selection: ThemeSelection) {
        cache.value = selection
        withContext(dispatcher) { store.write(selection.mode.name, selection.themeId) }
    }

    private suspend fun load() = loadLock.withLock {
        if (cache.value != null) return@withLock
        val (mode, themeId) = withContext(dispatcher) { store.readMode() to store.readThemeId() }
        cache.value = ThemeSelection(
            // An unknown name (a newer app's mode) falls back to the default rather than failing.
            mode = ThemeMode.entries.firstOrNull { it.name == mode } ?: ThemeMode.DEFAULT,
            // Saved before themes existed (only a mode): the Downstep palette, the only one then.
            themeId = themeId?.takeIf { it.isNotBlank() } ?: ThemeSelection.DEFAULT_THEME_ID,
        )
    }
}
