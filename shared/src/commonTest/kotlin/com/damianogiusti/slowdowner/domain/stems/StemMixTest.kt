package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.fake.FakeStemMixer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StemMixTest {

    private val levels = mapOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 0.8f, StemKind.BASS to 1f)

    @Test
    fun `with no mute and no solo every stem plays at its level`() {
        val mix = StemMix(levels)

        assertEquals(levels, mix.effectiveGains)
    }

    @Test
    fun `a muted stem is silent and the others play`() {
        val mix = StemMix(levels, mutedStems = setOf(StemKind.DRUMS))

        assertEquals(0f, mix.effectiveGain(StemKind.DRUMS))
        assertEquals(0.5f, mix.effectiveGain(StemKind.VOCALS))
        assertEquals(1f, mix.effectiveGain(StemKind.BASS))
    }

    @Test
    fun `when a stem is soloed only that stem plays`() {
        val mix = StemMix(levels, soloedStems = setOf(StemKind.DRUMS))

        assertEquals(mapOf(StemKind.VOCALS to 0f, StemKind.DRUMS to 0.8f, StemKind.BASS to 0f), mix.effectiveGains)
    }

    @Test
    fun `when several stems are soloed then all of them play`() {
        val mix = StemMix(levels, soloedStems = setOf(StemKind.VOCALS, StemKind.BASS))

        assertEquals(mapOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 0f, StemKind.BASS to 1f), mix.effectiveGains)
    }

    @Test
    fun `a muted soloed stem is silent and the others stay silenced`() {
        val mix = StemMix(levels, mutedStems = setOf(StemKind.VOCALS), soloedStems = setOf(StemKind.VOCALS))

        assertEquals(mapOf(StemKind.VOCALS to 0f, StemKind.DRUMS to 0f, StemKind.BASS to 0f), mix.effectiveGains)
    }

    @Test
    fun `the level of a muted stem is kept`() {
        val mix = StemMix(levels).withMuted(StemKind.DRUMS, true)

        assertEquals(0.8f, mix.gains[StemKind.DRUMS])
        assertEquals(0.8f, mix.withMuted(StemKind.DRUMS, false).effectiveGain(StemKind.DRUMS))
    }

    @Test
    fun `a solo flag of a stem the track does not have silences nobody`() {
        val mix = StemMix(levels, soloedStems = setOf(StemKind.PIANO))

        assertEquals(levels, mix.effectiveGains)
        assertFalse(mix.isSilencedBySolo(StemKind.VOCALS))
    }

    @Test
    fun `a stem that is not there has no gain`() {
        assertEquals(0f, StemMix(levels).effectiveGain(StemKind.PIANO))
    }

    @Test
    fun `a stem silenced by a solo is told apart from a muted one`() {
        val mix = StemMix(levels, mutedStems = setOf(StemKind.BASS), soloedStems = setOf(StemKind.VOCALS))

        assertTrue(mix.isSilencedBySolo(StemKind.DRUMS))
        assertFalse(mix.isSilencedBySolo(StemKind.BASS))
        assertFalse(mix.isSilencedBySolo(StemKind.VOCALS))
    }

    @Test
    fun `the effective gains come in the order of the stems`() {
        val mix = StemMix(mapOf(StemKind.BASS to 1f, StemKind.VOCALS to 0.5f))

        assertEquals(listOf(StemKind.VOCALS, StemKind.BASS), mix.effectiveGains.keys.toList())
    }

    @Test
    fun `applying a mix sets the effective gain of every stem`() {
        val mixer = FakeStemMixer()

        mixer.applyMix(StemMix(levels, soloedStems = setOf(StemKind.BASS)))

        assertEquals(listOf(StemKind.VOCALS to 0f, StemKind.DRUMS to 0f, StemKind.BASS to 1f), mixer.calls)
    }

    @Test
    fun `playable stems carry their flags into the mix`() {
        val playable = PlayableStems(
            TrackMode.STEMS,
            listOf(
                PlayableStem(StemKind.VOCALS, "/v", 0.5f, isMuted = true),
                PlayableStem(StemKind.DRUMS, "/d", 0.8f, isSoloed = true),
            ),
        )

        assertEquals(
            StemMix(
                mapOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 0.8f),
                mutedStems = setOf(StemKind.VOCALS),
                soloedStems = setOf(StemKind.DRUMS),
            ),
            playable.mix,
        )
    }
}
