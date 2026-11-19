package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.stems.PlayableStem
import com.damianogiusti.slowdowner.domain.stems.PlayableStems
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemMix
import com.damianogiusti.slowdowner.domain.stems.StemsRepository
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.stems.TrackStems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeStemsRepository(initial: List<TrackStems> = emptyList()) : StemsRepository {

    val stems = MutableStateFlow(initial.associateBy { it.trackId })
    /** Track ids in the order they were deleted. */
    val deleted = mutableListOf<String>()

    /** Tracks whose stem files are gone from disk. */
    val missingFiles = mutableSetOf<String>()

    override suspend fun playableStems(trackId: String): PlayableStems? {
        val track = stems.value[trackId] ?: return null
        if (trackId in missingFiles) return null
        return PlayableStems(
            track.mode,
            track.mix.gains.map { (kind, gain) -> PlayableStem(kind, "/stems/$trackId/${kind.name}.mp3", gain) },
        )
    }

    override fun observe(trackId: String): Flow<TrackStems?> = stems.map { it[trackId] }

    override fun observeSeparatedTrackIds(): Flow<Set<String>> = stems.map { it.keys }

    override suspend fun setMode(trackId: String, mode: TrackMode) =
        stems.update { all -> all[trackId]?.let { all + (trackId to it.copy(mode = mode)) } ?: all }

    override suspend fun setGain(trackId: String, stem: StemKind, gain: Float) = stems.update { all ->
        val current = all[trackId] ?: return@update all
        if (stem !in current.mix.gains) return@update all
        all + (trackId to current.copy(mix = StemMix(current.mix.gains + (stem to gain.coerceIn(0f, 1f)))))
    }

    override suspend fun delete(trackId: String) {
        deleted += trackId
        stems.update { it - trackId }
    }
}
