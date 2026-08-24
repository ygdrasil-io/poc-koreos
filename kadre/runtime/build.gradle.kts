import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

group = "org.graphiks.kadre.internal"

val foundationJvmJar = project(":kadre:foundation").tasks.named<Jar>("jvmJar")

kotlin {
    jvmToolchain(25)
    jvm()
    explicitApi()

    sourceSets {
        jvmMain.dependencies {
            api(project(":kadre:foundation"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    dependsOn(foundationJvmJar)
    compilerOptions.freeCompilerArgs.add(
        foundationJvmJar.flatMap(Jar::getArchiveFile).map { archive ->
            "-Xfriend-paths=${archive.asFile.absolutePath}"
        },
    )
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}
