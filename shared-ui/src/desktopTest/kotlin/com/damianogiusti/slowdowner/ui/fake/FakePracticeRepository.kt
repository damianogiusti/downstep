package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeRepository
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.practice.SavePresetResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** Has no saved state and no presets, and keeps nothing. */
class FakePracticeRepository : PracticeRepository {
    override suspend fun lastState(trackId: String): PracticeSettings? = null
    override suspend fun saveLastState(trackId: String, settings: PracticeSettings) = Unit
    override fun observeLastStates(): Flow<Map<String, PracticeSettings>> = emptyFlow()
    override fun observePresets(trackId: String): Flow<List<PracticePreset>> = flowOf(emptyList())
    override suspend fun savePreset(trackId: String, name: String, settings: PracticeSettings) =
        SavePresetResult.NameTaken
    override suspend fun deletePreset(presetId: Long) = Unit
}
