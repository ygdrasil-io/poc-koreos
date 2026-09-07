@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

group = "org.graphiks.kadre.internal"

val foundationJvmJar = project(":kadre:foundation").tasks.named<Jar>("jvmJar")
val foundationProject = project(":kadre:foundation")
val foundationJsMain = foundationProject.layout.buildDirectory.dir("classes/kotlin/js/main")
val foundationWasmJsMain = foundationProject.layout.buildDirectory.dir("classes/kotlin/wasmJs/main")
val runtimeJsMain = layout.buildDirectory.dir("classes/kotlin/js/main")
val runtimeWasmJsMain = layout.buildDirectory.dir("classes/kotlin/wasmJs/main")

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    jvm()
    js { browser() }
    wasmJs { browser() }
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre:foundation"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<BaseKotlinCompile>().configureEach {
    if (name.endsWith("Jvm")) {
        dependsOn(foundationJvmJar)
        friendPaths.from(foundationJvmJar.flatMap(Jar::getArchiveFile))
    }
}

tasks.withType<Kotlin2JsCompile>().configureEach {
    when (name) {
        "compileKotlinJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinJs"))
            compilerOptions.freeCompilerArgs.add(
                foundationJsMain.map { "-Xfriend-modules=${it.asFile.absolutePath}" },
            )
        }

        "compileTestKotlinJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinJs"))
            compilerOptions.freeCompilerArgs.add(
                runtimeJsMain.zip(foundationJsMain) { runtime, foundation ->
                    "-Xfriend-modules=${runtime.asFile.absolutePath}${File.pathSeparator}${foundation.asFile.absolutePath}"
                },
            )
        }

        "compileKotlinWasmJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinWasmJs"))
            compilerOptions.freeCompilerArgs.add(
                foundationWasmJsMain.map { "-Xfriend-modules=${it.asFile.absolutePath}" },
            )
        }

        "compileTestKotlinWasmJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinWasmJs"))
            compilerOptions.freeCompilerArgs.add(
                runtimeWasmJsMain.zip(foundationWasmJsMain) { runtime, foundation ->
                    "-Xfriend-modules=${runtime.asFile.absolutePath}${File.pathSeparator}${foundation.asFile.absolutePath}"
                },
            )
        }
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
