package com.damianogiusti.slowdowner.desktop.update

import com.damianogiusti.slowdowner.data.update.UpdateInstaller
import com.damianogiusti.slowdowner.logging.Logger
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The app downloads its update itself, and that is what keeps macOS and Windows quiet: a browser
 * marks downloaded files (quarantine on macOS, "Mark of the Web" on Windows), and a marked app asks
 * for approval again. A file written by the app carries no mark, so the new version opens like the
 * old one did.
 *
 * The swap happens in a small script after the app quits: a running app cannot replace its own
 * files safely (Windows locks them, macOS caches code signatures by file). The script never deletes
 * the old app: it goes to the Trash or the Recycle Bin, where the user can still get it back.
 * [trashDir] stands in for those in tests.
 *
 * [prepare] only stages and checks; [startSwap] starts the script, once the user has read that the app
 * closes and opens again by itself. The script waits a limited time for the app to quit.
 */
class DesktopUpdateInstaller(
    private val installation: AppInstallation?,
    private val workDir: File,
    private val logger: Logger,
    private val processId: Long = ProcessHandle.current().pid(),
    private val trashDir: File? = null,
    private val architecture: String = System.getProperty("os.arch").orEmpty(),
) : UpdateInstaller {

    // The command of the last prepare(): set on the IO thread, read when the user confirms.
    @Volatile
    private var pendingSwap: List<String>? = null

    override val assetName: String? = when (installation) {
        is AppInstallation.MacBundle -> macAsset(architecture)
        is AppInstallation.WindowsFolder -> WINDOWS_ASSET
        null -> null
    }

    override fun prepare(download: File, version: String) {
        pendingSwap = null
        workDir.mkdirs()
        when (installation) {
            is AppInstallation.MacBundle -> prepareMac(installation.bundle, download, version)
            is AppInstallation.WindowsFolder -> prepareWindows(installation.folder, download, version)
            null -> error("This app cannot replace itself")
        }
    }

    private fun prepareMac(bundle: File, dmg: File, version: String) {
        val staged = stagedCopy(AppInstallation.MacBundle(bundle), workDir)
        staged.deleteTree()
        DiskImage.copyApp(dmg, staged, logger)
        checkApp(File(staged, "Contents/app"), version)
        // Checks the whole bundle against its signature: a damaged copy would not open.
        run("codesign", "--verify", "--deep", "--strict", staged.path)
        val script = script("replace-app.sh")
        pendingSwap = listOf("/bin/sh", script.path, processId.toString(), staged.path, bundle.path) + listOfNotNull(trashDir?.path)
    }

    private fun prepareWindows(folder: File, zip: File, version: String) {
        val staged = stagedCopy(AppInstallation.WindowsFolder(folder), workDir)
        staged.deleteTree()
        // The zip has a Downstep folder, whatever name the user gave to theirs.
        ZipArchive.extractFolder(zip, APP_NAME, staged)
        checkApp(File(staged, "app"), version)
        if (!File(staged, "$APP_NAME.exe").isFile) throw IOException("No $APP_NAME.exe in the update")
        val script = script("replace-app.ps1")
        pendingSwap = listOf(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
            "-File", script.path, "-ProcessId", processId.toString(), "-NewApp", staged.path, "-Target", folder.path,
        ) + (trashDir?.let { listOf("-TrashDir", it.path) } ?: emptyList())
    }

    override fun startSwap() {
        val process = start(pendingSwap ?: error("No update prepared"))
        pendingSwap = null
        // The app quits right after this: on Windows the lock must already be held by then, or a copy
        // opened at once would not see it. A slow PowerShell gets a few seconds; past them, it goes on.
        if (installation is AppInstallation.WindowsFolder) waitForLock(process)
    }

    private fun waitForLock(process: Process) {
        repeat(LOCK_WAIT_STEPS) {
            if (UpdateSwapFiles.isSwapRunning(workDir)) return
            if (!process.isAlive) throw IOException("The swap script ended (${process.exitValue()})")
            Thread.sleep(LOCK_WAIT_STEP_MS)
        }
    }

    /** The launcher settings name our main class and the expected version: the right app, the right release. */
    private fun checkApp(appDir: File, version: String) {
        val config = appDir.listFiles { file -> file.extension == "cfg" }?.firstOrNull()
            ?: throw IOException("No launcher settings in the update")
        val lines = config.readLines()
        if ("app.mainclass=$MAIN_CLASS" !in lines) throw IOException("The update is not Downstep")
        if (lines.none { it == "java-options=-D$VERSION_PROPERTY=$version" }) throw IOException("The update is not version $version")
    }

    private fun script(name: String): File {
        val target = File(workDir, name)
        val source = javaClass.getResourceAsStream("/update/$name") ?: error("Missing script $name")
        source.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    /**
     * Runs on after the app quits. Its output goes to a log next to it. It starts in [workDir]: started
     * in the app's folder, as it inherited from an app opened in Explorer, Windows would refuse to
     * rename that folder. A script that ends at once (blocked by a policy or an antivirus) throws:
     * the app must not quit for an update that will not come.
     */
    private fun start(command: List<String>): Process {
        val log = File(workDir, "replace-app.log")
        val process = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
            .start()
        // A working script is still waiting for the app to quit. Its output stays in the log: it names
        // the user's folders, which never go to the crash reports.
        if (process.waitFor(SCRIPT_ALIVE_CHECK_MS, TimeUnit.MILLISECONDS)) {
            throw IOException("The swap script ended at once (${process.exitValue()})")
        }
        return process
    }

    private fun nullDevice() = File(if (File.separatorChar == '\\') "NUL" else "/dev/null")

    companion object {
        const val APP_NAME = "Downstep"
        const val MAC_ASSET = "Downstep-mac.dmg"
        const val MAC_INTEL_ASSET = "Downstep-mac-intel.dmg"
        const val WINDOWS_ASSET = "Downstep-windows-x64.zip"
        const val VERSION_PROPERTY = "slowdowner.version"
        private const val MAIN_CLASS = "com.damianogiusti.slowdowner.desktop.MainKt"
        private const val SCRIPT_ALIVE_CHECK_MS = 1_000L
        private const val LOCK_WAIT_STEPS = 25
        private const val LOCK_WAIT_STEP_MS = 200L

        /**
         * An unknown architecture gets no file: the update is offered as a link to the release page, and
         * the same goes for a release with no file of this name (older releases have no Intel DMG).
         */
        internal fun macAsset(architecture: String): String? = when (architecture.lowercase()) {
            "aarch64", "arm64" -> MAC_ASSET
            "x86_64", "amd64" -> MAC_INTEL_ASSET
            else -> null
        }

        /** Where [prepare] puts the new app. Next to the installed folder on Windows: on the same disk, the swap is two renames. */
        internal fun stagedCopy(installation: AppInstallation, workDir: File): File = when (installation) {
            is AppInstallation.MacBundle -> File(workDir, installation.bundle.name)
            is AppInstallation.WindowsFolder -> File(installation.folder.parentFile, installation.folder.name + ".update")
        }

        /**
         * A copy prepared and never swapped in ("Later", then a normal quit) would stay forever next to
         * the user's folder, and run if someone opened it. Windows only, where the update lock rules out
         * a swap running now (call it after that check), and only an hour after it was prepared, so it
         * is never the copy a swap is moving. On macOS it stays in the cache until the next update.
         */
        fun discardAbandoned(installation: AppInstallation?, workDir: File, nowMillis: Long = System.currentTimeMillis()) {
            if (installation !is AppInstallation.WindowsFolder) return
            val staged = stagedCopy(installation, workDir)
            val parent = staged.parentFile ?: return
            if (staged.isDirectory && nowMillis - staged.lastModified() >= ABANDONED_AFTER_MS) {
                // Renamed first, in one step: an Install started meanwhile prepares a fresh copy under the
                // old name, and this never deletes a file of it.
                staged.renameTo(File(parent, staged.name + DISCARDED + nowMillis))
            }
            parent.listFiles { file -> file.name.startsWith(staged.name + DISCARDED) }
                ?.forEach { runCatching { it.deleteTree() } }
        }

        private const val DISCARDED = ".discarded-"

        private const val ABANDONED_AFTER_MS = 60 * 60 * 1000L

        /** Runs a command to the end; throws with its output if it fails or hangs. */
        internal fun run(vararg command: String): String {
            // Output to a file, not a pipe: reading a pipe would wait for the end and skip the timeout.
            val output = File.createTempFile("downstep-command", ".txt")
            try {
                val process = ProcessBuilder(*command).redirectErrorStream(true).redirectOutput(output).start()
                if (!process.waitFor(COMMAND_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    process.destroyForcibly()
                    throw IOException("${command.first()} did not finish")
                }
                val text = output.readText()
                if (process.exitValue() != 0) throw IOException("${command.first()} failed (${process.exitValue()}): ${text.trim()}")
                return text
            } finally {
                output.delete()
            }
        }

        private const val COMMAND_TIMEOUT_MINUTES = 5L
    }
}
