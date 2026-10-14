package com.damianogiusti.slowdowner.data.update

import com.damianogiusti.slowdowner.data.library.HttpStatusException
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * The releases of the public repository. No token: GitHub allows 60 requests per hour from one
 * address, and the app asks once per launch.
 */
class GitHubReleaseDataSource(
    private val client: OkHttpClient,
    private val latestUrl: String = LATEST_URL,
) : ReleaseDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override fun latest(): ReleaseDto? {
        val request = Request.Builder()
            .url(latestUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            // Drafts do not count: until a release is published, there is no latest one.
            if (response.code == HTTP_NOT_FOUND) return null
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body?.string() ?: throw IOException("Empty body")
            return json.decodeFromString(ReleaseDto.serializer(), body)
        }
    }

    override suspend fun download(url: String, target: File, onProgress: (downloaded: Long, total: Long) -> Unit) {
        val partial = File(target.path + ".part")
        try {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                val body = response.body ?: throw IOException("Empty body")
                val total = body.contentLength()
                var downloaded = 0L
                partial.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            target.delete()
            if (!partial.renameTo(target)) throw IOException("Cannot move the download to $target")
        } finally {
            partial.delete()
        }
    }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/damianogiusti/downstep/releases/latest"
        private const val USER_AGENT = "Downstep"
        private const val HTTP_NOT_FOUND = 404
        private const val BUFFER_SIZE = 64 * 1024
    }
}
