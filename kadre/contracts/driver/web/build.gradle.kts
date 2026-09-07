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

fun registerBrowserSmoke(target: String, distributionTask: String) = tasks.register<Exec>("${target}BrowserSmoke") {
    group = "verification"
    description = "Runs the $target public Web attach smoke in Chromium."
    dependsOn(browserSmokeRunnerTest, installPlaywright, distributionTask)
    workingDir(projectDir)
    commandLine(
        "node",
        "playwright/run-browser-smoke.mjs",
        "--target=$target",
        "--distribution=${layout.buildDirectory.dir("dist/$target/productionExecutable").get().asFile.absolutePath}",
        "--evidence=${browserSmokeOutput.get().dir(target).asFile.absolutePath}",
    )
    inputs.files(playwrightPackage, playwrightLock)
    inputs.dir(layout.projectDirectory.dir("playwright"))
    inputs.dir(layout.buildDirectory.dir("dist/$target/productionExecutable"))
    outputs.dir(browserSmokeOutput.map { it.dir(target) })
}

registerBrowserSmoke(target = "js", distributionTask = "jsBrowserDistribution")
registerBrowserSmoke(target = "wasmJs", distributionTask = "wasmJsBrowserDistribution")
