package com.damianogiusti.slowdowner.data.support

import com.damianogiusti.slowdowner.domain.support.SupportPromptHistory
import com.damianogiusti.slowdowner.domain.support.SupportPromptRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class DefaultSupportPromptRepository(
    private val store: SupportPromptStore,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
) : SupportPromptRepository {

    private val cache = MutableStateFlow<SupportPromptHistory?>(null)
    // Read-modify-write: a launch and a dismissal must not overwrite each other.
    private val lock = Mutex()

    override fun observe(): Flow<SupportPromptHistory> =
        cache.onStart { lock.withLock { load() } }.filterNotNull()

    override suspend fun recordLaunch() = update { it.copy(launchCount = it.launchCount + 1) }

    override suspend fun recordShown(at: Instant) = update { it.copy(lastShownAt = at) }

    private suspend fun update(change: (SupportPromptHistory) -> SupportPromptHistory) = lock.withLock {
        val history = change(load())
        cache.value = history
        withContext(dispatcher) { store.write(SupportPromptMapper.toDto(history)) }
    }

    private suspend fun load(): SupportPromptHistory = cache.value ?: withContext(dispatcher) {
        store.read()?.let(SupportPromptMapper::toDomain) ?: SupportPromptHistory()
    }.also { cache.value = it }
}
