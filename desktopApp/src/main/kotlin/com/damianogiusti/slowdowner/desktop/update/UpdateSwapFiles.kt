package com.damianogiusti.slowdowner.desktop.update

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.time.Duration
import java.time.Instant

/**
 * What the swap scripts (replace-app.sh, replace-app.ps1) leave next to them in the update folder.
 *
 * - `update.lock`: held by the Windows script while it moves the app. A copy opened by hand meanwhile
 *   would lock those files and make the update fail, so it quits at once instead. The rule is the one
 *   of SingleInstance: only a lock that is really held, and recent, keeps the app from starting.
 * - `swap-result`: one word for how the swap went, reported once by the next start.
 */
object UpdateSwapFiles {
    const val LOCK = "update.lock"
    const val RESULT = "swap-result"

    // Every step of the script has its own timeout and they add up to well under this.
    private val MAX_LOCK_AGE: Duration = Duration.ofMinutes(5)

    private val OUTCOMES = setOf(
        "replaced", "app-did-not-quit", "old-copy-stuck", "installed-app-in-use", "new-app-not-moved",
    )

    /** True only if another process holds the lock, taken less than five minutes ago. Any doubt: false. */
    fun isSwapRunning(dir: File, now: Instant = Instant.now()): Boolean = try {
        val file = File(dir, LOCK)
        if (!file.isFile) {
            false
        } else {
            val age = Duration.between(Files.getLastModifiedTime(file.toPath()).toInstant(), now)
            // A date in the future is as unreliable as an old one.
            !age.isNegative && age <= MAX_LOCK_AGE && isLockedByOthers(file)
        }
    } catch (_: Exception) {
        false
    }

    /** The word the last swap left, once: the file goes after reading. Null if there was no swap. */
    fun takeResult(dir: File): String? {
        val file = File(dir, RESULT)
        if (!file.isFile) return null
        val word = runCatching { file.readText().trim() }.getOrNull()
        file.delete()
        return word?.takeIf { it in OUTCOMES } ?: "unknown"
    }

    private fun isLockedByOthers(file: File): Boolean =
        FileChannel.open(file.toPath(), READ, WRITE).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                // The lock is per process: held in this JVM (tests) it throws instead of returning null.
                return true
            }
            if (lock == null) {
                true
            } else {
                lock.release()
                false
            }
        }
}
