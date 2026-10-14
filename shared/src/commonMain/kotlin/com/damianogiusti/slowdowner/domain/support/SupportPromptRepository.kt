package com.damianogiusti.slowdowner.domain.support

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface SupportPromptRepository {
    fun observe(): Flow<SupportPromptHistory>
    suspend fun recordLaunch()
    suspend fun recordShown(at: Instant)
}
