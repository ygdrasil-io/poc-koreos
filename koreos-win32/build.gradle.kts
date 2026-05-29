/**
 * Module koreos-win32 — backend Windows Desktop via FFM JVM 25 + kextract.
 *
 * Cible : jvm uniquement.
 * Ce module implémente les interfaces de koreos-core pour Win32 (HWND,
 * CreateWindowEx, MSG loop, etc.) en utilisant la Foreign Function &
 * Memory API (JEP 454) générée par kextract.
 *
 * Note : Les bindings kextract générés (Win32_h.kt) seront ajoutés en Sprint 3
 * une fois le Windows SDK configuré dans le pipeline CI. Ce module fournit
 * pour l'instant les type aliases et le stub Win32Runtime.
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
