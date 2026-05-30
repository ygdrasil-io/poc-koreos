/**
 * Module samples/pong — squelette KMP 6 cibles.
 *
 * Cibles : jvm, androidTarget, iosX64, iosArm64, iosSimulatorArm64, js(IR), wasmJs.
 * Redmine #73 : setup module (skeleton uniquement, pas d'implémentation).
 *
 * Tickets liés :
 *   #74 : GameState — commonMain
 *   #79 : PongGame — commonMain
 *   #80 : entry points par plateforme
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("ygdrasil.conventions.kmp-library")
}

android {
    namespace = "io.ygdrasil.koreos.samples.pong"
}

kotlin {
    // Cibles web — en plus des cibles iOS/JVM/Android du plugin de convention
    js(IR) { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":koreos"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                // wgpu4k — rendu WebGPU JVM (PongRenderer)
                implementation(libs.wgpu4k)
                implementation(libs.webgpu.ktypes.descriptors)
                implementation(libs.kotlinx.coroutines.core)
                // koreos-appkit — ObjCRuntime (macOS CAMetalLayer)
                implementation(project(":koreos-appkit"))
            }
        }
    }
}

// JVM run task — uses -XstartOnFirstThread (required for AppKit on macOS)
// Usage : ./gradlew :samples:pong:run
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs Pong on JVM (macOS AppKit + wgpu4k)"
    dependsOn("jvmJar")
    mainClass.set("io.ygdrasil.koreos.samples.pong.MainKt")
    classpath = files(
        kotlin.targets.getByName("jvm").compilations.getByName("main").output.allOutputs,
        configurations.getByName("jvmRuntimeClasspath"),
    )
    jvmArgs(
        "-XstartOnFirstThread",
        "--enable-native-access=ALL-UNNAMED",
    )
}
