package com.damianogiusti.slowdowner.ui.stems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.source.OpenResult
import com.damianogiusti.slowdowner.domain.stems.ObserveTrackStemsUseCase
import com.damianogiusti.slowdowner.domain.stems.SetStemGainUseCase
import com.damianogiusti.slowdowner.domain.stems.SetStemMutedUseCase
import com.damianogiusti.slowdowner.domain.stems.SetStemSoloedUseCase
import com.damianogiusti.slowdowner.domain.stems.StemMix
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.SwitchTrackModeUseCase
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.stems.TrackStems
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.resources.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * The Instruments panel: the mode of the open track (original or stems) and the gain of each stem.
 * Window-scoped like the player, which shows the panel's action only while [state] is
 * [InstrumentsUiState.Ready]. Only apps that bind the stem repositories create it.
 *
 * Mute and solo are saved flags beside the level, so a muted stem keeps its level and comes back at it, also
 * after a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InstrumentsViewModel(private val useCases: InstrumentsUseCases) : ViewModel() {

    private val isSwitching = MutableStateFlow(false)

    // A reopen that fails leaves nothing open, so the panel goes away: the reason reaches the user as a message.
    private val _messages = MutableSharedFlow<UiMessage>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    // What the user dragged and the database has not confirmed yet: without it the thumb would jump back
    // to the old saved value for the moment between the end of the drag and the saved one arriving.
    private val pendingGains = MutableStateFlow<Map<StemKind, Float>>(emptyMap())
    private val gainJobs = mutableMapOf<StemKind, Job>()

    val state: StateFlow<InstrumentsUiState> = combine(separatedTrack(), isSwitching, pendingGains) { separated, isSwitching, pending ->
        if (separated == null) {
            InstrumentsUiState.Unavailable
        } else {
            InstrumentsUiState.Ready(
                trackId = separated.trackId,
                mode = separated.stems.mode,
                isSwitching = isSwitching,
                stems = separated.stems.mix.gains.entries
                    .sortedBy { it.key.ordinal }
                    .map { (kind, saved) -> separated.stems.mix.toRow(kind, pending[kind] ?: saved) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InstrumentsUiState.Unavailable)

    /** The open track with its stems, or null. A track change starts from a clean slate. */
    private fun separatedTrack(): Flow<SeparatedTrack?> = openTrackId()
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId == null) {
                flowOf(null)
            } else {
                useCases.observeStems(trackId)
                    .map { stems -> stems?.let { SeparatedTrack(trackId, it) } }
                    .onStart { resetForNewTrack() }
                    .onEach { dropConfirmedGains(it) }
            }
        }

    // A reopen closes the track for its decode (now playing is null) and opens it again: that is not a
    // track change, so the panel keeps its state, and its sheet stays, while a switch is running.
    private fun openTrackId(): Flow<String?> =
        combine(useCases.observeNowPlaying().map { it?.id }, isSwitching) { id, isSwitching -> id to isSwitching }
            .filter { (id, isSwitching) -> id != null || !isSwitching }
            .map { (id, _) -> id }

    fun onModeSelected(mode: TrackMode) {
        val ready = state.value as? InstrumentsUiState.Ready ?: return
        if (ready.isSwitching || ready.mode == mode) return
        val title = useCases.observeNowPlaying().value?.title.orEmpty()
        isSwitching.value = true
        viewModelScope.launch {
            try {
                val message = when (useCases.switchMode(ready.trackId, mode)) {
                    OpenResult.TooLong -> Res.string.error_track_too_long
                    OpenResult.Failed -> Res.string.error_cannot_open
                    OpenResult.Opened, null -> null
                }
                if (message != null) _messages.tryEmit(UiMessage(message, listOf(title)))
            } finally {
                isSwitching.value = false
            }
        }
    }

    fun onGainChanged(stem: StemKind, percent: Int) {
        val ready = state.value as? InstrumentsUiState.Ready ?: return
        if (!ready.areStemControlsEnabled) return
        applyGain(ready.trackId, stem, percent.coerceIn(0, PERCENT) / PERCENT.toFloat())
    }

    fun onMuteToggled(stem: StemKind) {
        val ready = state.value as? InstrumentsUiState.Ready ?: return
        if (!ready.areStemControlsEnabled) return
        val row = ready.stems.firstOrNull { it.kind == stem } ?: return
        viewModelScope.launch { useCases.setMuted(ready.trackId, stem, !row.isMuted) }
    }

    fun onSoloToggled(stem: StemKind) {
        val ready = state.value as? InstrumentsUiState.Ready ?: return
        if (!ready.areStemControlsEnabled) return
        val row = ready.stems.firstOrNull { it.kind == stem } ?: return
        viewModelScope.launch { useCases.setSoloed(ready.trackId, stem, !row.isSoloed) }
    }

    // A drag makes a burst of values: a newer one replaces the one still being saved, and the last always
    // finishes, so the sound follows the slider and the database settles on the final value.
    private fun applyGain(trackId: String, stem: StemKind, gain: Float) {
        pendingGains.update { it + (stem to gain) }
        gainJobs[stem]?.cancel()
        gainJobs[stem] = viewModelScope.launch { useCases.setGain(trackId, stem, gain) }
    }

    private fun resetForNewTrack() {
        gainJobs.values.forEach(Job::cancel)
        gainJobs.clear()
        pendingGains.value = emptyMap()
    }

    private fun dropConfirmedGains(separated: SeparatedTrack?) {
        val saved = separated?.stems?.mix?.gains ?: return
        pendingGains.update { pending -> pending.filter { (kind, gain) -> saved[kind] != gain } }
    }

    private class SeparatedTrack(val trackId: String, val stems: TrackStems)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PERCENT = 100
    }
}

private fun StemMix.toRow(kind: StemKind, level: Float) = InstrumentRow(
    kind = kind,
    name = kind.label,
    gain = level,
    isMuted = kind in mutedStems,
    isSoloed = kind in soloedStems,
    isSilencedBySolo = isSilencedBySolo(kind),
)

private val StemKind.label: StringResource
    get() = when (this) {
        StemKind.VOCALS -> Res.string.stem_vocals
        StemKind.DRUMS -> Res.string.stem_drums
        StemKind.BASS -> Res.string.stem_bass
        StemKind.GUITAR -> Res.string.stem_guitar
        StemKind.PIANO -> Res.string.stem_piano
        StemKind.OTHER -> Res.string.stem_other
    }

/** The use cases of [InstrumentsViewModel], grouped to keep its constructor short. */
class InstrumentsUseCases(
    val observeNowPlaying: ObserveNowPlayingUseCase,
    val observeStems: ObserveTrackStemsUseCase,
    val switchMode: SwitchTrackModeUseCase,
    val setGain: SetStemGainUseCase,
    val setMuted: SetStemMutedUseCase,
    val setSoloed: SetStemSoloedUseCase,
)
