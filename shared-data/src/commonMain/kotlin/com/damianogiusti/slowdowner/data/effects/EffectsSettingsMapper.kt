package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality

object EffectsSettingsMapper {
    private val defaults = EffectsSettings()

    fun toDomain(dto: EffectsSettingsDto) = EffectsSettings(
        // The curve when there is one; else the first EQ's gains, migrated; else flat.
        eq = EqCurveCodec.decode(dto.eqCurveJson)
            ?: dto.legacyEq?.let { EqCurve.fromLegacyGains(it.enabled, it.preampDb, it.bandGainsDb) }
            ?: defaults.eq,
        quality = ProcessingQuality.entries.firstOrNull { it.name == dto.qualityName } ?: defaults.quality,
    )

    fun toDto(settings: EffectsSettings) = EffectsSettingsDto(
        eqCurveJson = EqCurveCodec.encode(settings.eq),
        qualityName = settings.quality.name,
    )
}
