package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.support.SupportPromptHistory
import com.damianogiusti.slowdowner.domain.support.SupportPromptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class FakeSupportPromptRepository : SupportPromptRepository {

    val history = MutableStateFlow(SupportPromptHistory())

    override fun observe(): Flow<SupportPromptHistory> = history

    override suspend fun recordLaunch() = history.update { it.copy(launchCount = it.launchCount + 1) }

    override suspend fun recordShown(at: Instant) = history.update { it.copy(lastShownAt = at) }
}
