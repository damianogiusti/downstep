import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    enabled = osName.startsWith("Mac") || isWindows
}

// The C engine travels inside the app, in Compose's per-OS resources folder. On Windows LAME is
// a DLL of its own (LGPL), next to the engine.
val appResources = layout.buildDirectory.dir("appResources")
val syncEngineLibrary = tasks.register<Sync>("syncEngineLibrary") {
    dependsOn(":engine-desktop:buildNativeEngine")
    if (isWindows) {
        from(engineBuildDir.file("sdengine.dll"), engineBuildDir.file("mp3lame.dll"))
        into(appResources.map { it.dir("windows-x64") })
    } else {
        val arch = when (System.getProperty("os.arch")) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "x64"
            else -> throw GradleException("Unsupported macOS architecture: ${System.getProperty("os.arch")}")
        }
        from(engineBuildDir.file("libsdengine.dylib"))
        into(appResources.map { it.dir("macos-$arch") })
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
            targetFormats(TargetFormat.Dmg)
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
    tasks.matching { it.name == "createDistributable" || it.name == "packageDmg" }
        .configureEach { dependsOn("sentryUploadSourceBundleJava") }
}
