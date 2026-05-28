/**
 * Sample hello-triangle — intégration wgpu4k : Instance + Surface depuis CAMetalLayer (GRA-137).
 *
 * Consomme le [RawWindowHandle] de Koreos pour initialiser wgpu4k :
 *   EventLoop → AppKitEventLoop → NSWindow + CAMetalLayer → WGPU Instance + Surface + Adapter + Device
 *
 * Périmètre M2 — pas encore de rendu : la fenêtre reste vide (clear par défaut).
 *
 * Usage : ./gradlew :samples:hello-triangle:run
 * Prérequis : macOS avec JDK 25 (thread principal — lancé par Gradle).
 */
plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("io.ygdrasil.koreos.samples.hellotriangle.MainKt")

    // macOS : NSApplication doit tourner sur le thread principal JVM.
    // Panama FFM : --enable-native-access supprime les warnings JDK 22+.
    applicationDefaultJvmArgs = listOf(
        "-XstartOnFirstThread",
        "--enable-native-access=ALL-UNNAMED",
    )
}

dependencies {
    implementation(project(":koreos"))
    implementation(libs.wgpu4k)
    // runBlocking — transitif via wgpu4k mais déclaré explicitement pour la clarté
    implementation(libs.kotlinx.coroutines.core)
}
