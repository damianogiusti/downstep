package com.damianogiusti.slowdowner.data.stems

/** A separated track as the store keeps it: names are the stored text, the mapper reads them. */
data class TrackStemsDto(
    val trackId: String,
    val model: String,
    val mode: String,
    val stems: List<StemFileDto>,
)

/** [fileName] is a file inside the track's stem folder ([StemsStore.dirFor]). */
data class StemFileDto(
    val stem: String,
    val fileName: String,
    val gain: Float,
    val isMuted: Boolean = false,
    val isSoloed: Boolean = false,
)
