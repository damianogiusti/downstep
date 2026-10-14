package com.damianogiusti.slowdowner.domain.practice

data class PracticePreset(
    val id: Long,
    val trackId: String,
    val name: String,
    val settings: PracticeSettings,
)
