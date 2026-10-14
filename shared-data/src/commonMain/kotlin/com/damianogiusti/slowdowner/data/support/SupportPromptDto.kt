package com.damianogiusti.slowdowner.data.support

data class SupportPromptDto(
    val launchCount: Int,
    val lastShownAtMillis: Long?,
)
