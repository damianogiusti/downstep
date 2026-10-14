package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.practice.PracticeDao
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsColumns
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [PracticeDao]; [existingTrackIds] stands in for the tracks table. */
class FakePracticeDao(private val existingTrackIds: Set<String>) : PracticeDao {

    val states = MutableStateFlow<List<PracticeStateEntity>>(emptyList())
    val presets = MutableStateFlow<List<PracticePresetEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun findState(trackId: String) = states.value.firstOrNull { it.trackId == trackId }

    override fun observeStates(): Flow<List<PracticeStateEntity>> = states

    override suspend fun insertOrReplaceStateWhereTrackExists(
        trackId: String,
        speedPercent: Float,
        pitchSemitones: Float,
        loopStart: Float,
        loopEnd: Float,
        isLoopOn: Boolean,
        now: Long,
    ) {
        if (trackId !in existingTrackIds) return
        val row = PracticeStateEntity(
            trackId,
            PracticeSettingsColumns(speedPercent, pitchSemitones, loopStart, loopEnd, isLoopOn),
            now,
        )
        states.update { current -> current.filterNot { it.trackId == trackId } + row }
    }

    override fun observePresets(trackId: String): Flow<List<PracticePresetEntity>> =
        presets.map { all -> all.filter { it.trackId == trackId } }

    override suspend fun presetNameExists(trackId: String, name: String) =
        presets.value.any { it.trackId == trackId && it.name == name }

    override suspend fun insertPreset(preset: PracticePresetEntity): Long {
        val id = nextId++
        presets.update { it + preset.copy(id = id) }
        return id
    }

    override suspend fun deletePreset(presetId: Long) = presets.update { all -> all.filterNot { it.id == presetId } }
}
