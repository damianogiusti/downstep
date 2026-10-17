package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.click.ClickSettings
import com.damianogiusti.slowdowner.domain.click.ClickSettingsRepository
import com.damianogiusti.slowdowner.domain.click.ClickTrackEngine
import com.damianogiusti.slowdowner.domain.click.TrackTempo
import com.damianogiusti.slowdowner.domain.tempo.TempoDetector
import com.damianogiusti.slowdowner.domain.tempo.TempoEstimate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what the engine was told: the last value of each, plus every call in order. */
class FakeClickEngine : ClickTrackEngine {
    val calls = mutableListOf<String>()
    var tempo: TrackTempo? = null
        private set
    var isMetronomeOn = false
        private set
    var volume = 0f
        private set
    var countInBeats = 0
        private set
    var countInEveryLoop = false
        private set
    override var countInRemaining = 0

    override fun setTempo(tempo: TrackTempo?) {
        this.tempo = tempo
        calls += "tempo:${tempo?.bpm}:${tempo?.beatOffsetSeconds}"
    }

    override fun setMetronome(enabled: Boolean, volume: Float) {
        isMetronomeOn = enabled
        this.volume = volume
        calls += "metronome:$enabled:$volume"
    }

    override fun setCountIn(beats: Int, everyLoop: Boolean) {
        countInBeats = beats
        countInEveryLoop = everyLoop
        calls += "countIn:$beats:$everyLoop"
    }
}

/**
 * Answers with [estimate] (or, given a hint, that tempo with [hintedOffset], or null when it
 * [findsHinted] nothing); counts the analyses. [during] runs while one analyses, with its hint.
 */
class FakeTempoDetector(
    var estimate: TempoEstimate? = TempoEstimate(120f, 0.25f, 0.9f),
    var hintedOffset: Float = 0.1f,
) : TempoDetector {
    val requests = mutableListOf<Float?>()
    var findsHinted = true
    var during: (hintBpm: Float?) -> Unit = {}

    override suspend fun detect(hintBpm: Float?): TempoEstimate? {
        requests += hintBpm
        during(hintBpm)
        return when {
            hintBpm == null -> estimate
            findsHinted -> TempoEstimate(hintBpm, hintedOffset, 0.5f)
            else -> null
        }
    }
}

class FakeClickSettingsRepository(initial: ClickSettings = ClickSettings()) : ClickSettingsRepository {
    val settings = MutableStateFlow(initial)
    override fun observe(): Flow<ClickSettings> = settings
    override suspend fun save(settings: ClickSettings) {
        this.settings.value = settings
    }
}
