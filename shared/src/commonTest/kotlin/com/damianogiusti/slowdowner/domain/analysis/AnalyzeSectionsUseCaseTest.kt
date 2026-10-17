package com.damianogiusti.slowdowner.domain.analysis

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/** The optional backend: absent means unavailable, never made-up results. */
class AnalyzeSectionsUseCaseTest {

    private val song = LibraryTrack("a", "A", "/a.m4a", null, 0)

    @Test
    fun `when no analyzer is bound then analysis is unavailable`() = runTest {
        val analyze = AnalyzeSectionsUseCase(analyzer = null)

        assertFalse(analyze.isAvailable)
        assertEquals(SectionAnalysisResult.Unavailable, analyze(song))
    }

    @Test
    fun `when the analyzer answers then its sections come sorted and within the file`() = runTest {
        val analyzer = object : SectionAnalyzer {
            override suspend fun analyze(track: LibraryTrack) = listOf(
                SuggestedSection("Chorus", 0.5f, 1.2f),
                SuggestedSection("Verse", 0.1f, 0.3f),
                SuggestedSection("Broken", 0.4f, 0.4f),
            )
        }

        val result = AnalyzeSectionsUseCase(analyzer)(song)

        assertEquals(
            SectionAnalysisResult.Success(listOf(SuggestedSection("Verse", 0.1f, 0.3f), SuggestedSection("Chorus", 0.5f, 1f))),
            result,
        )
    }

    @Test
    fun `when the analyzer fails then the failure is returned`() = runTest {
        val analyzer = object : SectionAnalyzer {
            override suspend fun analyze(track: LibraryTrack): List<SuggestedSection> = error("model missing")
        }

        assertIs<SectionAnalysisResult.Failure>(AnalyzeSectionsUseCase(analyzer)(song))
    }
}
