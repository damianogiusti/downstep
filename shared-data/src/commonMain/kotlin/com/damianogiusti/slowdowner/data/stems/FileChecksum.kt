package com.damianogiusti.slowdowner.data.stems

import java.io.File
import java.security.MessageDigest

/** SHA-256 of a file, streamed: model files are tens of megabytes. */
class FileChecksum {

    /** Lower-case hexadecimal. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
