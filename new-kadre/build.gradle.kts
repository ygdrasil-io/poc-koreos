plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(25)
    jvm()
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre-new:foundation"))
        }
    }
}

tasks.named("check") {
    dependsOn(":kadre-new:foundation:check")
    dependsOn(":kadre-new:contracts:validator:check")
}
