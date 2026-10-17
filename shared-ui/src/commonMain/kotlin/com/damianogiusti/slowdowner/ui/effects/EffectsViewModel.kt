package com.damianogiusti.slowdowner.ui.effects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.effects.ActiveEq
import com.damianogiusti.slowdowner.domain.effects.EffectsEdit
import com.damianogiusti.slowdowner.domain.effects.EffectsEngine
import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.effects.IsolationBands
import com.damianogiusti.slowdowner.domain.effects.LoadTrackEqUseCase
import com.damianogiusti.slowdowner.domain.effects.ObserveEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import com.damianogiusti.slowdowner.domain.effects.SetBandIsolationUseCase
import com.damianogiusti.slowdowner.domain.effects.UpdateEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.SLIDER_MAX
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The effects of the open track: its own EQ (else the global one), the quality and the session's
 * mix and Isolate. Scoped to the window: it follows the track that is playing, loading each one's
 * EQ, and saves every edit with the track it was made on ([UpdateEffectsSettingsUseCase]).
 */
class EffectsViewModel(
    private val engine: EffectsEngine,
    observeSettings: ObserveEffectsSettingsUseCase,
    private val updateSettings: UpdateEffectsSettingsUseCase,
    private val setBandIsolation: SetBandIsolationUseCase,
    private val loadTrackEq: LoadTrackEqUseCase,
    observeNowPlaying: ObserveNowPlayingUseCase,
    private val activeEq: ActiveEq,
) : ViewModel() {

    private val _state = MutableStateFlow<EffectsUiState?>(null)
    val state: StateFlow<EffectsUiState?> = _state.asStateFlow()

    private val _isolation = MutableStateFlow(IsolationBands.FULL_MIX)
    /** Isolate (Low / Mid / High solo): for this session only, never saved with the EQ. */
    val isolation: StateFlow<IsolationBands> = _isolation.asStateFlow()

    // Edits and loads in order, for the engine and the disk: each one is played, edits also saved.
    private val changes = Channel<EffectsEdit>(Channel.UNLIMITED)

    /** The last edit, and the last one saved: [flushPendingSave] writes the first if they differ. */
    @Volatile
    private var lastEdit: EffectsEdit? = null
    @Volatile
    private var saved: EffectsEdit? = null

    init {
        viewModelScope.launch {
            val initial = observeSettings().first()
            _state.value = EffectsUiState(initial, 0, 0).withMix()
            activeEq.publish(null, initial.eq)
            launch {
                // Each value reaches the engine at once and is saved once they settle, the last one
                // even if this ViewModel is cleared first.
                updateSettings(changes.receiveAsFlow(), onSaved = { saved = it })
            }
            launch {
                observeNowPlaying().map { it?.id }.distinctUntilChanged().collectLatest(::load)
            }
            launch {
                // A preset with an EQ: as if the user had shaped it, so it is saved with the track.
                activeEq.requests.collect { request ->
                    if (request.trackId == _state.value?.trackId) edit { it.copy(eq = request.curve) }
                }
            }
        }
    }

    /**
     * Saves the edit the debounce still holds, and waits for it. Called when the app quits: the
     * process exits before the pause ends, and nothing clears this ViewModel on the way out.
     */
    suspend fun flushPendingSave() {
        val edit = lastEdit ?: return
        if (edit === saved) return
        updateSettings.saveNow(edit)
        saved = edit
    }

    fun onMixChanged(sliderPosition: Int) {
        engine.setMixFromSlider(sliderPosition)
        _state.update { it?.withMix() }
    }

    fun onMixReset() = onMixChanged(SLIDER_MAX / 2)

    fun onEqToggled(enabled: Boolean) = editEq { it.copy(enabled = enabled) }

    fun onPreampChanged(gainDb: Float) = editEq { it.copy(preampDb = gainDb) }

    /** A node of the graph moved: its frequency, gain or width. */
    fun onBandChanged(index: Int, band: EqBand) = editEq { it.withBand(index, band) }

    /** A double click on a node: back to 0 dB where it is. */
    fun onBandGainReset(index: Int) = editEq { curve ->
        curve.bands.getOrNull(index)?.let { curve.withBand(index, it.copy(gainDb = 0f)) } ?: curve
    }

    /** The starting layout again, flat: on or off stays. */
    fun onEqReset() = editEq { EqCurve(enabled = it.enabled) }

    fun onQualitySelected(quality: ProcessingQuality) = edit { it.copy(quality = quality) }

    fun onIsolationChanged(isolation: IsolationBands) {
        _isolation.value = isolation
        setBandIsolation(isolation)
    }

    // Nothing could turn Isolate off any more: give the full mix back.
    override fun onCleared() {
        if (!_isolation.value.isFullMix) setBandIsolation(IsolationBands.FULL_MIX)
    }

    /** Another track opened (or none is): its EQ comes in, played but not saved again. */
    private suspend fun load(trackId: String?) {
        val curve = loadTrackEq(trackId)
        val current = _state.value ?: return
        val settings = current.settings.copy(eq = curve)
        _state.value = current.copy(settings = settings, trackId = trackId)
        activeEq.publish(trackId, curve)
        changes.trySend(EffectsEdit(trackId, settings, isLoad = true))
    }

    private fun editEq(transform: (EqCurve) -> EqCurve) = edit { it.copy(eq = transform(it.eq)) }

    private fun edit(transform: (EffectsSettings) -> EffectsSettings) {
        val current = _state.value ?: return
        val settings = transform(current.settings).let { it.copy(eq = it.eq.sanitised()) }
        if (settings == current.settings) return
        _state.value = current.copy(settings = settings)
        activeEq.publish(current.trackId, settings.eq)
        val edit = EffectsEdit(current.trackId, settings)
        lastEdit = edit
        changes.trySend(edit)
    }

    private fun EffectsUiState.withMix() = copy(
        mixSlider = engine.mixSliderPosition,
        mixRightPercent = (engine.mixRight * 100).roundToInt(),
    )
}
