import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("io.sentry.jvm.gradle")
}

kotlin {
    // The engine binding uses FFM (java.lang.foreign), final from Java 22.
    jvmToolchain(23)
}

dependencies {
    implementation(project(":shared-ui"))
    implementation(project(":shared-data"))
    implementation(project(":engine-desktop"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    // Dispatchers.Main on the Swing thread, for viewModelScope.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    // The window's back (Esc), which menus and dialogs close on: fullscreen is left from there, last.
    // The version Compose already brings at runtime.
    implementation("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0")
    implementation(platform("io.insert-koin:koin-bom:4.1.1"))
    implementation("io.insert-koin:koin-core")
    implementation("io.insert-koin:koin-compose-viewmodel")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("io.sentry:sentry:8.61.0")

    testImplementation(kotlin("test"))
    testImplementation("io.insert-koin:koin-test")
    testImplementation(compose.desktop.uiTestJUnit4)
}

val osName: String = System.getProperty("os.name")
val isWindows = osName.startsWith("Windows")
val isLinux = osName.startsWith("Linux")
val engineBuildDir = rootProject.layout.projectDirectory.dir("engine-desktop/build/native")

tasks.test {
    dependsOn(":engine-desktop:buildNativeEngine")
    systemProperty("slowdowner.engine.library", engineBuildDir.file(System.mapLibraryName("sdengine")).asFile.path)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // ./gradlew :desktopApp:test -PnetworkTests=true runs the YouTube import too.
    systemProperty("slowdowner.networkTests", providers.gradleProperty("networkTests").getOrElse("false"))
    // The real app swap (scripts, disk images, Trash) runs only in CI, on machines that are thrown away:
    // ./gradlew :desktopApp:test -PupdateSwapTests=true. Never on a developer's computer.
    systemProperty("slowdowner.updateSwapTests", providers.gradleProperty("updateSwapTests").getOrElse("false"))
    enabled = osName.startsWith("Mac") || isWindows || isLinux
}

// The C engine travels inside the app, in Compose's per-OS resources folder. On Windows LAME is
// a DLL of its own (LGPL), next to the engine. Compose names the Linux folder after the CPU.
val appResources = layout.buildDirectory.dir("appResources")
val syncEngineLibrary = tasks.register<Sync>("syncEngineLibrary") {
    dependsOn(":engine-desktop:buildNativeEngine")
    if (isWindows) {
        from(engineBuildDir.file("sdengine.dll"), engineBuildDir.file("mp3lame.dll"))
        into(appResources.map { it.dir("windows-x64") })
    } else if (isLinux) {
        val arch = if (System.getProperty("os.arch") == "aarch64") "arm64" else "x64"
        from(engineBuildDir.file("libsdengine.so"))
        into(appResources.map { it.dir("linux-$arch") })
    } else {
        from(engineBuildDir.file("libsdengine.dylib"))
        into(appResources.map { it.dir("macos-arm64") })
    }
}
// The licenses travel inside the app on every OS (Compose's "common" resources): the DMG has no
// other place for them. The font license stays beside the fonts in shared-ui.
val syncLicenses = tasks.register<Sync>("syncLicenses") {
    from(layout.projectDirectory.dir("licenses"), rootProject.layout.projectDirectory.file("shared-ui/FONTS-OFL.txt"))
    into(appResources.map { it.dir("common/licenses") })
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(syncEngineLibrary, syncLicenses) }

// CI passes -PappVersion=1.0.<run number>: the band reports it from the window title or the log.
val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")
// Only the release workflow has it: see CrashReporting. A local try sets SENTRY_ENVIRONMENT=development,
// so its events stay apart from the users' ones.
val sentryDsn: String? = providers.environmentVariable("SENTRY_DSN").orNull?.takeIf { it.isNotBlank() }
val sentryEnvironment: String = providers.environmentVariable("SENTRY_ENVIRONMENT").orNull?.takeIf { it.isNotBlank() }
    ?: "production"

compose.desktop {
    application {
        mainClass = "com.damianogiusti.slowdowner.desktop.MainKt"
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(23)) }
            .get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Dslowdowner.version=$appVersion")
        if (sentryDsn != null) {
            jvmArgs += listOf("-Dslowdowner.sentryDsn=$sentryDsn", "-Dslowdowner.sentryEnvironment=$sentryEnvironment")
        }

        nativeDistributions {
            // Each OS packages only its own formats: no .deb on macOS, no DMG on Linux.
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Downstep"
            packageVersion = appVersion
            description = "Slow down and loop audio to practise"
            appResourcesRootDir.set(appResources)
            // Simpler than listing modules by hand; the bundle is bigger, which is fine for personal use.
            includeAllModules = true
            macOS {
                bundleID = "com.damianogiusti.slowdowner.desktop"
                // Same floor as the engine (engine/CMakeLists.txt) and the bundled Java runtime.
                minimumSystemVersion = "11.0"
                // Built from icons/SlowDowner.svg with icons/make-icns.sh.
                iconFile.set(project.file("icons/SlowDowner.icns"))
            }
            windows {
                // Built with icons/make-icns.sh too.
                iconFile.set(project.file("icons/SlowDowner.ico"))
            }
            linux {
                // downstep_<version>_<arch>.deb, installed in /opt/downstep.
                packageName = "downstep"
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo"
                iconFile.set(project.file("icons/SlowDowner.png"))
            }
        }
    }
}

// Class-data sharing (CDS), for a faster cold start: the JDK's classes come mapped from an archive
// instead of being parsed and verified at every launch. A small gain, measured on Windows to the
// first composition: 1.51 s instead of 1.54 s with a warm disk cache, 1.54 s instead of 1.76 s in a
// colder series. jlink leaves the JDK's default archive out of the bundled runtime unless asked, and
// this Compose plugin has no CDS option.
//
// The app's own classes are not archived. -XX:+AutoCreateSharedArchive (archive at the first exit,
// map it after) made that first start take ~14 s instead of 1.5 and needs a writable app folder; the
// macOS bundle is signed and read-only. That would need an archive made at build time by a training
// run of the app.
//
// jlink's own --generate-cds-archive runs the image's java launcher, which Compose strips
// (--strip-native-commands). Same dump by hand: borrow the launcher of the JDK the image comes from,
// which loads the image's own JVM and modules, then remove it again.
tasks.withType<AbstractJLinkTask>().configureEach {
    val jdk = javaHome
    val runtime = destinationDir
    doLast {
        val launcherName = if (isWindows) "java.exe" else "java"
        val borrowed = runtime.get().asFile.resolve("bin/$launcherName")
        // Only a slower start without it: any failure is worth a warning, not a failed release.
        try {
            borrowed.parentFile.mkdirs()
            // With its attributes: File.copyTo drops the execute bit, and on Linux and macOS the
            // launcher then cannot run (error=13).
            Files.copy(
                File(jdk.get(), "bin/$launcherName").toPath(),
                borrowed.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES,
            )
            borrowed.setExecutable(true)
            val dump = ProcessBuilder(borrowed.path, "-Xshare:dump").redirectErrorStream(true).start()
            val output = dump.inputStream.bufferedReader().readText()
            if (dump.waitFor() != 0) logger.warn("No CDS archive in the bundled runtime: $output")
        } catch (e: Exception) {
            logger.warn("No CDS archive in the bundled runtime: ${e.message}")
        } finally {
            borrowed.delete()
            borrowed.parentFile.delete() // only if the image had no bin folder of its own
        }
    }
}

// Crash reports (Sentry). The DSN and the token exist only in the release workflow: local runs,
// tests and the testers' builds report nothing. With the token, the release uploads the sources,
// so a stack trace shows the code around each line.
val sentryAuthToken: String? = providers.environmentVariable("SENTRY_AUTH_TOKEN").orNull?.takeIf { it.isNotBlank() }
sentry {
    org.set("damianogiusticom")
    projectName.set("downstep")
    authToken.set(sentryAuthToken)
    includeSourceContext.set(sentryAuthToken != null)
    // Most stack frames are in the shared modules, not here.
    additionalSourceDirsForSourceContext.set(
        listOf("shared/src/commonMain/kotlin", "shared-data/src/commonMain/kotlin", "shared-data/src/desktopMain/kotlin",
            "shared-ui/src/commonMain/kotlin", "shared-ui/src/desktopMain/kotlin", "engine-desktop/src/main/kotlin")
            .map { rootProject.file(it).path },
    )
    // The SDK is the dependency above, at a version we pick.
    autoInstallation { enabled.set(false) }
    telemetry.set(false)
}
// The Sentry tasks read the sources Compose generates (Res and friends) but do not declare it, and
// Gradle stops the build. After compileKotlin those sources exist.
tasks.matching { it.name.startsWith("generateSentryBundleId") || it.name.startsWith("sentryCollectSources") }
    .configureEach { dependsOn("compileKotlin") }
// The plugin uploads the sources on assemble, which the release does not run: it packages directly.
if (sentryAuthToken != null) {
    tasks.matching { it.name == "createDistributable" || it.name == "packageDmg" || it.name == "packageDeb" }
        .configureEach { dependsOn("sentryUploadSourceBundleJava") }
}
