plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

kotlin {
    androidLibrary {
        namespace = "com.damianogiusti.slowdowner.data"
        compileSdk = 37
        minSdk = 23
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    // Android and desktop are both JVMs: commonMain may use java.io, OkHttp, NewPipe and java.util.zip.
    // iOS (phase 6) will need its own data sources for those.
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            // Public API: SlowDownerDatabase is a RoomDatabase, built by each app.
            api("androidx.room:room-runtime:2.8.5")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
            implementation("com.squareup.okhttp3:okhttp:5.5.0")
            implementation(project.dependencies.platform("io.insert-koin:koin-bom:4.1.1"))
            implementation("io.insert-koin:koin-core")
        }
        getByName("desktopMain").dependencies {
            implementation("androidx.sqlite:sqlite-bundled:2.6.2")
        }
        getByName("desktopTest").dependencies {
            implementation("junit:junit:4.13.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            // MigrationTestHelper: builds old versions from the exported schemas.
            implementation("androidx.room:room-testing:2.8.5")
        }
    }
}

dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.8.5")
    add("kspDesktop", "androidx.room:room-compiler:2.8.5")
}

room {
    // Exported schemas are committed: they document every version and feed migration tests.
    schemaDirectory("$projectDir/schemas")
}
