import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
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
        from(engineBuildDir.file("libsdengine.dylib"))
        into(appResources.map { it.dir("macos-arm64") })
    }
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(syncEngineLibrary) }

// CI passes -PappVersion=1.0.<run number>: the band reports it from the window title or the log.
val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")

compose.desktop {
    application {
        mainClass = "com.damianogiusti.slowdowner.desktop.MainKt"
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(23)) }
            .get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Dslowdowner.version=$appVersion")

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
