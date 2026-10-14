package com.damianogiusti.slowdowner.data.update

import com.damianogiusti.slowdowner.domain.update.AppRelease
import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.update.UpdateCheckResult
import com.damianogiusti.slowdowner.domain.update.UpdateInstallResult
import com.damianogiusti.slowdowner.fake.FakeReleaseDataSource
import com.damianogiusti.slowdowner.fake.FakeUpdateInstaller
import com.damianogiusti.slowdowner.fake.RecordingLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

class DefaultUpdateRepositoryTest {

    private val dataSource = FakeReleaseDataSource()
    private val installer = FakeUpdateInstaller()
    private val logger = RecordingLogger()
    private val downloadDir = Files.createTempDirectory("slowdowner-updates").toFile()
    private val dmg = "new app".toByteArray()

    @After
    fun tearDown() {
        downloadDir.deleteRecursively()
    }

    @Test
    fun `when a release is published then it is the latest, installable here`() = runTest {
        dataSource.latest = release("v1.0.4")

        assertEquals(UpdateCheckResult.Done, repository().refresh())

        assertEquals(AppRelease(version("1.0.4"), PAGE, canInstall = true), repository.observeLatestRelease().first())
    }

    @Test
    fun `when the release has no file for this computer then it cannot be installed`() = runTest {
        dataSource.latest = release("v1.0.4")
        val repository = repository(FakeUpdateInstaller(assetName = "Downstep-linux.tar"))
        repository.refresh()

        assertEquals(false, repository.observeLatestRelease().first()?.canInstall)
    }

    @Test
    fun `when this computer installs by hand then the release cannot be installed`() = runTest {
        dataSource.latest = release("v1.0.4")
        val repository = repository(FakeUpdateInstaller(assetName = null))
        repository.refresh()

        assertEquals(false, repository.observeLatestRelease().first()?.canInstall)
    }

    @Test
    fun `when the tag is not a version then there is no latest release`() = runTest {
        dataSource.latest = release("nightly")

        repository().refresh()

        assertNull(repository.observeLatestRelease().first())
    }

    @Test
    fun `when offline then network error, logged for development only`() = runTest {
        dataSource.latestError = IOException("Offline")

        assertEquals(UpdateCheckResult.NetworkError, repository().refresh())

        assertEquals(listOf('D'), logger.lines.map { it.level })
    }

    @Test
    fun `when the answer is not a release then failure`() = runTest {
        dataSource.latestError = SerializationException("bad json")

        assertEquals(UpdateCheckResult.Failure, repository().refresh())
    }

    @Test
    fun `when installed then the verified download goes to the installer`() = runTest {
        dataSource.latest = release("v1.0.4")
        dataSource.files[DOWNLOAD] = dmg
        repository().refresh()
        val progress = mutableListOf<Float>()

        val result = repository.install(latest()) { progress += it }

        assertEquals(UpdateInstallResult.ReadyToRestart, result)
        assertArrayEquals(dmg, installer.prepared.single().first)
        assertEquals("1.0.4", installer.prepared.single().second)
        assertEquals(1f, progress.last())
    }

    @Test
    fun `when GitHub has no checksum then the size is enough`() = runTest {
        dataSource.latest = release("v1.0.4", digest = null)
        dataSource.files[DOWNLOAD] = dmg
        repository().refresh()

        assertEquals(UpdateInstallResult.ReadyToRestart, repository.install(latest()) {})
    }

    @Test
    fun `when the checksum does not match then nothing is installed`() = runTest {
        dataSource.latest = release("v1.0.4")
        dataSource.files[DOWNLOAD] = "evil app".toByteArray().copyOf(dmg.size)
        repository().refresh()

        assertEquals(UpdateInstallResult.Failure, repository.install(latest()) {})

        assertTrue(installer.prepared.isEmpty())
        assertEquals("Cannot install the update", logger.at('E').single().message)
    }

    @Test
    fun `when the download is cut then nothing is installed`() = runTest {
        dataSource.latest = release("v1.0.4", digest = null)
        dataSource.files[DOWNLOAD] = dmg.copyOf(3)
        repository().refresh()

        assertEquals(UpdateInstallResult.Failure, repository.install(latest()) {})

        assertTrue(installer.prepared.isEmpty())
    }

    @Test
    fun `when the download fails then network error`() = runTest {
        dataSource.latest = release("v1.0.4")
        repository().refresh()

        assertEquals(UpdateInstallResult.NetworkError, repository.install(latest()) {})
    }

    @Test
    fun `when the installer fails then failure, reported as a bug`() = runTest {
        dataSource.latest = release("v1.0.4")
        dataSource.files[DOWNLOAD] = dmg
        installer.error = IllegalStateException("no app in the image")
        repository().refresh()

        assertEquals(UpdateInstallResult.Failure, repository.install(latest()) {})

        assertEquals("no app in the image", logger.at('E').single().throwable?.message)
    }

    @Test
    fun `when the installed version is not a version then it is null`() {
        assertNull(repository(installedVersion = "dev").installedVersion())
        assertEquals(version("1.0.3"), repository(installedVersion = "1.0.3").installedVersion())
    }

    private lateinit var repository: DefaultUpdateRepository

    private fun repository(
        installer: FakeUpdateInstaller = this.installer,
        installedVersion: String = "1.0.3",
    ) = DefaultUpdateRepository(dataSource, installer, installedVersion, downloadDir, Dispatchers.Unconfined, logger)
        .also { repository = it }

    private suspend fun latest() = requireNotNull(repository.observeLatestRelease().first())

    private fun release(tag: String, digest: String? = "sha256:" + sha256(dmg)) = ReleaseDto(
        tagName = tag,
        htmlUrl = PAGE,
        assets = listOf(
            ReleaseAssetDto("Downstep-mac.dmg", DOWNLOAD, dmg.size.toLong(), digest),
            ReleaseAssetDto("Downstep-windows-x64.zip", "https://example.com/win.zip", 10, null),
        ),
    )

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun version(text: String) = requireNotNull(AppVersion.parse(text))

    private companion object {
        const val PAGE = "https://github.com/damianogiusti/downstep/releases/tag/v1.0.4"
        const val DOWNLOAD = "https://github.com/damianogiusti/downstep/releases/download/v1.0.4/Downstep-mac.dmg"
    }
}
