package com.damianogiusti.slowdowner.data.loops

import com.damianogiusti.slowdowner.data.database.isConstraintViolation
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsMapper
import com.damianogiusti.slowdowner.domain.loops.Loop
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.LoopRepository
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.loops.TrackLoops
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class DefaultLoopRepository(
    private val dao: LoopDao,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val now: () -> Long = System::currentTimeMillis,
) : LoopRepository {

    override fun observe(trackId: String): Flow<TrackLoops> =
        combine(dao.observeLoops(trackId), dao.observeSettings(trackId)) { loops, settings ->
            LoopMapper.toDomain(trackId, loops, settings)
        }.flowOn(dispatcher)

    override suspend fun trackLoops(trackId: String): TrackLoops = withContext(dispatcher) {
        LoopMapper.toDomain(trackId, dao.loopsOf(trackId), dao.findSettings(trackId))
    }

    override suspend fun loop(loopId: Long): Loop? = withContext(dispatcher) {
        dao.findLoop(loopId)?.let(LoopMapper::toDomain)
    }

    override suspend fun create(trackId: String, name: String, bounds: LoopBounds): Loop? = withContext(dispatcher) {
        val entity = LoopEntity(
            trackId = trackId,
            name = name,
            start = bounds.start,
            end = bounds.end,
            passes = 0,
            notes = "",
            progression = LoopMapper.toColumns(Progression.DEFAULT),
            createdAt = now(),
        )
        try {
            LoopMapper.toDomain(entity.copy(id = dao.insertLoop(entity)))
        } catch (e: Exception) {
            // The track was deleted meanwhile: the foreign key caught it.
            if (!isConstraintViolation(e)) throw e
            null
        }
    }

    override suspend fun rename(loopId: Long, name: String) = withContext(dispatcher) { dao.rename(loopId, name) }

    override suspend fun updateNotes(loopId: Long, notes: String) = withContext(dispatcher) { dao.updateNotes(loopId, notes) }

    override suspend fun updateBounds(loopId: Long, bounds: LoopBounds) =
        withContext(dispatcher) { dao.updateBounds(loopId, bounds.start, bounds.end) }

    override suspend fun addPass(loopId: Long): Loop? = withContext(dispatcher) {
        dao.addPass(loopId)
        dao.findLoop(loopId)?.let(LoopMapper::toDomain)
    }

    override suspend fun resetPasses(loopId: Long) = withContext(dispatcher) { dao.resetPasses(loopId) }

    override suspend fun updateProgression(loopId: Long, progression: Progression) =
        withContext(dispatcher) { dao.updateProgression(loopId, LoopMapper.toColumns(progression)) }

    override suspend fun delete(loopId: Long) = withContext(dispatcher) { dao.deleteLoop(loopId) }

    override suspend fun setActiveLoop(trackId: String, loopId: Long?) = withSettingsRow(trackId) {
        dao.setActiveLoop(trackId, loopId)
    }

    override suspend fun setBpm(trackId: String, bpm: Float?) = withSettingsRow(trackId) { dao.setBpm(trackId, bpm) }

    override suspend fun setSnapToBeat(trackId: String, enabled: Boolean) = withSettingsRow(trackId) {
        dao.setSnapToBeat(trackId, enabled)
    }

    // Two statements, no transaction: both are idempotent, and the auto-save's REPLACE in between
    // keeps the loop columns.
    private suspend fun withSettingsRow(trackId: String, update: suspend () -> Unit) = withContext(dispatcher) {
        dao.ensureStateRow(trackId, DEFAULT_STATE, now())
        update()
    }

    private companion object {
        val DEFAULT_STATE = PracticeSettingsMapper.toColumns(PracticeSettings())
    }
}
