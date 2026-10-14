package com.damianogiusti.slowdowner.data.library

import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

class OkHttpFileDataSource(client: OkHttpClient) : HttpFileDataSource {

    // Redirects are followed by hand: OkHttp would follow an http:// Location and then fail on
    // the cleartext policy, before any interceptor could upgrade it.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    /**
     * Downloads in ranged chunks: YouTube throttles long single requests on
     * progressive streams, short ranges keep full speed.
     */
    override suspend fun download(
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val partial = File(target.path + ".part")
        try {
            var downloaded = 0L
            var total: Long? = null
            partial.outputStream().use { out ->
                while (total == null || downloaded < total!!) {
                    coroutineContext.ensureActive()
                    requestChunk(url, from = downloaded).use { response ->
                        total = total ?: totalSize(response)
                        downloaded = copyBody(response, out, downloaded) { onProgress(it, total!!) }
                        // Server ignored the Range header and sent the whole file.
                        if (response.code == HTTP_OK) total = downloaded
                    }
                }
            }
            if (!partial.renameTo(target)) throw IOException("Cannot move download to $target")
        } finally {
            partial.delete()
        }
    }

    private fun requestChunk(url: String, from: Long): Response {
        var target = secureUrl(url)
        repeat(MAX_REDIRECTS + 1) {
            val request = Request.Builder()
                .url(target)
                .header("User-Agent", NewPipeDownloaderAdapter.USER_AGENT)
                .header("Range", "bytes=$from-${from + CHUNK_SIZE - 1}")
                .build()
            val response = client.newCall(request).execute()
            if (response.isRedirect) {
                val location = response.header("Location")?.let { response.request.url.resolve(it) }
                response.close()
                target = secureUrl(location?.toString() ?: throw IOException("Redirect without a location"))
                return@repeat
            }
            if (!response.isSuccessful) {
                response.close()
                throw HttpStatusException(response.code)
            }
            return response
        }
        throw IOException("Too many redirects while downloading")
    }

    private fun totalSize(response: Response): Long =
        response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            ?: response.body?.contentLength()?.takeIf { it > 0 }
            ?: throw IOException("Unknown content length")

    private fun copyBody(response: Response, out: OutputStream, start: Long, onProgress: (Long) -> Unit): Long {
        var downloaded = start
        val input = response.body?.byteStream() ?: throw IOException("Empty body")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            downloaded += read
            onProgress(downloaded)
        }
        return downloaded
    }

    companion object {
        /**
         * YouTube sometimes hands out, or redirects to, http:// stream URLs. Android blocks
         * cleartext traffic, and googlevideo serves the same files over https.
         */
        fun secureUrl(url: String): String =
            if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring("http://".length) else url

        private const val MAX_REDIRECTS = 5
        private const val CHUNK_SIZE = 10L * 1024 * 1024
        private const val BUFFER_SIZE = 64 * 1024
        private const val HTTP_OK = 200
    }
}
