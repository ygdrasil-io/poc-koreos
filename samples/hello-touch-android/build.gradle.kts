/**
 * Sample hello-touch-android — démontre le pipeline touch events sur Android.
 *
 * Miroir du sample hello-touch iOS, démontre la convergence d'API Koreos.
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
    implementation("androidx.activity:activity:1.10.1")
}
