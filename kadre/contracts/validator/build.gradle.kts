plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(25)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
val webContractIds = listOf("BCK-001", "INT-002", "INT-003", "INT-004")
val appKitContractIds = listOf(
    "APK-001", "APK-002", "APK-003", "APK-004", "APK-005", "APK-006",
    "APK-007", "APK-008", "APK-009", "APK-010", "APK-011", "APK-012",
    "APK-013", "APK-014", "APK-015", "APK-016", "APK-017", "APK-018",
    "APK-019",
)
val runtimeContractIds = listOf(
    "INP-001", "INP-002", "INP-003",
    "WIN-001", "WIN-002", "WIN-003", "WIN-004", "WIN-005", "WIN-006", "WIN-007", "WIN-008",
    "DSP-001", "RUN-007", "RUN-008", "INT-001",
)
val contractEvidenceGateIds = appKitContractIds + runtimeContractIds + webContractIds
check(contractEvidenceGateIds.distinct().size == contractEvidenceGateIds.size) {
    "each configured contract evidence gate must have exactly one explicit producer"
}
val activeContractIds = rootProject.file("kadre/contracts/registry/contracts.tsv")
    .readLines()
    .drop(1)
    .map { line -> line.split('\t') }
    .filter { columns -> columns[1] == "active" }
    .map { columns -> columns.first() }
    .toSet()
val activeAppKitContractIds = appKitContractIds.filter(activeContractIds::contains)
val activeRuntimeContractIds = runtimeContractIds.filter(activeContractIds::contains)
val contractEvidenceTarget = "jvm"
val gitSha = Regex("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")
val repositoryHead = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map(String::trim)
val contractEvidenceCommit = providers.gradleProperty("kadreContractCommit")
    .orElse(repositoryHead)
    .map { commit ->
        require(commit.matches(gitSha)) {
            "kadreContractCommit must be a 40- or 64-character Git SHA"
        }
        commit
    }

val validateContractRegistry by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates the New Kadre machine-readable contract registry."
    dependsOn("jvmMainClasses")
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("org.graphiks.kadre.contracts.ValidateContractRegistryKt")
    args(
        rootProject.file("kadre/contracts/registry/contracts.tsv").absolutePath,
        listOf(
            rootProject.file("kadre/runtime/contracts/evidence.tsv"),
            rootProject.file("kadre/backend/appkit/contracts/evidence.tsv"),
            rootProject.file("kadre/contracts/driver/web/contracts/evidence.tsv"),
        ).joinToString(separator = ",") { it.absolutePath },
        contractEvidenceGateIds.joinToString(separator = ","),
    )
    inputs.files(
        rootProject.file("kadre/runtime/contracts/evidence.tsv"),
        rootProject.file("kadre/backend/appkit/contracts/evidence.tsv"),
        rootProject.file("kadre/contracts/driver/web/contracts/evidence.tsv"),
    )
}

val appKitContractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val appKitContractMapping = rootProject.file("kadre/backend/appkit/contracts/evidence.tsv")
val appKitJUnitReports = rootProject.file("kadre/backend/appkit/build/test-results/jvmTest")
val appKitStandaloneLoopJUnitReports = rootProject.file("kadre/backend/appkit/build/test-results/appKitStandaloneLoopTest")
val appKitContractEvidenceDirectory = rootProject.file("kadre/backend/appkit/build/contract-evidence")
val appKitContractAdapter = "appkit-jvm"
val appKitContractEvidenceTasks = activeAppKitContractIds.map { contractId ->
    tasks.register<JavaExec>("generateAppKit${contractId.replace("-", "")}ContractEvidence") {
        group = "verification"
        description = "Generates and validates $contractId evidence from AppKit JUnit reports."
        dependsOn("jvmMainClasses", ":kadre:backend:appkit:appKitNativeTests")
        classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
        mainClass.set("org.graphiks.kadre.contracts.GenerateContractEvidenceKt")
        val output = appKitContractEvidenceDirectory.resolve("$contractId.json")
        args(
            appKitContractRegistry.absolutePath,
            appKitContractMapping.absolutePath,
            listOf(appKitJUnitReports, appKitStandaloneLoopJUnitReports).joinToString(
                separator = System.getProperty("path.separator"),
            ) {
                it.absolutePath
            },
            output.absolutePath,
            contractEvidenceCommit.get(),
            contractId,
            contractEvidenceTarget,
            appKitContractAdapter,
        )
        inputs.file(appKitContractRegistry)
        inputs.file(appKitContractMapping)
        inputs.dir(appKitJUnitReports)
        inputs.dir(appKitStandaloneLoopJUnitReports)
        inputs.property("contractCommit", contractEvidenceCommit)
        inputs.property("contractTarget", contractEvidenceTarget)
        inputs.property("contractAdapter", appKitContractAdapter)
        outputs.file(output)
    }
}

val validateAppKitContractEvidence by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates every active AppKit JVM contract evidence artifact."
    dependsOn(appKitContractEvidenceTasks)
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("org.graphiks.kadre.contracts.ValidateContractEvidenceKt")
    args(
        appKitContractRegistry.absolutePath,
        appKitContractMapping.absolutePath,
        contractEvidenceCommit.get(),
        contractEvidenceTarget,
        "junit",
        activeAppKitContractIds.joinToString(separator = ","),
        rootProject.file("kadre/backend/appkit/build").absolutePath,
        listOf(
            "test-results/jvmTest",
            "test-results/appKitStandaloneLoopTest",
        ).joinToString(separator = System.getProperty("path.separator")),
    )
    inputs.file(appKitContractRegistry)
    inputs.file(appKitContractMapping)
    inputs.dir(appKitContractEvidenceDirectory)
    inputs.property("contractCommit", contractEvidenceCommit)
    inputs.property("contractTarget", contractEvidenceTarget)
    inputs.property("contractExecutions", "junit")
    inputs.property("contractGateIds", activeAppKitContractIds)
    inputs.property(
        "junitReportRelativeDirectories",
        listOf("test-results/jvmTest", "test-results/appKitStandaloneLoopTest"),
    )
}

val generateAppKitContractEvidence by tasks.registering {
    group = "verification"
    description = "Generates and validates evidence for every active AppKit contract."
    dependsOn(validateAppKitContractEvidence)
    outputs.dir(appKitContractEvidenceDirectory)
}

val runtimeContractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val runtimeContractMapping = rootProject.file("kadre/runtime/contracts/evidence.tsv")
val runtimeJUnitReports = rootProject.file("kadre/runtime/build/test-results/jvmTest")
val runtimeContractEvidenceDirectory = rootProject.file("kadre/runtime/build/contract-evidence")
val runtimeContractAdapter = "runtime-jvm"
val runtimeContractEvidenceTasks = activeRuntimeContractIds.map { contractId ->
    tasks.register<JavaExec>("generateRuntime${contractId.replace("-", "")}ContractEvidence") {
        group = "verification"
        description = "Generates and validates $contractId evidence from runtime JUnit reports."
        dependsOn("jvmMainClasses", ":kadre:runtime:jvmTest")
        classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
        mainClass.set("org.graphiks.kadre.contracts.GenerateContractEvidenceKt")
        val output = runtimeContractEvidenceDirectory.resolve("$contractId.json")
        args(
            runtimeContractRegistry.absolutePath,
            runtimeContractMapping.absolutePath,
            runtimeJUnitReports.absolutePath,
            output.absolutePath,
            contractEvidenceCommit.get(),
            contractId,
            contractEvidenceTarget,
            runtimeContractAdapter,
        )
        inputs.file(runtimeContractRegistry)
        inputs.file(runtimeContractMapping)
        inputs.dir(runtimeJUnitReports)
        inputs.property("contractCommit", contractEvidenceCommit)
        inputs.property("contractTarget", contractEvidenceTarget)
        inputs.property("contractAdapter", runtimeContractAdapter)
        outputs.file(output)
    }
}

val validateRuntimeContractEvidence by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates every active runtime JVM contract evidence artifact."
    dependsOn(runtimeContractEvidenceTasks)
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("org.graphiks.kadre.contracts.ValidateContractEvidenceKt")
    args(
        runtimeContractRegistry.absolutePath,
        runtimeContractMapping.absolutePath,
        contractEvidenceCommit.get(),
        contractEvidenceTarget,
        "junit",
        activeRuntimeContractIds.joinToString(separator = ","),
        rootProject.file("kadre/runtime/build").absolutePath,
        "test-results/jvmTest",
    )
    inputs.file(runtimeContractRegistry)
    inputs.file(runtimeContractMapping)
    inputs.dir(runtimeContractEvidenceDirectory)
    inputs.property("contractCommit", contractEvidenceCommit)
    inputs.property("contractTarget", contractEvidenceTarget)
    inputs.property("contractExecutions", "junit")
    inputs.property("contractGateIds", activeRuntimeContractIds)
    inputs.property("junitReportRelativeDirectories", listOf("test-results/jvmTest"))
}

val generateRuntimeContractEvidence by tasks.registering {
    group = "verification"
    description = "Generates and validates evidence for every active runtime contract."
    dependsOn(validateRuntimeContractEvidence)
    outputs.dir(runtimeContractEvidenceDirectory)
}

val browserContractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val browserContractMappings = listOf(
    rootProject.file("kadre/runtime/contracts/evidence.tsv"),
    rootProject.file("kadre/backend/appkit/contracts/evidence.tsv"),
    rootProject.file("kadre/contracts/driver/web/contracts/evidence.tsv"),
)
val browserContractEngines = providers.gradleProperty("kadreBrowserEngines")
    .orElse("chromium")
    .map { configuredEngines ->
        configuredEngines.split(',')
            .map(String::trim)
            .also { engines ->
                require(engines.isNotEmpty() && engines.none(String::isBlank)) {
                    "kadreBrowserEngines must contain one or more comma-separated browser engines"
                }
            }
            .distinct()
            .joinToString(",")
    }
val browserContractEvidenceTasks = listOf("js", "wasmJs").map { target ->
    tasks.register<JavaExec>("validate${target.replaceFirstChar(Char::uppercase)}BrowserContractEvidence") {
        group = "verification"
        description = "Validates every active $target browser contract evidence artifact."
        dependsOn("jvmMainClasses")
        mustRunAfter(":kadre:contracts:driver:web:${target}BrowserSmoke")
        classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
        mainClass.set("org.graphiks.kadre.contracts.ValidateContractEvidenceKt")
        val artifactDirectory = rootProject.file("kadre/contracts/driver/web/build/contract-evidence/$target")
        args(
            browserContractRegistry.absolutePath,
            browserContractMappings.joinToString(separator = ",") { it.absolutePath },
            contractEvidenceCommit.get(),
            target,
            browserContractEngines.get(),
            webContractIds.joinToString(separator = ","),
            artifactDirectory.absolutePath,
            "test-results/browser/{engine}",
        )
        inputs.file(browserContractRegistry)
        inputs.files(browserContractMappings)
        inputs.files(fileTree(artifactDirectory))
        inputs.property("contractCommit", contractEvidenceCommit)
        inputs.property("contractTarget", target)
        inputs.property("contractExecutions", browserContractEngines)
        inputs.property("contractGateIds", webContractIds)
        inputs.property("junitReportRelativeDirectories", listOf("test-results/browser/{engine}"))
    }
}

tasks.named("check") {
    dependsOn(validateContractRegistry)
    dependsOn(generateRuntimeContractEvidence)
    dependsOn(browserContractEvidenceTasks)
}
