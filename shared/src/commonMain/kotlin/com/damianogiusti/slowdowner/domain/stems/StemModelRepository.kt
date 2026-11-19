package com.damianogiusti.slowdowner.domain.stems

import kotlinx.coroutines.flow.Flow

interface StemModelRepository {
    /** Cold: checks the files when collected, and again after [download] and [delete]. */
    fun observe(): Flow<StemModelState>

    /** [onProgress] gets 0..1 over the bytes of all the model's files. */
    suspend fun download(onProgress: (Float) -> Unit): ModelDownloadResult

    suspend fun delete()
}
