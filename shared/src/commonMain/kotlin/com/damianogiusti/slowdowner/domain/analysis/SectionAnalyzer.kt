package com.damianogiusti.slowdowner.domain.analysis

import com.damianogiusti.slowdowner.domain.library.LibraryTrack

/**
 * Seam for the design's Analyze button: finds the sections of a track (verse, chorus, bridge…) to
 * offer as loops. The intended backend is a section/beat detection model, with an LLM only for
 * naming.
 *
 * There is NO implementation yet, on purpose: the app must not show made-up sections. DI binds
 * nothing for this interface, so [AnalyzeSectionsUseCase.isAvailable] is false and the UI shows
 * Analyze as unavailable. A real implementation only needs a binding to light the feature up.
 */
interface SectionAnalyzer {
    /** Suggestions only: the app never turns them into loops without the user accepting each one. */
    suspend fun analyze(track: LibraryTrack): List<SuggestedSection>
}

/** A proposed loop; [start] and [end] are fractions 0..1 of the file. */
data class SuggestedSection(val name: String, val start: Float, val end: Float)
