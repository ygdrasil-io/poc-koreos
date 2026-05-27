/**
 * Sample hello-metal — démonstrateur POC Metal view minimale (Jalon M1).
 *
 * Ce module illustre l'utilisation de koreos pour ouvrir une fenêtre macOS
 * native avec un contentView layer-backed (CAMetalLayer), prête pour un
 * renderer Metal ou wgpu4k.
 *
 * Cible : jvm (JDK 25, macOS Desktop uniquement).
 * Dépendance : koreos (façade) qui embarque koreos-appkit en jvmMain.
 *
 * Note : ce module utilise le plugin Kotlin/JVM (non KMP) car il est jvm-only
 * et doit pouvoir déclarer un point d'entrée d'application standard.
 */
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":koreos"))
}
