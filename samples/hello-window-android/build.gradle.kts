/**
 * Sample hello-window-android — fenêtre Koreos sur Android.
 *
 * Miroir Android du sample hello-window (JVM/iOS).
 * Démontre la convergence d'API Koreos : même HelloApp, même comportement
 * sur les trois plateformes.
 *
 * Usage : ./gradlew :samples:hello-window-android:assembleDebug
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.ygdrasil.koreos.samples.hellowindowandroid"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.ygdrasil.koreos.samples.hellowindowandroid"
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
