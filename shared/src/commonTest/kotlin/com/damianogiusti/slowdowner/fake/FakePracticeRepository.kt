package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeRepository
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.practice.SavePresetResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakePracticeRepository : PracticeRepository {

    val states = MutableStateFlow<Map<String, PracticeSettings>>(emptyMap())
    val presets = MutableStateFlow<List<PracticePreset>>(emptyList())
    /** Track ids in save order, to check when saves happen. */
    val savedTrackIds = mutableListOf<String>()
    private var nextPresetId = 1L

    override suspend fun lastState(trackId: String) = states.value[trackId]

    override suspend fun saveLastState(trackId: String, settings: PracticeSettings) {
        savedTrackIds += trackId
        states.update { it + (trackId to settings) }
    }

    override fun observeLastStates(): Flow<Map<String, PracticeSettings>> = states

    override fun observePresets(trackId: String): Flow<List<PracticePreset>> =
        presets.map { all -> all.filter { it.trackId == trackId } }

    override suspend fun savePreset(trackId: String, name: String, settings: PracticeSettings, eq: EqCurve?): SavePresetResult {
        if (presets.value.any { it.trackId == trackId && it.name == name }) return SavePresetResult.NameTaken
        val preset = PracticePreset(nextPresetId++, trackId, name, settings, eq)
        presets.update { it + preset }
        return SavePresetResult.Saved(preset)
    }

    override suspend fun deletePreset(presetId: Long) = presets.update { all -> all.filterNot { it.id == presetId } }
}
