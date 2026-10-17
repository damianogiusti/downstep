package com.damianogiusti.slowdowner.domain.theme

import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    /** Emits the saved selection, [ThemeSelection.DEFAULT] when none was saved, then every change. */
    fun observe(): Flow<ThemeSelection>
    suspend fun save(selection: ThemeSelection)
}
