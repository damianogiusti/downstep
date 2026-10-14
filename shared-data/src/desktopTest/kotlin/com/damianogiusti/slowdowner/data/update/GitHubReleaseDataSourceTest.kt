package com.damianogiusti.slowdowner.data.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files

/** Against a local server that answers like api.github.com. */
class GitHubReleaseDataSourceTest {

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val dataSource = GitHubReleaseDataSource(OkHttpClient(), "$base/latest")

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `when a release is published then its tag, page and files are read`() {
        answer("/latest", 200, RELEASE_JSON.toByteArray())

        val release = dataSource.latest()

        assertEquals("v1.0.4", release?.tagName)
        assertEquals("https://github.com/damianogiusti/downstep/releases/tag/v1.0.4", release?.htmlUrl)
        assertEquals(
            ReleaseAssetDto(
                "Downstep-mac.dmg",
                "https://github.com/damianogiusti/downstep/releases/download/v1.0.4/Downstep-mac.dmg",
                144103194,
                "sha256:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae",
            ),
            release?.assets?.first(),
        )
        assertNull(release?.assets?.last()?.digest)
    }

    @Test
    fun `when no release is published then latest is null`() {
        answer("/latest", 404, """{"message":"Not Found"}""".toByteArray())

        assertNull(dataSource.latest())
    }

    @Test(expected = IOException::class)
    fun `when GitHub refuses then it is an IOException`() {
        // The rate limit answers 403.
        answer("/latest", 403, """{"message":"API rate limit exceeded"}""".toByteArray())

        dataSource.latest()
    }

    @Test
    fun `when downloading then the file is written whole, after a redirect`() = runBlocking {
        val bytes = ByteArray(300_000) { it.toByte() }
        // GitHub sends release files from another host, through a redirect.
        server.createContext("/download") { exchange ->
            exchange.responseHeaders.add("Location", "$base/storage")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        answer("/storage", 200, bytes)
        val target = Files.createTempFile("update", ".dmg").toFile()
        var lastProgress = 0L to 0L

        dataSource.download("$base/download", target) { downloaded, total -> lastProgress = downloaded to total }

        assertArrayEquals(bytes, target.readBytes())
        assertEquals(300_000L to 300_000L, lastProgress)
        target.delete()
        Unit
    }

    private fun answer(path: String, status: Int, body: ByteArray) {
        server.createContext(path) { exchange ->
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private companion object {
        // Trimmed from a real answer of /repos/{owner}/{repo}/releases/latest.
        val RELEASE_JSON = """
            {
              "url": "https://api.github.com/repos/damianogiusti/downstep/releases/1",
              "html_url": "https://github.com/damianogiusti/downstep/releases/tag/v1.0.4",
              "id": 1,
              "tag_name": "v1.0.4",
              "name": "Downstep 1.0.4",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "id": 10,
                  "name": "Downstep-mac.dmg",
                  "content_type": "application/x-apple-diskimage",
                  "state": "uploaded",
                  "size": 144103194,
                  "digest": "sha256:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae",
                  "browser_download_url": "https://github.com/damianogiusti/downstep/releases/download/v1.0.4/Downstep-mac.dmg"
                },
                {
                  "id": 11,
                  "name": "Downstep-windows-x64.zip",
                  "size": 90000000,
                  "digest": null,
                  "browser_download_url": "https://github.com/damianogiusti/downstep/releases/download/v1.0.4/Downstep-windows-x64.zip"
                }
              ],
              "body": "For Mac with Apple silicon"
            }
        """.trimIndent()
    }
}
