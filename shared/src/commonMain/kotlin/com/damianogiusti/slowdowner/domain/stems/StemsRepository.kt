package com.damianogiusti.slowdowner.domain.stems

import kotlinx.coroutines.flow.Flow

interface StemsRepository {
    /** Null when the track is not separated. */
    fun observe(trackId: String): Flow<TrackStems?>

    /** Ids of the tracks that have stems, for a badge in the library. */
    fun observeSeparatedTrackIds(): Flow<Set<String>>

    /**
     * The stem files of the track with their saved mix, for playing. Null when the track is not separated or
     * a stem file is missing on disk: the track then plays as the original.
     */
    suspend fun playableStems(trackId: String): PlayableStems?

    /** Does nothing when the track is not separated. */
    suspend fun setMode(trackId: String, mode: TrackMode)

    /** Clamps [gain] to 0..1. Does nothing when the track does not have [stem]. */
    suspend fun setGain(trackId: String, stem: StemKind, gain: Float)

    /** Mute silences the stem without touching its level. Does nothing when the track does not have [stem]. */
    suspend fun setMuted(trackId: String, stem: StemKind, isMuted: Boolean)

    /** Does nothing when the track does not have [stem]. */
    suspend fun setSoloed(trackId: String, stem: StemKind, isSoloed: Boolean)

    /** Removes the stem rows and the stem files. Does nothing when the track is not separated. */
    suspend fun delete(trackId: String)
}
