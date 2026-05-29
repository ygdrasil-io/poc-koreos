/**
 * Module koreos — façade publique KMP.
 *
 * Ce module expose l'API Koreos aux consommateurs finaux via le mécanisme
 * expect/actual. Il délègue vers les backends spécifiques :
 *   - jvmMain     → koreos-appkit (macOS Desktop via FFM/kextract)
 *   - iosMain     → koreos-uikit (M3, hors scope M1)
 *   - androidMain → koreos-android (M3, hors scope M1)
 *   - jsMain      → koreos-web-common (stub — impl complète dans #24)
 *   - wasmJsMain  → koreos-web-common (stub — impl complète dans #24)
 *
 * Cibles KMP : jvm, androidTarget, iosX64, iosArm64, iosSimulatorArm64, js(IR), wasmJs.
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("ygdrasil.conventions.kmp-library")
    id("ygdrasil.conventions.kmp-dokka")
    id("ygdrasil.conventions.kmp-publish")
}

android {
    namespace = "io.ygdrasil.koreos"
}

kotlin {
    // Cibles web — en plus des cibles iOS/JVM/Android du plugin de convention
    js(IR) { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":koreos-core"))
            }
        }

        androidMain {
            dependencies {
                api(project(":koreos-android"))
            }
        }

        jvmMain {
            dependencies {
                api(project(":koreos-appkit"))
            }
        }

        iosArm64Main {
            dependencies {
                api(project(":koreos-uikit"))
            }
        }

        iosSimulatorArm64Main {
            dependencies {
                api(project(":koreos-uikit"))
            }
        }

        iosX64Main {
            dependencies {
                api(project(":koreos-uikit"))
            }
        }

        jsMain {
            dependencies {
                api(project(":koreos-web-common"))
            }
        }

        wasmJsMain {
            dependencies {
                api(project(":koreos-web-common"))
            }
        }
    }
}
