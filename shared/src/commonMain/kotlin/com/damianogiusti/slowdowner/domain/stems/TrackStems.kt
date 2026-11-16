package com.damianogiusti.slowdowner.domain.stems

/**
 * Stems of a separated track. [model] identifies the model that made them (the data layer decides
 * the ids). Speed, pitch and loop are not here: they stay in `PracticeSettings`, shared by both modes.
 */
data class TrackStems(val trackId: String, val model: String, val mode: TrackMode, val mix: StemMix)
