package com.damianogiusti.slowdowner.data.player

import com.damianogiusti.slowdowner.domain.player.PlayerPreferences

object PlayerPreferencesMapper {
    fun toDomain(dto: PlayerPreferencesDto) = PlayerPreferences(isPitchFine = dto.isPitchFine)

    fun toDto(preferences: PlayerPreferences) = PlayerPreferencesDto(isPitchFine = preferences.isPitchFine)
}
