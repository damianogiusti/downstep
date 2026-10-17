package com.damianogiusti.slowdowner.domain.effects

/** The EQ a track plays with: its own, else the global one. No track ([trackId] null): the global one. */
class LoadTrackEqUseCase(
    private val trackEq: TrackEqRepository,
    private val settings: EffectsSettingsRepository,
) {
    suspend operator fun invoke(trackId: String?): EqCurve =
        (trackId?.let { trackEq.trackEq(it) } ?: settings.current().eq).sanitised()
}
