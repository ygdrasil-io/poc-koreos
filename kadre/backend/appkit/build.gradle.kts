plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

group = "org.graphiks.kadre.internal"

kotlin {
    jvmToolchain(25)
    jvm()
    explicitApi()

    sourceSets {
        jvmMain.dependencies {
            api(project(":kadre:runtime"))
            implementation(libs.kffi.objc)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

if (System.getProperty("os.name", "").let { name ->
        name.contains("Mac", ignoreCase = true) || name.contains("Darwin", ignoreCase = true)
    }
) {
    tasks.named<Test>("jvmTest") {
        jvmArgs("-XstartOnFirstThread", "--enable-native-access=ALL-UNNAMED")
    }
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}
