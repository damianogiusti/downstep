package com.damianogiusti.slowdowner.domain.practice

sealed interface SavePresetResult {
    data class Saved(val preset: PracticePreset) : SavePresetResult
    data object InvalidName : SavePresetResult
    data object NameTaken : SavePresetResult
    data object NothingOpen : SavePresetResult
}
