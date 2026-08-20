plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(25)
    jvm()

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
