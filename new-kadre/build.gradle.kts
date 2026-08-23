plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

kotlin {
    jvmToolchain(25)
    jvm()
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre-new:foundation"))
        }
        jvmMain.dependencies {
            api(project(":kadre-new:platform:desktop"))
        }
    }
}

tasks.named("check") {
    dependsOn(":kadre-new:foundation:check")
    dependsOn(":kadre-new:contracts:validator:check")
    dependsOn(":kadre-new:platform:desktop:check")
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("new-kadre-contract-repository").get().asFile.toURI()
        }
    }
}
