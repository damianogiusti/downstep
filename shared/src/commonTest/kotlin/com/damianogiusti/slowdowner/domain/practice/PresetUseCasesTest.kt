package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakePracticeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class PresetUseCasesTest {

    private val engine = FakeEngine()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val repository = FakePracticeRepository()
    private val savePreset = SavePresetUseCase(engine, nowPlaying, repository)
    private val song = LibraryTrack("a", "A", "/a.m4a", null, 0)

    @Test
    fun `when the name is blank then the preset is rejected`() = runTest {
        nowPlaying.set(song)

        assertEquals(SavePresetResult.InvalidName, savePreset("   "))
    }

    @Test
    fun `when the name is too long then the preset is rejected`() = runTest {
        nowPlaying.set(song)

        assertEquals(SavePresetResult.InvalidName, savePreset("x".repeat(SavePresetUseCase.MAX_NAME_LENGTH + 1)))
    }

    @Test
    fun `when nothing is open then the preset is rejected`() = runTest {
        assertEquals(SavePresetResult.NothingOpen, savePreset("Slow"))
    }

    @Test
    fun `when saved then the preset holds the trimmed name and current settings`() = runTest {
        nowPlaying.set(song)
        engine.speed = 60f

        val result = savePreset("  Slow  ") as SavePresetResult.Saved

        assertEquals("Slow", result.preset.name)
        assertEquals(60f, result.preset.settings.speedPercent)
    }

    @Test
    fun `when the name is already used on the track then it is reported`() = runTest {
        nowPlaying.set(song)
        savePreset("Slow")

        assertEquals(SavePresetResult.NameTaken, savePreset("Slow"))
    }

    @Test
    fun `when a preset is applied then the engine gets its speed pitch and loop`() {
        val preset = PracticePreset(1, "a", "Solo", PracticeSettings(75f, -2f, LoopRegion(0.2f, 0.4f, isOn = true)))

        ApplyPresetUseCase(engine)(preset)

        assertEquals(
            listOf("speed:75.0", "pitch:-2.0", "loop:${LoopRegion(0.2f, 0.4f, isOn = true)}"),
            engine.practiceCalls,
        )
    }

    @Test
    fun `when a preset is deleted then it is no longer observed`() = runTest {
        nowPlaying.set(song)
        val saved = (savePreset("Slow") as SavePresetResult.Saved).preset

        DeletePresetUseCase(repository)(saved)

        assertTrue(ObservePresetsUseCase(repository)("a").first().isEmpty())
    }
}
