package com.damianogiusti.slowdowner.data.practice

import com.damianogiusti.slowdowner.data.effects.EqCurveCodec
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings

object PracticeSettingsMapper {
    fun toColumns(settings: PracticeSettings) = PracticeSettingsColumns(
        speedPercent = settings.speedPercent,
        pitchSemitones = settings.pitchSemitones,
        loopStart = settings.loop.start,
        loopEnd = settings.loop.end,
        isLoopOn = settings.loop.isOn,
    )

    fun toDomain(columns: PracticeSettingsColumns) = PracticeSettings(
        speedPercent = columns.speedPercent,
        pitchSemitones = columns.pitchSemitones,
        loop = LoopRegion(columns.loopStart, columns.loopEnd, columns.isLoopOn),
    )

    fun toDomain(entity: PracticePresetEntity) = PracticePreset(
        id = entity.id,
        trackId = entity.trackId,
        name = entity.name,
        settings = toDomain(entity.settings),
        eq = EqCurveCodec.decode(entity.eqCurve),
    )
}
