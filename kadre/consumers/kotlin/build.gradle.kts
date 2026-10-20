plugins {
    kotlin("jvm") version "2.4.0"
}

val kadreRepository = providers.gradleProperty("kadreRepository")
    .orNull
    ?: error("-PkadreRepository is required")
val kadreVersion = providers.gradleProperty("kadreVersion")
    .orNull
    ?: error("-PkadreVersion is required")

repositories {
    maven { url = uri(kadreRepository) }
    mavenCentral()
}

dependencies {
    implementation("org.graphiks.kadre:kadre:$kadreVersion")
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    // Provider win32 publié (phase 13) : l'exécution de découverte ServiceLoader
    // a besoin du jar provider sur le classpath runtime ; jamais project(...).
    implementation("org.graphiks.kadre.internal:win32:$kadreVersion")
}

tasks.register<JavaExec>("win32ConsumerRun") {
    group = "verification"
    description = "Runs the win32 discovery + standalone session consumer proof."
    mainClass.set("Win32DiscoveryMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
