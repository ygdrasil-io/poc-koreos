/**
 * Module koreos — façade publique KMP.
 *
 * Ce module expose l'API Koreos aux consommateurs finaux via le mécanisme
 * expect/actual. Il délègue vers les backends spécifiques :
 *   - jvmMain  → koreos-appkit (macOS Desktop via FFM/kextract)
 *   - iosMain  → koreos-uikit (M3, hors scope M1)
 *   - androidMain → koreos-android (M3, hors scope M1)
 *
 * Cibles KMP : jvm, androidTarget, iosX64, iosArm64, iosSimulatorArm64.
 */
plugins {
    id("ygdrasil.conventions.kmp-library")
    id("ygdrasil.conventions.kmp-dokka")
}

android {
    namespace = "io.ygdrasil.koreos"
}

kotlin {
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
    }
}
