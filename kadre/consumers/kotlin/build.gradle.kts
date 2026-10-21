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
    // kadre depends on kffi snapshot artifacts: consumers resolve them from
    // the Sonatype snapshots repository (the release flow publishes there)
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    mavenCentral()
}

dependencies {
    // Dépendances explicites (phase 13, clôture wayland-D4) : la transitivité
    // POM à travers le repo contractuel ne garantit pas la résolution des
    // modules frères — le consumer les déclare lui-même.
    implementation("org.graphiks.kadre:kadre:$kadreVersion")
    implementation("org.graphiks.kadre:foundation-jvm:$kadreVersion")
    implementation("org.graphiks.kadre:runtime-jvm:$kadreVersion")
    implementation("org.graphiks.kadre:desktop-jvm:$kadreVersion")
    // Provider win32 publié (phase 13) : l'exécution de découverte ServiceLoader
    // a besoin du jar provider sur le classpath runtime ; jamais project(...).
    implementation("org.graphiks.kadre.internal:win32:$kadreVersion")
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}

tasks.register<JavaExec>("win32ConsumerRun") {
    group = "verification"
    description = "Runs the win32 discovery + standalone session consumer proof."
    mainClass.set("Win32DiscoveryMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
