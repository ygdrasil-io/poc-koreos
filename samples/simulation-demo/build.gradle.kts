plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.plugin.allopen") version "2.0.0"
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://repo.graphiks.org/repository/maven-public")
}

kotlin {
    jvm()
    jvmToolchain(25)

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":kadre"))
                implementation(project(":kadre-appkit"))
                implementation(project(":kadre-test"))
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.material3)
                implementation(compose.desktop.currentOs)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs Simulation Demo on JVM"
    dependsOn("jvmJar")
    mainClass.set("org.graphiks.kadre.samples.simulation.MainKt")
    classpath = files(
        kotlin.targets.getByName("jvm").compilations.getByName("main").output.allOutputs,
        configurations.getByName("jvmRuntimeClasspath"),
    )
    jvmArgs(buildList {
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
        add("--enable-native-access=ALL-UNNAMED")
    })
}
