/**
 * Module koreos-core — interfaces et types Kotlin pur, aucune référence native.
 *
 * Cibles KMP : jvm, androidTarget, iosX64, iosArm64, iosSimulatorArm64.
 * Ce module ne doit contenir aucun code dépendant d'une plateforme (pas de
 * java.*, platform.*, android.*) afin de rester 100 % commonMain.
 */
plugins {
    id("ygdrasil.conventions.kmp-library")
    id("ygdrasil.conventions.kmp-dokka")
}

android {
    namespace = "io.ygdrasil.koreos.core"
}
