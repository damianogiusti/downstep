package com.damianogiusti.slowdowner.domain.source

interface SpotifyEngine {
    fun selectTrack(uri: String)
    fun destroy()
    fun isAvailable(): Boolean
    fun setListener(listener: Listener?)

    /** Called on the main thread. */
    interface Listener {
        /** The track is buffered and can be opened in the playback engine. */
        fun onTrackReady(uri: String)
        fun onError(message: String)
    }
}
