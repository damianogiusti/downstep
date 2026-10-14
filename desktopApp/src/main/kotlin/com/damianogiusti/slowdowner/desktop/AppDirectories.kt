package com.damianogiusti.slowdowner.desktop

import java.io.File

/** Where the desktop app keeps its data, as Android keeps it in filesDir and cacheDir. */
class AppDirectories(val root: File = defaultRoot()) {
    val database: File get() = File(root.apply { mkdirs() }, "slowdowner.db")
    val library: File get() = File(root, "library")
    val cache: File get() = File(root, "cache")
    val logs: File get() = File(root, "logs")

    private companion object {
        /** The system's place for app data: never next to the app, which an update replaces. */
        fun defaultRoot(): File {
            val os = System.getProperty("os.name").orEmpty()
            val home = System.getProperty("user.home")
            return when {
                // The folder keeps the app's first name (SlowDowner), so existing libraries stay where they are.
                os.startsWith("Mac") -> File(home, "Library/Application Support/SlowDowner")
                // Roaming, as most apps: %APPDATA%\SlowDowner.
                os.startsWith("Windows") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "SlowDowner")
                else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "SlowDowner")
            }
        }
    }
}
