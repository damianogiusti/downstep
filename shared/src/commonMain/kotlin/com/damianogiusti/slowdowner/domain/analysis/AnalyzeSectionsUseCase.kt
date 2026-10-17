package com.damianogiusti.slowdowner.domain.analysis

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import kotlinx.coroutines.CancellationException

/**
 * Runs section analysis when a [SectionAnalyzer] is bound. [analyzer] is null when the platform has
 * none, which today is always (see [SectionAnalyzer]).
 */
class AnalyzeSectionsUseCase(private val analyzer: SectionAnalyzer?) {

    val isAvailable: Boolean get() = analyzer != null

    suspend operator fun invoke(track: LibraryTrack): SectionAnalysisResult {
        val analyzer = analyzer ?: return SectionAnalysisResult.Unavailable
        return try {
            val sections = analyzer.analyze(track)
                .filter { it.start.isFinite() && it.end.isFinite() }
                .map { it.copy(start = it.start.coerceIn(0f, 1f), end = it.end.coerceIn(0f, 1f)) }
                .filter { it.start < it.end }
                .sortedBy { it.start }
            SectionAnalysisResult.Success(sections)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SectionAnalysisResult.Failure(e)
        }
    }
}

sealed interface SectionAnalysisResult {
    /** No analyzer on this platform: show Analyze as unavailable, never fake sections. */
    data object Unavailable : SectionAnalysisResult
    data class Success(val sections: List<SuggestedSection>) : SectionAnalysisResult
    data class Failure(val error: Throwable) : SectionAnalysisResult
}
