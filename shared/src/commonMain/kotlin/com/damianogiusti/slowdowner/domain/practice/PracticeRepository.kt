package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.effects.EqCurve
import kotlinx.coroutines.flow.Flow

/** Per-track practice settings: the last state used, plus named presets. */
interface PracticeRepository {
    suspend fun lastState(trackId: String): PracticeSettings?
    suspend fun saveLastState(trackId: String, settings: PracticeSettings)
    /** Last state of every track that has one, keyed by track id. */
    fun observeLastStates(): Flow<Map<String, PracticeSettings>>
    fun observePresets(trackId: String): Flow<List<PracticePreset>>
    /** Returns [SavePresetResult.Saved] or [SavePresetResult.NameTaken]. [eq] null saves no EQ with it. */
    suspend fun savePreset(trackId: String, name: String, settings: PracticeSettings, eq: EqCurve? = null): SavePresetResult
    suspend fun deletePreset(presetId: Long)
}
