/**
 * Sample hello-touch-android — application Android de démonstration touch events.
 *
 * Consomme [io.ygdrasil.koreos.samples.hellotouch.HelloTouchHandler] depuis
 * `:samples:hello-touch` (commonMain KMP), démontrant que le handler est
 * 100 % Kotlin commun entre iOS et Android.
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.ygdrasil.koreos.samples.hellotouchandroid"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.ygdrasil.koreos.samples.hellotouchandroid"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(project(":koreos"))
    implementation(project(":samples:hello-touch"))
    implementation("androidx.activity:activity:1.10.1")
}
