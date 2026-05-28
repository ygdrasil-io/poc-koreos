/**
 * Module koreos-appkit — backend macOS Desktop via FFM JVM 25 + kextract.
 *
 * Cible : jvm uniquement.
 * Ce module implémente les interfaces de koreos-core pour AppKit (NSWindow,
 * NSApplication, NSView layer-backed) en utilisant la Foreign Function &
 * Memory API (JEP 454) générée par kextract.
 *
 * Dépendance amont : koreos-core (uniquement la sourceSet jvm via jvmMain).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("ygdrasil.conventions.kmp-publish")
}

kotlin {
    jvmToolchain(25)

    jvm()

    sourceSets {
        jvmMain {
            dependencies {
                api(project(":koreos-core"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
