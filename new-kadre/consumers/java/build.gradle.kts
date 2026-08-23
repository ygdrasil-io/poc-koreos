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
    mavenCentral()
}

dependencies {
    implementation("org.graphiks.kadre:kadre-new:$kadreVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
