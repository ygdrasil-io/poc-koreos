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
val runtimeAllMetadataJar = runtimeProject.tasks.named<Jar>("allMetadataJar")
val runtimeCommonMainMetadata = runtimeProject.layout.buildDirectory.dir("classes/kotlin/metadata/commonMain")
val foundationJsMain = foundationProject.layout.buildDirectory.dir("classes/kotlin/js/main")
val foundationWasmJsMain = foundationProject.layout.buildDirectory.dir("classes/kotlin/wasmJs/main")
val runtimeJsMain = runtimeProject.layout.buildDirectory.dir("classes/kotlin/js/main")
val runtimeWasmJsMain = runtimeProject.layout.buildDirectory.dir("classes/kotlin/wasmJs/main")

kotlin {
    applyDefaultHierarchyTemplate()
    js {
        outputModuleName.set("kadre-platform-web")
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
    }
    wasmJs {
        outputModuleName.set("kadre-platform-web")
        browser()
        binaries.library()
        // The JS target exposes the same switch as `generateTypeScriptDefinitions()`; the Wasm
        // target has no such DSL entry, so the compiler flag is added directly. It is what makes
        // the two targets produce the declaration files `types/kadre-host.d.ts` is reconciled with.
        compilerOptions.freeCompilerArgs.add("-Xgenerate-dts")
    }
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
        // webMain references internal runtime types. The js/wasm compilations already
        // friend the runtime through -Xfriend-modules, so the shared web metadata
        // compilation must expose the same friends or it cannot resolve them.
        dependsOn(runtimeAllMetadataJar)
        friendPaths.from(runtimeCommonMainMetadata)
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

/**
 * The `@kadre/host` npm package of one target.
 *
 * The Kotlin library distribution carries the compiled module and the declarations the compiler
 * generated for it; `types/kadre-host.d.ts` is the curated contract of
 * `kadre/INTEROP-EXPORTS.md` section 6 and becomes the package's `index.d.ts`. The task refuses to
 * package a symbol the curated file does not declare, so the two cannot drift apart silently.
 */
fun registerHostPackage(target: String, distributionTask: String, moduleFile: String) =
    tasks.register<Sync>("${target}HostPackage") {
        group = "build"
        description = "Assembles the @kadre/host package for the $target target."
        dependsOn(distributionTask)
        inputs.file(layout.projectDirectory.file("types/kadre-host.d.ts"))
        from(layout.buildDirectory.dir("dist/$target/productionLibrary"))
        from(layout.projectDirectory.file("types/kadre-host.d.ts")) { rename { "index.d.ts" } }
        into(layout.buildDirectory.dir("dist/$target/host-package"))
        doLast {
            val directory = layout.buildDirectory.dir("dist/$target/host-package").get().asFile
            directory.resolve("package.json").writeText(
                """
                {
                  "name": "@kadre/host",
                  "version": "${project.version}",
                  "type": "module",
                  "main": "$moduleFile",
                  "module": "$moduleFile",
                  "types": "index.d.ts",
                  "private": false
                }
                """.trimIndent() + "\n",
            )
            check(directory.resolve(moduleFile).isFile) {
                "the $target package must ship $moduleFile"
            }
            check(directory.resolve("index.d.ts").isFile) {
                "the $target package must declare index.d.ts"
            }
            val curated = directory.resolve("index.d.ts").readText()
            val generated = directory.walkTopDown()
                .filter { it.isFile && it.name != "index.d.ts" }
                .filter { it.name.endsWith(".d.ts") || it.name.endsWith(".d.mts") }
                .flatMap { declarationSymbols(it.readText()).asSequence() }
                .toSet()
            val undeclared = generated.filterNot { symbol -> Regex("\\b${Regex.escape(symbol)}\\b").containsMatchIn(curated) }
            check(undeclared.isEmpty()) {
                "the $target declarations carry symbols the curated index.d.ts does not declare: $undeclared"
            }
            // The Wasm target's module is part of the evidence: Kotlin/Wasm empties a library
            // module (its exports are only realized when an application is linked), so the packaged
            // `.wasm` carries no entry point. Printing its size keeps that visible in every build.
            val wasmModule = directory.listFiles().orEmpty().firstOrNull { it.extension == "wasm" }
            logger.lifecycle(
                "@kadre/host ($target): $moduleFile${wasmModule?.let { " (${it.length()} bytes of wasm)" }.orEmpty()}" +
                    " and index.d.ts; generated declarations declare $generated",
            )
        }
    }

/** The exported type names of one generated declaration file, without the compiler's own helpers. */
fun declarationSymbols(declaration: String): Set<String> {
    val ignored = setOf("Nullable", "KtSingleton", "constructor", "org")
    return Regex(
        """(?m)^\s*(?:export\s+)?(?:declare\s+)?(?:abstract\s+)?(?:class|interface|function|const|let|var|type|enum|namespace)\s+([A-Za-z_][A-Za-z0-9_$]*)""",
    ).findAll(declaration)
        .map { it.groupValues[1] }
        .filterNot { it in ignored || it.startsWith("$") }
        .toSet()
}

val contractTestRepository = rootProject.layout.buildDirectory.dir("kadre-contract-repository")

val jsHostPackage = registerHostPackage("js", "jsBrowserProductionLibraryDistribution", "kadre-platform-web.js")
val wasmJsHostPackage = registerHostPackage(
    "wasmJs",
    "wasmJsBrowserProductionLibraryDistribution",
    "kadre-platform-web.mjs",
)

val jsHostPackageArchive by tasks.registering(Zip::class) {
    group = "build"
    description = "Archives the @kadre/host package of the js target for publication."
    from(jsHostPackage)
    archiveBaseName.set("web-host-package")
    archiveClassifier.set("js")
    destinationDirectory.set(layout.buildDirectory.dir("host-package-archives"))
}

val wasmJsHostPackageArchive by tasks.registering(Zip::class) {
    group = "build"
    description = "Archives the @kadre/host package of the wasmJs target for publication."
    from(wasmJsHostPackage)
    archiveBaseName.set("web-host-package")
    archiveClassifier.set("wasmJs")
    destinationDirectory.set(layout.buildDirectory.dir("host-package-archives"))
}

tasks.named("check") {
    dependsOn(jsHostPackage, wasmJsHostPackage)
}

publishing {
    publications {
        // The Kotlin publications of this project only carry klibs and poms, which is not a package
        // a JavaScript or TypeScript consumer can resolve. `web-host-package` publishes the
        // assembled `@kadre/host` packages instead, one classified zip per target:
        //   org.graphiks.kadre:web-host-package:<version>:js     -> web-host-package-<version>-js.zip
        //   org.graphiks.kadre:web-host-package:<version>:wasmJs -> web-host-package-<version>-wasmJs.zip
        create<MavenPublication>("webHostPackage") {
            artifactId = "web-host-package"
            artifact(jsHostPackageArchive) { classifier = "js"; extension = "zip" }
            artifact(wasmJsHostPackageArchive) { classifier = "wasmJs"; extension = "zip" }
        }
    }
    repositories {
        maven {
            name = "contractTest"
            url = contractTestRepository.get().asFile.toURI()
        }
    }
}

