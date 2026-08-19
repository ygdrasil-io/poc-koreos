/**
 * Module kadre-android — Android backend for kadre.
 *
 * Strategy A: exposes raw android.view.Surface, no custom JNI.
 * Targets: androidTarget only (no jvm or iOS in this module).
 *
 * Uses kmp-library (which adds jvm + iOS via convention) but only the
 * androidTarget has source code. iOS/JVM publications are disabled
 * to avoid missing klib errors during publishToMavenLocal.
 *
 * GRA-147 : setup module Android.
 * GRA-159 : publication Maven Central.
 */
plugins {
    id("ygdrasil.conventions.kmp-library")
    id("ygdrasil.conventions.kmp-publish")
}

afterEvaluate {
    // kadre-android is Android-only. kmp-library adds iOS/JVM targets but they
    // have no source, so their klibrary files are never generated and Maven
    // publication would fail. Disable those publication/metadata tasks.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        val pubName = publication?.name ?: ""
        if (pubName.startsWith("ios") || pubName == "jvm") {
            enabled = false
        }
    }
    tasks.withType<GenerateModuleMetadata>().configureEach {
        val pubName = publication.orNull?.name ?: ""
        if (pubName.startsWith("ios") || pubName == "jvm") {
            enabled = false
        }
    }
}

kotlin {
    android {
        namespace = "org.graphiks.kadre.android"
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
    }

    // ABI compatibility validation — integrated into the Kotlin plugin.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":kadre-core"))
            }
        }
        androidMain {
            dependencies {
                implementation("androidx.activity:activity:1.10.1")
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":kadre-test"))
            }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("androidx.test.ext:junit:1.2.1")
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test:core:1.6.1")
            }
        }
    }
}
