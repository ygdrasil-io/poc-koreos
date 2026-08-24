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
