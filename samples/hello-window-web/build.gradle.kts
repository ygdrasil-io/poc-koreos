/**
 * Sample hello-window-web — fenêtre canvas web cross-cible (JS + Wasm).
 *
 * Démontre l'API Koreos de base pour les cibles navigateur :
 * création de fenêtre canvas, journalisation des événements DOM.
 *
 * Cibles : js(IR) browser, wasmJs browser.
 *
 * Redmine #26 : setup module samples/hello-window-web.
 */
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "hello-window-web.js"
            }
        }
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "hello-window-wasm.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":koreos"))
            }
        }
        jsMain {
            dependencies {
                implementation(project(":koreos-js"))
            }
        }
        wasmJsMain {
            dependencies {
                implementation(project(":koreos-wasm"))
            }
        }
    }
}
