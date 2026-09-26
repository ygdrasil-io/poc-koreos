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
 * Sources:
 * - the compiled Kotlin library output (`compileSync/<target>/main/productionLibrary/kotlin`), which
 *   is where the JavaScript-bearing module lives. The post-processed `dist/<target>/productionLibrary`
 *   copy is not used: for the Wasm target the `wasm-opt` step reduces a library module to an empty
 *   one, so that copy ships neither code nor exports;
 * - `types/kadre-host-<target>.mjs`, the hand-written shim presenting the promised `KadreWeb`
 *   surface over the module's function bindings, shipped as `index.mjs`;
 * - `types/kadre-host.d.ts`, the curated contract of `kadre/INTEROP-EXPORTS.md` section 6, shipped as
 *   `index.d.ts`.
 *
 * The task proves three things before the package can be published: the generated declarations
 * export exactly the bindings the shim imports (no binding the shim relies on is missing, and no
 * unexpected symbol leaks), every name the shim exports is declared by the curated contract, and the
 * Kotlin module bundle really carries the bound names.
 */
fun registerHostPackage(
    target: String,
    compileTask: String,
    moduleFile: String,
    shimFileName: String,
) = tasks.register<Sync>("${target}HostPackage") {
    group = "build"
    description = "Assembles the @kadre/host package for the $target target."
    dependsOn(compileTask)
    inputs.file(layout.projectDirectory.file("types/kadre-host.d.ts"))
    inputs.file(layout.projectDirectory.file("types/$shimFileName"))
    // `optimized/` holds the wasm-opt output, which is empty for a library module.
    from(layout.buildDirectory.dir("compileSync/$target/main/productionLibrary/kotlin")) {
        exclude("optimized/**")
    }
    from(layout.projectDirectory.file("types/$shimFileName")) { rename { "index.mjs" } }
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
              "main": "index.mjs",
              "module": "index.mjs",
              "types": "index.d.ts",
              "private": false
            }
            """.trimIndent() + "\n",
        )
        listOf("index.mjs", "index.d.ts", moduleFile).forEach { required ->
            check(directory.resolve(required).isFile) { "the $target package must ship $required" }
        }

        val shim = directory.resolve("index.mjs").readText()
        val bound = shimBindingNames(shim)
        val declared = directory.walkTopDown()
            .filter { it.isFile && it.name != "index.d.ts" }
            .filter { it.name.endsWith(".d.ts") || it.name.endsWith(".d.mts") }
            .flatMap { declarationSymbols(it.readText()).asSequence() }
            .filterNot { it in shimPublicNames(shim) }
            .toSet()
        check(bound == declared) {
            "the $target shim must bind exactly the generated exports: missing ${declared - bound}, unexpected ${bound - declared}"
        }
        val bundle = directory.resolve(moduleFile).readText()
        val unbound = bound.filterNot { bundle.contains(it) }
        check(unbound.isEmpty()) { "the $target module bundle does not carry: $unbound" }
        val curated = directory.resolve("index.d.ts").readText()
        val undeclared = shimPublicNames(shim).filterNot { name -> Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(curated) }
        check(undeclared.isEmpty()) { "the shim exports names the curated index.d.ts does not declare: $undeclared" }

        val wasmModule = directory.listFiles().orEmpty().firstOrNull { it.extension == "wasm" }
        logger.lifecycle(
            "@kadre/host ($target): index.mjs, index.d.ts and $moduleFile" +
                wasmModule?.let { " (${it.length()} bytes of wasm)" }.orEmpty() +
                "; bound bindings $bound",
        )
    }
}

/** The Kotlin bindings the shim loads, read from its import or destructuring block. */
fun shimBindingNames(shim: String): Set<String> {
    val block = shim.substringBefore("//<shim-body>")
    val braces = block.substringAfter('{').substringBefore('}')
    return braces.split(',')
        .map { it.trim().substringBefore(' ').trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

/** The names the shim publishes to its consumer. */
fun shimPublicNames(shim: String): Set<String> = Regex("""(?m)^export\s+(?:class|const|function)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    .findAll(shim)
    .map { it.groupValues[1] }
    .toSet()

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

/** The two shims must present the same behaviour: only their loading block may differ. */
fun verifyShimBodiesAreShared(): Unit {
    val js = layout.projectDirectory.file("types/kadre-host-js.mjs").asFile.readText()
    val wasm = layout.projectDirectory.file("types/kadre-host-wasm.mjs").asFile.readText()
    val marker = "//<shim-body>"
    check(js.substringAfter(marker) == wasm.substringAfter(marker)) {
        "the two @kadre/host shims must share their body from $marker on"
    }
}

val contractTestRepository = rootProject.layout.buildDirectory.dir("kadre-contract-repository")

val jsHostPackage = registerHostPackage(
    target = "js",
    compileTask = "compileProductionLibraryKotlinJs",
    moduleFile = "kadre-platform-web.js",
    shimFileName = "kadre-host-js.mjs",
)
val wasmJsHostPackage = registerHostPackage(
    target = "wasmJs",
    compileTask = "compileProductionLibraryKotlinWasmJs",
    moduleFile = "kadre-platform-web.mjs",
    shimFileName = "kadre-host-wasm.mjs",
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
    doFirst { verifyShimBodiesAreShared() }
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

