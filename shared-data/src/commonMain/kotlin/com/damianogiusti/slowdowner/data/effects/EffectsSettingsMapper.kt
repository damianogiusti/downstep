package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality

object EffectsSettingsMapper {
    private val defaults = EffectsSettings()

    fun toDomain(dto: EffectsSettingsDto) = EffectsSettings(
        eqEnabled = dto.eqEnabled,
        preampDb = dto.preampDb.clampDb(),
        // A stored list of the wrong size (older format) falls back to flat.
        bandGainsDb = dto.bandGainsDb.takeIf { it.size == defaults.bandGainsDb.size }
            ?.map { it.clampDb() }
            ?: defaults.bandGainsDb,
        quality = ProcessingQuality.entries.firstOrNull { it.name == dto.qualityName } ?: defaults.quality,
    )

    fun toDto(settings: EffectsSettings) = EffectsSettingsDto(
        eqEnabled = settings.eqEnabled,
        preampDb = settings.preampDb,
        bandGainsDb = settings.bandGainsDb,
        qualityName = settings.quality.name,
    )

    private fun Float.clampDb() = coerceIn(EffectsSettings.MIN_GAIN_DB, EffectsSettings.MAX_GAIN_DB)
}
