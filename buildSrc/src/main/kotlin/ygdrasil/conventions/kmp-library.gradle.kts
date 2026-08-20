package ygdrasil.conventions

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(25)

    android {
        compileSdk = 35
        minSdk = 24
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm() // Target for Desktop (JVM)

    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}
