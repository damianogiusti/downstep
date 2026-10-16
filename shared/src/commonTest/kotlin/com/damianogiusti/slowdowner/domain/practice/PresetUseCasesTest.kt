package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeLoopRepository
import com.damianogiusti.slowdowner.fake.FakePracticeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test

class PresetUseCasesTest {

    private val engine = FakeEngine()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val repository = FakePracticeRepository()
    private val savePreset = SavePresetUseCase(engine, nowPlaying, repository)
    private val song = LibraryTrack("a", "A", "/a.m4a", null, 0)
    private val loops = FakeLoopRepository()
    private val applyPreset = ApplyPresetUseCase(engine, loops, nowPlaying)

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
    fun `when a preset is applied then the engine gets its speed and pitch and keeps the active loop`() = runTest {
        nowPlaying.set(song)
        activeLoop(LoopBounds(0.3f, 0.5f))

        applyPreset(preset(LoopRegion(0.2f, 0.4f, isOn = true)))

        assertEquals(
            listOf("speed:75.0", "pitch:-2.0", "loop:${LoopRegion(0.3f, 0.5f, isOn = true)}"),
            engine.practiceCalls,
        )
    }

    @Test
    fun `when a preset loops but the track has no active loop then nothing loops`() = runTest {
        nowPlaying.set(song)

        applyPreset(preset(LoopRegion(0.2f, 0.4f, isOn = true)))

        assertEquals(LoopRegion.WHOLE_FILE, LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
    }

    @Test
    fun `when a preset has the loop off then the active loop is kept but off`() = runTest {
        nowPlaying.set(song)
        activeLoop(LoopBounds(0.3f, 0.5f))

        applyPreset(preset(LoopRegion(0.2f, 0.4f, isOn = false)))

        assertEquals(LoopRegion(0.3f, 0.5f, isOn = false), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
    }

    private fun preset(loop: LoopRegion) = PracticePreset(1, "a", "Solo", PracticeSettings(75f, -2f, loop))

    private suspend fun activeLoop(bounds: LoopBounds) {
        loops.setActiveLoop(song.id, assertNotNull(loops.create(song.id, "Solo", bounds)).id)
    }

    @Test
    fun `when a preset is deleted then it is no longer observed`() = runTest {
        nowPlaying.set(song)
        val saved = (savePreset("Slow") as SavePresetResult.Saved).preset

        DeletePresetUseCase(repository)(saved)

        assertTrue(ObservePresetsUseCase(repository)("a").first().isEmpty())
    }
}
