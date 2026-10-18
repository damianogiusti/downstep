package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.domain.tempo.TempoDetector
import com.damianogiusti.slowdowner.domain.tempo.TempoEstimate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The C engine's tempo analysis of the open file, on [dispatcher]: it keeps a core busy for a moment. */
class NativeTempoDetector(
    private val engine: DesktopPlaybackEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TempoDetector {

    override suspend fun detect(hintBpm: Float?): TempoEstimate? = withContext(dispatcher) {
        engine.detectTempo(hintBpm?.toDouble() ?: 0.0)?.let { found ->
            TempoEstimate(found.bpm.toFloat(), found.beatOffsetSeconds.toFloat(), found.confidence.toFloat())
        }
    }
}
