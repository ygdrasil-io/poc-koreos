/**
 * Module koreos-js — façade koreos pour la cible Kotlin/JS (IR).
 *
 * Expose l'API koreos aux consommateurs JavaScript/TypeScript via la cible js(IR).
 * Délègue vers koreos-web-common pour l'implémentation partagée web (JS + wasmJs).
 *
 * Cible KMP : js(IR) + browser uniquement.
 *
 * GRA-30 : setup initial du module koreos-js.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    js(IR) { browser() }

    sourceSets {
        jsMain {
            dependencies {
                api(project(":koreos-web-common"))
                api(project(":koreos-core"))
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
