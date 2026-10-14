package com.damianogiusti.slowdowner.data.practice

import com.damianogiusti.slowdowner.data.database.isConstraintViolation
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeRepository
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.practice.SavePresetResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultPracticeRepository(
    private val dao: PracticeDao,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val now: () -> Long = System::currentTimeMillis,
) : PracticeRepository {

    override suspend fun lastState(trackId: String): PracticeSettings? = withContext(dispatcher) {
        dao.findState(trackId)?.let { PracticeSettingsMapper.toDomain(it.settings) }
    }

    override suspend fun saveLastState(trackId: String, settings: PracticeSettings) = withContext(dispatcher) {
        dao.upsertStateIfTrackExists(trackId, PracticeSettingsMapper.toColumns(settings), now())
    }

    override fun observeLastStates(): Flow<Map<String, PracticeSettings>> =
        dao.observeStates()
            .map { rows -> rows.associate { it.trackId to PracticeSettingsMapper.toDomain(it.settings) } }
            .flowOn(dispatcher)

    override fun observePresets(trackId: String): Flow<List<PracticePreset>> =
        dao.observePresets(trackId)
            .map { rows -> rows.map(PracticeSettingsMapper::toDomain) }
            .flowOn(dispatcher)

    override suspend fun savePreset(
        trackId: String,
        name: String,
        settings: PracticeSettings,
    ): SavePresetResult = withContext(dispatcher) {
        if (dao.presetNameExists(trackId, name)) return@withContext SavePresetResult.NameTaken
        val entity = PracticePresetEntity(
            trackId = trackId,
            name = name,
            settings = PracticeSettingsMapper.toColumns(settings),
            createdAt = now(),
        )
        try {
            val id = dao.insertPreset(entity)
            SavePresetResult.Saved(PracticeSettingsMapper.toDomain(entity.copy(id = id)))
        } catch (e: Exception) {
            // Lost a race with another save of the same name: the unique index caught it.
            if (!isConstraintViolation(e)) throw e
            SavePresetResult.NameTaken
        }
    }

    override suspend fun deletePreset(presetId: Long) = withContext(dispatcher) { dao.deletePreset(presetId) }
}
