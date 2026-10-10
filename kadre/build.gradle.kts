@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("maven-publish")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    android {
        compileSdk = 35
        minSdk = 24
        namespace = "org.graphiks.kadre"
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm()
    js { browser() }
    wasmJs { browser() }
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre:foundation"))
        }
        androidMain.dependencies {
            api(project(":kadre:platform:android"))
        }
        jvmMain.dependencies {
            api(project(":kadre:platform:desktop"))
        }
        jsMain.dependencies {
            api(project(":kadre:platform:web"))
        }
        wasmJsMain.dependencies {
            api(project(":kadre:platform:web"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            // Parseur du test de pureté (voir AndroidVariantPurityTest) : Json.parseToJsonElement
            // ne nécessite pas le plugin de sérialisation, juste le runtime.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

tasks.named("check") {
    dependsOn(":kadre:foundation:check")
    dependsOn(":kadre:contracts:validator:check")
    dependsOn(":kadre:backend:appkit:check")
    dependsOn(":kadre:platform:desktop:check")
    dependsOn(":kadre:platform:web:check")
    dependsOn(":kadre:runtime:check")
    dependsOn("validateKotlinConsumer")
    dependsOn("validateJavaConsumer")
    dependsOn("validateWebKotlinConsumer")
    dependsOn("validateTypeScriptConsumer")
    // pin : la pureté est vérifiée à chaque check local avec les artefacts publiés
    dependsOn("jvmTest")
}

val contractTestRepository = rootProject.layout.buildDirectory.dir("kadre-contract-repository")
val contractPublications = tasks.register("publishContractArtifacts") {
    dependsOn(":kadre:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:foundation:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:backend:appkit:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:platform:desktop:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:platform:web:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:runtime:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:platform:android:publishAllPublicationsToContractTestRepository")
}

tasks.register<GradleBuild>("validateKotlinConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/kotlin")
    tasks = listOf("compileKotlin")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

tasks.register<GradleBuild>("validateJavaConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/java")
    tasks = listOf("compileJava")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

tasks.register<GradleBuild>("validateWebKotlinConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/web")
    tasks = listOf("compileKotlinJs", "compileKotlinWasmJs")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

tasks.register<GradleBuild>("validateTypeScriptConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/typescript")
    tasks = listOf("check")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

tasks.named<Test>("jvmTest") {
    systemProperty("kadreContractRepository", rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.absolutePath)
    systemProperty("kadreVersion", project.version.toString())
    dependsOn("publishContractArtifacts")
}

/**
 * The browser entries of the TypeScript consumer, as the Web driver's host page consumes them.
 *
 * The browser consumer is part of the consumer build's own `check` (`emitBrowserConsumer` is a
 * dependency of it), so this task names that one nested invocation instead of starting a second one:
 * Gradle refuses two `GradleBuild` tasks on the same directory in one invocation, which is what the
 * Web smoke and this check would otherwise be. Everything the emission needs is already published by
 * `contractPublications`, which `validateTypeScriptConsumer` depends on.
 */
tasks.register("emitTypeScriptBrowserConsumer") {
    group = "build"
    description = "Names the browser consumers emitted by the TypeScript consumer check."
    dependsOn("validateTypeScriptConsumer")
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}
