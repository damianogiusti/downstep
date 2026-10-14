package com.damianogiusti.slowdowner.data.update

import com.damianogiusti.slowdowner.domain.update.AppRelease
import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.update.UpdateCheckResult
import com.damianogiusti.slowdowner.domain.update.UpdateInstallResult
import com.damianogiusti.slowdowner.domain.update.UpdateRepository
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The latest release lives in memory only: every launch asks again, so a stale answer never
 * outlives the process.
 */
class DefaultUpdateRepository(
    private val dataSource: ReleaseDataSource,
    private val installer: UpdateInstaller,
    private val installedVersion: String?,
    private val downloadDir: File,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val logger: Logger,
) : UpdateRepository {

    private val latest = MutableStateFlow<ReleaseDto?>(null)

    override fun installedVersion(): AppVersion? = installedVersion?.let(AppVersion::parse)

    override fun observeLatestRelease(): Flow<AppRelease?> =
        latest.map { dto -> dto?.let { ReleaseMapper.toDomain(it, installer.assetName) } }

    override suspend fun refresh(): UpdateCheckResult = withContext(dispatcher) {
        try {
            latest.value = dataSource.latest()
            UpdateCheckResult.Done
        } catch (e: IOException) {
            // Offline is normal: nothing to report.
            logger.d("Cannot check for updates: $e")
            UpdateCheckResult.NetworkError
        } catch (e: SerializationException) {
            logger.w("Unexpected answer from the releases API", e)
            UpdateCheckResult.Failure
        }
    }

    override suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): UpdateInstallResult =
        withContext(dispatcher) {
            val dto = latest.value?.takeIf { AppVersion.parse(it.tagName) == release.version }
            val asset = dto?.let { ReleaseMapper.assetFor(it, installer.assetName) }
                ?: return@withContext UpdateInstallResult.Failure
            // Files only, never into folders: an old download goes, nothing else is touched.
            downloadDir.mkdirs()
            downloadDir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
            val file = File(downloadDir, asset.name)
            try {
                dataSource.download(asset.downloadUrl, file) { downloaded, total ->
                    val size = if (total > 0) total else asset.size
                    if (size > 0) onProgress((downloaded.toFloat() / size).coerceIn(0f, 1f))
                }
            } catch (e: IOException) {
                logger.w("Cannot download the update", e)
                return@withContext UpdateInstallResult.NetworkError
            }
            try {
                verify(file, asset)
                installer.prepare(file, release.version.toString())
                UpdateInstallResult.ReadyToRestart
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The app stays as it is; the dialog points to the download page.
                logger.e("Cannot install the update", e)
                file.delete()
                UpdateInstallResult.Failure
            }
        }

    /** A cut download or a swapped file must never replace the app. */
    private fun verify(file: File, asset: ReleaseAssetDto) {
        if (file.length() != asset.size) throw IOException("Download is ${file.length()} bytes, expected ${asset.size}")
        val expected = asset.digest?.takeIf { it.startsWith(SHA256_PREFIX) }?.removePrefix(SHA256_PREFIX) ?: return
        val actual = sha256(file)
        if (!actual.equals(expected, ignoreCase = true)) throw IOException("Download checksum does not match")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SHA256_PREFIX = "sha256:"
    }
}
