/**
 * Module koreos-test — utilitaires de test pour Koreos (Redmine #89).
 *
 * Fournit [ScriptedEventLoop] et le DSL `scriptedTest { ... }` permettant de
 * piloter un [io.ygdrasil.koreos.core.ApplicationHandler] avec une séquence
 * d'événements déterministe, sans backend natif (NSApp, Activity, etc.).
 *
 * Module NON publié : helper de test réutilisable en commonTest par les samples
 * et les modules backend. Mêmes cibles que koreos-core pour une portabilité totale.
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("ygdrasil.conventions.kmp-library")
}

android {
    namespace = "io.ygdrasil.koreos.test"
}

kotlin {
    js(IR) { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":koreos-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jsTest.dependencies {
            implementation(kotlin("test-js"))
        }
        wasmJsTest.dependencies {
            implementation(kotlin("test-wasm-js"))
        }
    }
}
