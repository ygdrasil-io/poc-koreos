@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("maven-publish")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    android {
        compileSdk = 35
        minSdk = 24
        namespace = "org.graphiks.kadre.foundation"
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {
        }
    }
    jvm()
    js { browser() }
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}

// Les variantes natives iOS/tvOS ne se publient que depuis un hôte Apple : les gates
// ubuntu (web-contracts) publient jvm/js/wasm via le même agrégat contractTest, et un
// hôte non-Apple ne produit pas les klibs. La résolution native depuis contractTest
// sera révisée quand platform:uikit sera officiellement publié (phase 1).
if (!org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
    tasks.configureEach {
        val nativePublicationTask = name.startsWith("publish") &&
            (name.contains("Ios") || name.contains("Tvos"))
        val nativeMetadataTask = name.startsWith("generateMetadataFileFor") &&
            (name.contains("Ios") || name.contains("Tvos"))
        if (nativePublicationTask || nativeMetadataTask) {
            enabled = false
        }
    }
}
