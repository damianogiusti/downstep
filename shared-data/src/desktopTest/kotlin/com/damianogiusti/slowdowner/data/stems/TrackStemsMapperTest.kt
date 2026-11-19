package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemMix
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.stems.TrackStems
import com.damianogiusti.slowdowner.fake.RecordingLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackStemsMapperTest {

    private val logger = RecordingLogger()
    private val mapper = TrackStemsMapper(logger)

    @Test
    fun `when names are known then the dto maps to the domain`() {
        val dto = TrackStemsDto(
            "a", "model", "FULL_TRACK",
            listOf(StemFileDto("VOCALS", "v.wav", 0.25f), StemFileDto("PIANO", "p.wav", 1f)),
        )

        assertEquals(
            TrackStems("a", "model", TrackMode.FULL_TRACK, StemMix(mapOf(StemKind.VOCALS to 0.25f, StemKind.PIANO to 1f))),
            mapper.toDomain(dto),
        )
        assertEquals(emptyList<RecordingLogger.Line>(), logger.lines)
    }

    @Test
    fun `when stems are muted or soloed then the mix has the flags and the levels`() {
        val dto = TrackStemsDto(
            "a", "model", "STEMS",
            listOf(
                StemFileDto("VOCALS", "v.wav", 0.25f, isMuted = true),
                StemFileDto("DRUMS", "d.wav", 0.5f, isSoloed = true),
                StemFileDto("BASS", "b.wav", 1f),
            ),
        )

        assertEquals(
            StemMix(
                mapOf(StemKind.VOCALS to 0.25f, StemKind.DRUMS to 0.5f, StemKind.BASS to 1f),
                mutedStems = setOf(StemKind.VOCALS),
                soloedStems = setOf(StemKind.DRUMS),
            ),
            mapper.toDomain(dto)?.mix,
        )
    }

    @Test
    fun `when a flagged stem name is unknown then its flags are dropped with it`() {
        val dto = TrackStemsDto("a", "model", "STEMS", listOf(StemFileDto("KAZOO", "k.wav", 1f, isSoloed = true), StemFileDto("BASS", "b.wav", 0.5f)))

        assertEquals(StemMix(mapOf(StemKind.BASS to 0.5f)), mapper.toDomain(dto)?.mix)
    }

    @Test
    fun `when a stem name is unknown then that stem is skipped and logged`() {
        val dto = TrackStemsDto("a", "model", "STEMS", listOf(StemFileDto("KAZOO", "k.wav", 1f), StemFileDto("BASS", "b.wav", 0.5f)))

        assertEquals(mapOf(StemKind.BASS to 0.5f), mapper.toDomain(dto)?.mix?.gains)
        assertEquals(1, logger.at('W').size)
    }

    @Test
    fun `when the mode is unknown then the track counts as not separated`() {
        assertNull(mapper.toDomain(TrackStemsDto("a", "model", "HOLOGRAM", listOf(StemFileDto("BASS", "b.wav", 1f)))))
        assertEquals(1, logger.at('W').size)
    }

    @Test
    fun `when several values are unknown then it logs once and without the value`() {
        val dto = TrackStemsDto("a", "model", "STEMS", listOf(StemFileDto("KAZOO", "k", 1f), StemFileDto("OBOE", "o", 1f)))

        mapper.toDomain(dto)
        mapper.toDomain(dto)

        assertEquals(1, logger.lines.size)
        assertEquals(emptyMap<String, String>(), logger.lines.single().properties)
    }
}
