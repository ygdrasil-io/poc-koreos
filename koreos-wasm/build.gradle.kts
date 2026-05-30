/**
 * Module koreos-wasm — façade koreos pour la cible Kotlin/Wasm (wasmJs).
 *
 * Expose l'API koreos aux consommateurs WebAssembly via la cible wasmJs.
 * Délègue vers koreos-web-common pour l'implémentation partagée web (JS + wasmJs).
 *
 * Cible KMP : wasmJs + browser uniquement.
 *
 * GRA-32 : setup initial du module koreos-wasm.
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        wasmJsMain {
            dependencies {
                api(project(":koreos-web-common"))
                api(project(":koreos-core"))
            }
        }
        wasmJsTest {
            dependencies {
                implementation(kotlin("test-wasm-js"))
            }
        }
    }
}
