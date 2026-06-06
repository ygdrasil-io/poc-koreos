plugins {
    kotlin("jvm")
    application
    id("org.jetbrains.kotlin.plugin.allopen") version "2.0.0"
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://repo.graphiks.org/repository/maven-public")
}

kotlin {
    jvmToolchain(25)
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/commonMain/kotlin", "src/jvmMain/kotlin")
        }
    }
    test {
        kotlin {
            srcDirs("src/commonTest/kotlin")
        }
    }
}

application {
    mainClass.set("org.graphiks.kadre.samples.simulation.MainKt")

    applicationDefaultJvmArgs = buildList {
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
        add("--enable-native-access=ALL-UNNAMED")
    }
}

dependencies {
    implementation(project(":kadre"))
    implementation(project(":kadre"))
    implementation("org.jetbrains.compose.runtime:runtime:1.6.0")
    implementation("org.jetbrains.compose.ui:ui:1.6.0")
    implementation("org.jetbrains.compose.material:material:1.6.0")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.6.0")
    implementation(project(":kadre-appkit"))
    implementation(project(":kadre-test"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "25"
    targetCompatibility = "25"
}
