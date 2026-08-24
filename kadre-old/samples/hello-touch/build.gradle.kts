/**
 * Sample hello-touch — demonstrates the Kadre touch events pipeline.
 *
 * KMP targets: androidTarget + iOS (iosArm64, iosSimulatorArm64).
 * The [HelloTouchHandler] handler is defined in commonMain and shared
 * between the Android target (consumed by hello-touch-android) and iOS.
 */
plugins {
    id("ygdrasil.conventions.kmp-library")
}

kotlin {
    android {
        namespace = "org.graphiks.kadre.samples.hellotouch"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":kadre"))
            }
        }
    }
}
