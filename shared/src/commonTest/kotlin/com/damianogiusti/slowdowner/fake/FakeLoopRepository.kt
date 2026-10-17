package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.loops.Loop
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.LoopRepository
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.loops.TrackLoops
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** In-memory [LoopRepository]; only [existingTrackIds] can get loops, like the foreign key. */
class FakeLoopRepository(private val existingTrackIds: Set<String> = setOf("a", "b")) : LoopRepository {

    val loops = MutableStateFlow<List<Loop>>(emptyList())
    /** Per-track settings, with the active id as written (possibly stale, like the column). */
    val settings = MutableStateFlow<Map<String, TrackLoops>>(emptyMap())
    private var nextId = 1L

    override fun observe(trackId: String): Flow<TrackLoops> = combine(loops, settings) { _, _ -> snapshot(trackId) }

    override suspend fun trackLoops(trackId: String) = snapshot(trackId)

    override suspend fun loop(loopId: Long) = loops.value.firstOrNull { it.id == loopId }

    override suspend fun create(trackId: String, name: String, bounds: LoopBounds): Loop? {
        if (trackId !in existingTrackIds) return null
        val loop = Loop(nextId++, trackId, name, bounds.start, bounds.end, createdAt = nextId)
        loops.update { it + loop }
        return loop
    }

    override suspend fun rename(loopId: Long, name: String) = edit(loopId) { it.copy(name = name) }

    override suspend fun updateNotes(loopId: Long, notes: String) = edit(loopId) { it.copy(notes = notes) }

    override suspend fun updateBounds(loopId: Long, bounds: LoopBounds) =
        edit(loopId) { it.copy(start = bounds.start, end = bounds.end) }

    override suspend fun addPass(loopId: Long): Loop? {
        edit(loopId) { it.copy(passes = it.passes + 1) }
        return loop(loopId)
    }

    override suspend fun resetPasses(loopId: Long) = edit(loopId) { it.copy(passes = 0) }

    override suspend fun updateProgression(loopId: Long, progression: Progression) =
        edit(loopId) { it.copy(progression = progression) }

    override suspend fun delete(loopId: Long) = loops.update { all -> all.filterNot { it.id == loopId } }

    override suspend fun setActiveLoop(trackId: String, loopId: Long?) = editSettings(trackId) { it.copy(activeLoopId = loopId) }

    override suspend fun setBpm(trackId: String, bpm: Float?) = editSettings(trackId) { it.copy(bpm = bpm) }

    private fun snapshot(trackId: String): TrackLoops {
        val trackLoops = loops.value.filter { it.trackId == trackId }.sortedWith(compareBy({ it.start }, { it.id }))
        val stored = settings.value[trackId] ?: TrackLoops(trackId)
        return stored.copy(loops = trackLoops, activeLoopId = stored.activeLoopId?.takeIf { id -> trackLoops.any { it.id == id } })
    }

    private fun edit(loopId: Long, change: (Loop) -> Loop) =
        loops.update { all -> all.map { if (it.id == loopId) change(it) else it } }

    private fun editSettings(trackId: String, change: (TrackLoops) -> TrackLoops) =
        settings.update { it + (trackId to change(it[trackId] ?: TrackLoops(trackId))) }
}
