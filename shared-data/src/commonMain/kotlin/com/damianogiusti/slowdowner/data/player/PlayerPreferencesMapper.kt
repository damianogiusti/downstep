package com.damianogiusti.slowdowner.data.player

import com.damianogiusti.slowdowner.domain.player.PlayerPreferences

object PlayerPreferencesMapper {
    fun toDomain(dto: PlayerPreferencesDto) = PlayerPreferences(
        isPitchFine = dto.isPitchFine,
        isLoopFine = dto.isLoopFine,
        isWaveformShown = dto.isWaveformShown,
    )

    fun toDto(preferences: PlayerPreferences) = PlayerPreferencesDto(
        isPitchFine = preferences.isPitchFine,
        isLoopFine = preferences.isLoopFine,
        isWaveformShown = preferences.isWaveformShown,
    )
}
