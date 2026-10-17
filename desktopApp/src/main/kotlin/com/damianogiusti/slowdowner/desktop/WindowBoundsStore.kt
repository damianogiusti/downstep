package com.damianogiusti.slowdowner.desktop

import java.util.prefs.Preferences

/** The window's last bounds in the user's Java preferences, next to the theme and effects settings. */
class WindowBoundsStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/damianogiusti/slowdowner/window"),
) {

    /** Null before the first run, or when a value is missing or unreadable. */
    fun read(): WindowBounds? {
        val width = preferences.getInt(KEY_WIDTH, 0)
        val height = preferences.getInt(KEY_HEIGHT, 0)
        if (width <= 0 || height <= 0 || preferences.get(KEY_X, null) == null || preferences.get(KEY_Y, null) == null) return null
        return WindowBounds(
            x = preferences.getInt(KEY_X, 0),
            y = preferences.getInt(KEY_Y, 0),
            width = width,
            height = height,
            isMaximized = preferences.getBoolean(KEY_MAXIMIZED, false),
        )
    }

    fun write(bounds: WindowBounds) {
        preferences.putInt(KEY_X, bounds.x)
        preferences.putInt(KEY_Y, bounds.y)
        preferences.putInt(KEY_WIDTH, bounds.width)
        preferences.putInt(KEY_HEIGHT, bounds.height)
        preferences.putBoolean(KEY_MAXIMIZED, bounds.isMaximized)
        preferences.flush()
    }

    private companion object {
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_MAXIMIZED = "maximized"
    }
}
