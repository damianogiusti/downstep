package com.damianogiusti.slowdowner.data.support

import com.damianogiusti.slowdowner.domain.support.SupportPromptHistory
import kotlin.time.Instant

object SupportPromptMapper {

    fun toDomain(dto: SupportPromptDto) = SupportPromptHistory(
        launchCount = dto.launchCount.coerceAtLeast(0),
        lastShownAt = dto.lastShownAtMillis?.let(Instant::fromEpochMilliseconds),
    )

    fun toDto(history: SupportPromptHistory) = SupportPromptDto(
        launchCount = history.launchCount,
        lastShownAtMillis = history.lastShownAt?.toEpochMilliseconds(),
    )
}
