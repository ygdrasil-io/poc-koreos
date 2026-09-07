@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("maven-publish")
}

val foundationProject = project(":kadre:foundation")
val foundationAllMetadataJar = foundationProject.tasks.named<Jar>("allMetadataJar")
val foundationCommonMainMetadata = foundationProject.layout.buildDirectory.dir("classes/kotlin/metadata/commonMain")
val runtimeProject = project(":kadre:runtime")
val foundationJsMain = foundationProject.layout.buildDirectory.dir("classes/kotlin/js/main")
val foundationWasmJsMain = foundationProject.layout.buildDirectory.dir("classes/kotlin/wasmJs/main")
val runtimeJsMain = runtimeProject.layout.buildDirectory.dir("classes/kotlin/js/main")
val runtimeWasmJsMain = runtimeProject.layout.buildDirectory.dir("classes/kotlin/wasmJs/main")

kotlin {
    applyDefaultHierarchyTemplate()
    js { browser() }
    wasmJs { browser() }
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre:foundation"))
            implementation(project(":kadre:runtime"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        wasmJsMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
        }
    }
}

tasks.withType<KotlinCompileCommon>().configureEach {
    if (name == "compileCommonMainKotlinMetadata" || name == "compileWebMainKotlinMetadata") {
        dependsOn(foundationAllMetadataJar)
        friendPaths.from(foundationCommonMainMetadata)
    }
}

tasks.withType<Kotlin2JsCompile>().configureEach {
    when (name) {
        "compileKotlinJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinJs"))
            dependsOn(runtimeProject.tasks.named("compileKotlinJs"))
            compilerOptions.freeCompilerArgs.add(
                runtimeJsMain.zip(foundationJsMain) { runtime, foundation ->
                    "-Xfriend-modules=${runtime.asFile.absolutePath}${File.pathSeparator}${foundation.asFile.absolutePath}"
                },
            )
        }
        "compileKotlinWasmJs" -> {
            dependsOn(foundationProject.tasks.named("compileKotlinWasmJs"))
            dependsOn(runtimeProject.tasks.named("compileKotlinWasmJs"))
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
