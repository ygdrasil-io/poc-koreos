plugins {
    java
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
    implementation("org.graphiks.kadre:kadre:$kadreVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
