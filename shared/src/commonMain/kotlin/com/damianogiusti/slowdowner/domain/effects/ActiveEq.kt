package com.damianogiusti.slowdowner.domain.effects

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The EQ of the open track as the user shaped it (Isolate aside), shared by the EQ editor, which
 * owns it, and the presets, which save it and can ask for another one. One per process.
 */
class ActiveEq {
    data class Current(val trackId: String?, val curve: EqCurve)

    private val _current = MutableStateFlow<Current?>(null)
    /** Published by the editor at each load and edit; null until the first one. */
    val current: StateFlow<Current?> = _current.asStateFlow()

    // Kept until the editor takes them, also while it is not listening yet.
    private val _requests = Channel<Current>(Channel.CONFLATED)
    /** Curves asked for by others (a preset): the editor plays and saves them as an edit. */
    val requests: Flow<Current> = _requests.receiveAsFlow()

    fun publish(trackId: String?, curve: EqCurve) {
        _current.value = Current(trackId, curve)
    }

    /** The curve [trackId] has now, if it is the one open. */
    fun curveOf(trackId: String): EqCurve? = _current.value?.takeIf { it.trackId == trackId }?.curve

    fun request(trackId: String?, curve: EqCurve) {
        _requests.trySend(Current(trackId, curve.sanitised()))
    }
}
