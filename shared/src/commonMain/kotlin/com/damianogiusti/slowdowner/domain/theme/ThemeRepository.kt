package com.damianogiusti.slowdowner.domain.theme

import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    /** Emits the saved mode, [ThemeMode.DEFAULT] when none was saved, then every change. */
    fun observe(): Flow<ThemeMode>
    suspend fun save(mode: ThemeMode)
}
