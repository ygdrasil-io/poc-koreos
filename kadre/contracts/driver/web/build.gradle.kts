@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

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
/**
 * The commit the canonical evidence of this project belongs to.
 *
 * `kadre/contracts/validator/build.gradle.kts` derives the value it validates with the same rule — the
 * `kadreContractCommit` override, else the repository HEAD — so the two can only agree. It is an
 * input of the smoke because a commit-pinned document is stale by definition once HEAD moves.
 */
val contractEvidenceCommit = providers.gradleProperty("kadreContractCommit")
    .orElse(
        providers.exec {
            workingDir(rootProject.projectDir)
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.map(String::trim),
    )
    .map { commit ->
        require(commit.matches(Regex("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}"))) {
            "kadreContractCommit must be a 40- or 64-character Git SHA"
        }
        commit
    }

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
        "--commit=${contractEvidenceCommit.get()}",
    )
    inputs.files(playwrightPackage, playwrightLock, contractRegistry, contractMapping)
    inputs.property("contractEvidenceCommit", contractEvidenceCommit)
    inputs.dir(layout.projectDirectory.dir("playwright"))
    inputs.dir(layout.buildDirectory.dir("dist/$target/productionExecutable"))
    inputs.dir(layout.buildDirectory.dir("dist/$target/host"))
    outputs.dir(browserSmokeOutput.map { it.dir(target) })
}

/**
 * The `@kadre/host` root the TypeScript scenario is served from.
 *
 * The package files are the target's UNPACKED PUBLISHED ARCHIVE — the same tree `kadre/consumers/typescript`
 * unpacked from the contract test repository and type-checked its consumer against — plus that build's
 * browser consumer entry. `:kadre:emitTypeScriptBrowserConsumer` is the single nested invocation that
 * produces both (Gradle refuses a second `GradleBuild` on the same directory), and it publishes the
 * archives first, so nothing here references `kadre/platform/web`'s build-tree package directory: the
 * executed consumer IS the published artifact.
 *
 * The task also writes `provenance.json` beside the served files — the archive's Maven coordinates,
 * the `.sha256` sidecar the publication wrote for the zip, and the SHA-256 of every zip entry — which
 * the TypeScript spec verifies against the bytes the fixture actually serves.
 */
fun registerHostPage(target: String) = tasks.register<Sync>("${target}HostPage") {
    group = "build"
    description = "Assembles the served @kadre/host root with the browser consumer for $target."
    dependsOn(":kadre:emitTypeScriptBrowserConsumer")
    from(unpackedHostPackages.dir(target))
    from(typescriptConsumerOutput.dir(target)) { include("consumer.js") }
    into(layout.buildDirectory.dir("dist/$target/host"))
    doLast {
        val directory = layout.buildDirectory.dir("dist/$target/host").get().asFile
        listOf("index.mjs", "index.d.ts", "consumer.js").forEach { required ->
            check(directory.resolve(required).isFile) { "the $target host root must serve $required" }
        }
        writeHostPackageProvenance(target, directory)
    }
}

val typescriptConsumerOutput = rootProject.layout.projectDirectory.dir("kadre/consumers/typescript/build/browser-consumer")

/** Where `kadre/consumers/typescript` unpacked both targets' published `@kadre/host` archives. */
val unpackedHostPackages = rootProject.layout.projectDirectory.dir("kadre/consumers/typescript/build/kadre-host-package")

/** The contract test repository `:kadre:publishContractArtifacts` publishes the archives to. */
val contractTestRepository = rootProject.layout.buildDirectory.dir("kadre-contract-repository")

/** The published coordinates of the `@kadre/host` archives, chosen by `kadre/platform/web`. */
val hostPackageGroup = "org.graphiks.kadre"
val hostPackageArtifact = "web-host-package"

/** The lowercase hex SHA-256 of `bytes`. */
fun sha256Of(bytes: ByteArray): String = buildString {
    for (byte in MessageDigest.getInstance("SHA-256").digest(bytes)) append("%02x".format(byte))
}

/** A JSON string literal; the recorded names and digests are plain ASCII, the escaping is defensive. */
fun jsonString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/**
 * Writes the served package's provenance, read from the publication itself — never from the served
 * tree, so a tree mutated after publication cannot launder its own record.
 */
fun writeHostPackageProvenance(target: String, servedRoot: File) {
    val version = project.version.toString()
    val artifactDirectory = contractTestRepository.get().asFile
        .resolve(hostPackageGroup.replace('.', '/'))
        .resolve(hostPackageArtifact)
        .resolve(version)
    val archive = artifactDirectory.resolve("$hostPackageArtifact-$version-$target.zip")
    check(archive.isFile) { "the contract repository must publish $archive" }
    val digest = sha256Of(archive.readBytes())
    val sidecar = artifactDirectory.resolve("${archive.name}.sha256")
    check(sidecar.isFile) { "the contract repository must publish $sidecar" }
    check(sidecar.readText().trim() == digest) {
        "the computed SHA-256 of ${archive.name} disagrees with the published ${sidecar.name}"
    }
    val entries = ZipFile(archive).use { zip ->
        zip.entries().asSequence()
            .filterNot { it.isDirectory }
            .sortedBy { it.name }
            .map { entry -> jsonString(entry.name) to jsonString(sha256Of(zip.getInputStream(entry).readBytes())) }
            .toList()
    }
    check(entries.isNotEmpty()) { "${archive.name} carries no files" }
    servedRoot.resolve("provenance.json").writeText(
        buildString {
            append("{\n")
            append("  \"group\": ${jsonString(hostPackageGroup)},\n")
            append("  \"artifact\": ${jsonString(hostPackageArtifact)},\n")
            append("  \"version\": ${jsonString(version)},\n")
            append("  \"classifier\": ${jsonString(target)},\n")
            append("  \"archive\": ${jsonString(archive.name)},\n")
            append("  \"archiveSha256\": ${jsonString(digest)},\n")
            append("  \"files\": {\n")
            entries.forEachIndexed { index, (name, sha) ->
                append("    $name: $sha").append(if (index == entries.lastIndex) "\n" else ",\n")
            }
            append("  }\n")
            append("}\n")
        },
    )
}

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
registerHostPage(target = "js")
registerHostPage(target = "wasmJs")

/**
 * The nightly-engine smokes of the roadmap's section 3.8: same runner, same distributions, one
 * engine and its recorded contract exclusions.
 *
 * The engine reaches the runner as one more `--name=value` argument (`--engine=<name>`, which the
 * launch turns into that engine's `--project` selection — firefox-js vs firefox-wasm by target),
 * and the engine's exclusion file (`contracts/engine-exclusions/<engine>.tsv`) reaches the evidence
 * producer, which subtracts it from the mapping the run must cover; the validator's per-engine
 * tasks subtract the same file from their expected contract set. The evidence root carries the
 * engine segment, so the four tasks' `outputs.dir` never overlap the Chromium smokes' — or each
 * other's.
 */
fun registerEngineBrowserSmoke(engine: String, target: String, distributionTask: String, hostPageTask: String) {
    val engineName = engine.replaceFirstChar(Char::uppercase)
    val targetName = target.replaceFirstChar(Char::uppercase)
    val exclusionsFile = layout.projectDirectory.file("contracts/engine-exclusions/$engine.tsv").asFile
    val engineEvidence = browserSmokeOutput.map { it.dir("$engine/$target") }
    tasks.register<Exec>("${engine}${targetName}BrowserSmoke") {
        group = "verification"
        description = "Runs the $target public Web attach smoke in $engineName with its recorded contract exclusions."
        dependsOn(browserSmokeRunnerTest, installPlaywright, distributionTask, hostPageTask)
        workingDir(projectDir)
        commandLine(
            "node",
            "playwright/run-browser-smoke.mjs",
            "--target=$target",
            "--engine=$engine",
            "--distribution=${layout.buildDirectory.dir("dist/$target/productionExecutable").get().asFile.absolutePath}",
            "--evidence=${engineEvidence.get().asFile.absolutePath}",
            "--consumer=${layout.buildDirectory.dir("dist/$target/host").get().asFile.absolutePath}",
            "--contracts=${contractRegistry.absolutePath}",
            "--mapping=${contractMapping.asFile.absolutePath}",
            "--engine-exclusions=${exclusionsFile.absolutePath}",
            "--commit=${contractEvidenceCommit.get()}",
        )
        inputs.files(playwrightPackage, playwrightLock, contractRegistry, contractMapping, exclusionsFile)
        inputs.property("contractEvidenceCommit", contractEvidenceCommit)
        inputs.property("browserEngine", engine)
        inputs.dir(layout.projectDirectory.dir("playwright"))
        inputs.dir(layout.buildDirectory.dir("dist/$target/productionExecutable"))
        inputs.dir(layout.buildDirectory.dir("dist/$target/host"))
        outputs.dir(engineEvidence)
    }
}

registerEngineBrowserSmoke("firefox", "js", "jsBrowserDistribution", ":kadre:contracts:driver:web:jsHostPage")
registerEngineBrowserSmoke("firefox", "wasmJs", "wasmJsBrowserDistribution", ":kadre:contracts:driver:web:wasmJsHostPage")
registerEngineBrowserSmoke("webkit", "js", "jsBrowserDistribution", ":kadre:contracts:driver:web:jsHostPage")
registerEngineBrowserSmoke("webkit", "wasmJs", "wasmJsBrowserDistribution", ":kadre:contracts:driver:web:wasmJsHostPage")
