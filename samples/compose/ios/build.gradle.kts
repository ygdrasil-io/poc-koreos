plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    jvmToolchain(25)

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":samples:compose:shared"))
            }
        }
    }
}
