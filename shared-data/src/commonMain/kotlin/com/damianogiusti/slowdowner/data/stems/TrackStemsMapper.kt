package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemMix
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.stems.TrackStems
import com.damianogiusti.slowdowner.logging.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stored text to domain. A name this version does not know (a database written by a newer one)
 * must not crash: an unknown stem is skipped, an unknown mode makes the track count as not
 * separated. Logged once per mapper, with no value in it.
 */
class TrackStemsMapper(private val logger: Logger) {

    private val hasLoggedUnknown = AtomicBoolean(false)

    fun toDomain(dto: TrackStemsDto): TrackStems? {
        val mode = dto.mode.toEnumOrNull<TrackMode>() ?: return unknown()
        val gains = dto.stems.mapNotNull { stem ->
            stem.stem.toEnumOrNull<StemKind>()?.let { it to stem.gain } ?: unknown()
        }
        return TrackStems(dto.trackId, dto.model, mode, StemMix(gains.toMap()))
    }

    private inline fun <reified E : Enum<E>> String.toEnumOrNull(): E? = enumValues<E>().firstOrNull { it.name == this }

    private fun unknown(): Nothing? {
        if (hasLoggedUnknown.compareAndSet(false, true)) logger.w("Stem data has a value this version does not know")
        return null
    }
}
