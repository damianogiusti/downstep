package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.stems.ModelDownloadResult
import com.damianogiusti.slowdowner.domain.stems.StemModelRepository
import com.damianogiusti.slowdowner.domain.stems.StemModelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStemModelRepository(var result: ModelDownloadResult = ModelDownloadResult.Success) : StemModelRepository {

    val state = MutableStateFlow<StemModelState>(StemModelState.NotDownloaded)
    var downloads = 0

    override fun observe(): Flow<StemModelState> = state

    override suspend fun download(onProgress: (Float) -> Unit): ModelDownloadResult {
        downloads++
        onProgress(0.5f)
        onProgress(1f)
        if (result == ModelDownloadResult.Success) state.value = StemModelState.Ready
        return result
    }

    override suspend fun delete() {
        state.value = StemModelState.NotDownloaded
    }
}
