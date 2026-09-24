@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

group = "org.graphiks.kadre.contracts.driver"

kotlin {
    applyDefaultHierarchyTemplate()
    js {
        outputModuleName.set("kadre-contract-driver-web-js")
        browser {
            commonWebpackConfig {
                outputFileName = "kadre-web-phase0.js"
            }
        }
        binaries.executable()
    }
    wasmJs {
        outputModuleName.set("kadre-contract-driver-web-wasm")
        browser {
            commonWebpackConfig {
                outputFileName = "kadre-web-phase0-wasm.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kadre:platform:web"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

val npmCommand = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
val playwrightPackage = layout.projectDirectory.file("package.json")
val playwrightLock = layout.projectDirectory.file("package-lock.json")
val browserSmokeOutput = layout.buildDirectory.dir("contract-evidence")
/** The registry and the mapping that decide which canonical JSON documents a smoke writes. */
val contractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val contractMapping = layout.projectDirectory.file("contracts/evidence.tsv")

val installPlaywright by tasks.registering(Exec::class) {
    group = "verification"
    description = "Installs the locked Playwright driver dependencies."
    workingDir(projectDir)
    commandLine(npmCommand, "ci", "--ignore-scripts")
    inputs.files(playwrightPackage, playwrightLock)
    outputs.dir(layout.projectDirectory.dir("node_modules"))
}

val browserSmokeRunnerTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Tests bounded browser smoke termination and finalization."
    dependsOn(installPlaywright)
    workingDir(projectDir)
    commandLine("node", "--test", "playwright/run-browser-smoke.test.mjs")
    inputs.files(
        playwrightPackage,
        playwrightLock,
        layout.projectDirectory.file("playwright/browser-smoke-launch.mjs"),
        layout.projectDirectory.file("playwright/run-browser-smoke.mjs"),
        layout.projectDirectory.file("playwright/run-browser-smoke.test.mjs"),
    )
}

fun registerBrowserSmoke(target: String, distributionTask: String, hostPageTask: String) = tasks.register<Exec>("${target}BrowserSmoke") {
    group = "verification"
    description = "Runs the $target public Web attach smoke in Chromium."
    dependsOn(browserSmokeRunnerTest, installPlaywright, distributionTask, hostPageTask)
    workingDir(projectDir)
    commandLine(
        "node",
        "playwright/run-browser-smoke.mjs",
        "--target=$target",
        "--distribution=${layout.buildDirectory.dir("dist/$target/productionExecutable").get().asFile.absolutePath}",
        "--evidence=${browserSmokeOutput.get().dir(target).asFile.absolutePath}",
        "--consumer=${layout.buildDirectory.dir("dist/$target/host").get().asFile.absolutePath}",
        "--contracts=${contractRegistry.absolutePath}",
        "--mapping=${contractMapping.asFile.absolutePath}",
    )
    inputs.files(playwrightPackage, playwrightLock, contractRegistry, contractMapping)
    inputs.dir(layout.projectDirectory.dir("playwright"))
    inputs.dir(layout.buildDirectory.dir("dist/$target/productionExecutable"))
    inputs.dir(layout.buildDirectory.dir("dist/$target/host"))
    outputs.dir(browserSmokeOutput.map { it.dir(target) })
}

/**
 * The `@kadre/host` root the TypeScript scenario is served from.
 *
 * It is the published package of this target — the same directory the packaging task of
 * `kadre/platform/web` assembles — plus the browser entry of the TypeScript consumer compiled by
 * `kadre/consumers/typescript` against that target's package. Both archives carry the same curated
 * declaration, and the consumer leaves the bare specifier for the page's import map to resolve.
 */
fun registerHostPage(target: String, packageTask: String) = tasks.register<Sync>("${target}HostPage") {
    group = "build"
    description = "Assembles the served @kadre/host root with the browser consumer for $target."
    dependsOn(packageTask, ":kadre:emitTypeScriptBrowserConsumer")
    from(project(":kadre:platform:web").layout.buildDirectory.dir("dist/$target/host-package"))
    from(typescriptConsumerOutput.dir(target)) { include("consumer.js") }
    into(layout.buildDirectory.dir("dist/$target/host"))
    doLast {
        val directory = layout.buildDirectory.dir("dist/$target/host").get().asFile
        listOf("index.mjs", "index.d.ts", "consumer.js").forEach { required ->
            check(directory.resolve(required).isFile) { "the $target host root must serve $required" }
        }
    }
}

val typescriptConsumerOutput = rootProject.layout.projectDirectory.dir("kadre/consumers/typescript/build/browser-consumer")

registerBrowserSmoke(
    target = "js",
    distributionTask = "jsBrowserDistribution",
    hostPageTask = ":kadre:contracts:driver:web:jsHostPage",
)
registerBrowserSmoke(
    target = "wasmJs",
    distributionTask = "wasmJsBrowserDistribution",
    hostPageTask = ":kadre:contracts:driver:web:wasmJsHostPage",
)
registerHostPage(target = "js", packageTask = ":kadre:platform:web:jsHostPackage")
registerHostPage(target = "wasmJs", packageTask = ":kadre:platform:web:wasmJsHostPackage")
