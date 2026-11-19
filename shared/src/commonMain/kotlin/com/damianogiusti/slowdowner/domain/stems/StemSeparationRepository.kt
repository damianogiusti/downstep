package com.damianogiusti.slowdowner.domain.stems

import kotlinx.coroutines.flow.Flow

/** Splits a library track into stems, in the background and one track at a time. */
interface StemSeparationRepository {
    /** Cold: gives the current state first, then every change. Collecting it does not start or stop the job. */
    fun observe(): Flow<SeparationJobState>

    /** Returns as soon as the job has begun; follow it with [observe]. Replaces an earlier outcome. */
    suspend fun start(trackId: String): SeparationStart

    /** Stops the running job, if any, and returns after it has cleaned up. The state becomes `Finished(Cancelled)`. */
    suspend fun cancel()

    /** Clears a [SeparationJobState.Finished] once the user has seen it. Does nothing while a job runs. */
    suspend fun acknowledge()
}
