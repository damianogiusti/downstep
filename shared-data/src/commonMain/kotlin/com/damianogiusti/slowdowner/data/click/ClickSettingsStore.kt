package com.damianogiusti.slowdowner.data.click

/** The click settings as stored: each value null when it was never saved. */
data class ClickSettingsDto(
    val metronomeEnabled: Boolean? = null,
    val volume: Float? = null,
    val countInEnabled: Boolean? = null,
    val countInBeats: Int? = null,
    val countInEveryLoop: Boolean? = null,
)

/** Local-only key-value storage of the metronome and count-in settings. Blocking; callers pick the thread. */
interface ClickSettingsStore {
    fun read(): ClickSettingsDto
    fun write(settings: ClickSettingsDto)
}
