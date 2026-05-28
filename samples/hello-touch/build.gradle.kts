/**
 * Sample hello-touch — démontre le pipeline touch events sur iOS.
 *
 * Cibles iOS uniquement (pas de JVM — wgpu4k K/N hors scope).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":koreos"))
            }
        }
    }
}
