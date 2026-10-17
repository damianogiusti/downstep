package com.damianogiusti.slowdowner.domain.analysis

import com.damianogiusti.slowdowner.domain.click.ClickTrackEngine
import com.damianogiusti.slowdowner.domain.click.SetCountInUseCase
import com.damianogiusti.slowdowner.domain.click.SetMetronomeUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The optional backends: absent means unavailable, never made-up results. */
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

    @Test
    fun `when no click engine is bound then metronome and count-in are unavailable`() {
        val metronome = SetMetronomeUseCase(engine = null)
        val countIn = SetCountInUseCase(engine = null)

        assertFalse(metronome.isAvailable)
        assertFalse(countIn.isAvailable)
        assertFalse(metronome(enabled = true, bpm = 120f))
        assertFalse(countIn(enabled = true, bpm = 120f))
    }

    @Test
    fun `when a click engine is bound then it needs a tempo to start`() {
        val calls = mutableListOf<String>()
        val engine = object : ClickTrackEngine {
            override fun setMetronome(enabled: Boolean, bpm: Float) { calls += "metronome:$enabled:$bpm" }
            override fun setCountIn(enabled: Boolean, bars: Int, bpm: Float) { calls += "countIn:$enabled:$bars:$bpm" }
        }
        val metronome = SetMetronomeUseCase(engine)

        assertTrue(metronome.isAvailable)
        assertFalse(metronome(enabled = true, bpm = null))
        assertTrue(metronome(enabled = true, bpm = 92f))
        assertTrue(SetCountInUseCase(engine)(enabled = true, bpm = 92f, bars = 9))
        assertEquals(listOf("metronome:true:92.0", "countIn:true:4:92.0"), calls)
    }
}
