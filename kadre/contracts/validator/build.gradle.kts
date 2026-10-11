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
val webContractIds = listOf("BCK-001", "BCK-002", "BCK-003", "BCK-004", "BCK-005", "BCK-006", "BCK-007", "BCK-008", "BCK-009", "BCK-010", "BCK-011", "INT-002", "INT-003", "INT-004")
val uikitContractIds = listOf("BCK-012", "BCK-013", "BCK-014", "WIN-009")
val appKitContractIds = listOf(
    "APK-001", "APK-002", "APK-003", "APK-004", "APK-005", "APK-006",
    "APK-007", "APK-008", "APK-009", "APK-010", "APK-011", "APK-012",
    "APK-013", "APK-014", "APK-015", "APK-016", "APK-017", "APK-018",
    "APK-019", "APK-020",
)
val runtimeContractIds = listOf(
    "INP-001", "INP-002", "INP-003",
    "WIN-001", "WIN-002", "WIN-003", "WIN-004", "WIN-005", "WIN-006", "WIN-007", "WIN-008",
    "DSP-001", "RUN-007", "RUN-008", "INT-001", "CAP-001",
)
val contractEvidenceGateIds = appKitContractIds + runtimeContractIds + webContractIds + uikitContractIds
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
            rootProject.file("kadre/contracts/driver/uikit/contracts/evidence.tsv"),
        ).joinToString(separator = ",") { it.absolutePath },
        contractEvidenceGateIds.joinToString(separator = ","),
    )
    inputs.files(
        rootProject.file("kadre/runtime/contracts/evidence.tsv"),
        rootProject.file("kadre/backend/appkit/contracts/evidence.tsv"),
        rootProject.file("kadre/contracts/driver/web/contracts/evidence.tsv"),
        rootProject.file("kadre/contracts/driver/uikit/contracts/evidence.tsv"),
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
        // The browser smoke is the only producer of these artifacts, exactly as the AppKit and runtime
        // evidence tasks are produced by their own generators: the aggregate gate runs Chromium itself
        // rather than validating documents a previous invocation happened to leave behind.
        dependsOn(":kadre:contracts:driver:web:${target}BrowserSmoke")
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

// UIKit driver contracts (BCK-012 phase 0 ; BCK-013/BCK-014/WIN-009 phase 1) : preuve O3
// produite par xcodebuild sur simulateurs (:kadre:contracts:driver:uikit:simulatorTests),
// exportée en JUnit par tools/xctest_to_junit.py. Miroir du bloc AppKit ci-dessus, par target
// famille. BCK-014 (multi-scène iPadOS) n'exige QUE iosSimulatorArm64 : les tâches générées et
// les gate ids de validation sont filtrés par les requiredTargets du registre (colonne 8) — un
// contrat actif hors cible ferait échouer ValidateContractEvidence (« target is not required »).
// macOS-only : aucune de ces tâches n'est câblée dans `check` — la gate obligatoire est
// le script hors Gradle de Task 7, comme le smoke AppKit.
val uikitContractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val uikitContractMapping = rootProject.file("kadre/contracts/driver/uikit/contracts/evidence.tsv")
val uikitContractAdapter = "uikit-driver"
val activeUikitContractIds = uikitContractIds.filter(activeContractIds::contains)
val uikitRequiredTargetsByContractId = uikitContractRegistry
    .readLines()
    .drop(1)
    .map { line -> line.split('\t') }
    .filter { columns -> columns.first() in uikitContractIds }
    .associate { columns ->
        columns.first() to columns[7].split(',').map(String::trim).filter(String::isNotBlank)
    }
val uikitTargets = listOf(
    // (target, tâche d'export JUnit du driver qui produit le répertoire ci-dessous)
    "iosSimulatorArm64" to "iosSimulatorJunitExport",
    "tvosSimulatorArm64" to "tvosSimulatorJunitExport",
)
val uikitContractEvidenceTasks = activeUikitContractIds.flatMap { id ->
    uikitTargets.mapNotNull { (target, junitExportTask) ->
        if (target !in uikitRequiredTargetsByContractId[id].orEmpty()) return@mapNotNull null
        val capitalized = target.replaceFirstChar { it.uppercase() }
        val artifactDirectory = rootProject.file("kadre/contracts/driver/uikit/build/contract-evidence/$target")
        // ValidateContractEvidence résout les répertoires JUnit relativement au répertoire
        // d'artefacts — ils vivent donc sous build/contract-evidence/<target>/test-results.
        val junitDirectory = artifactDirectory.resolve("test-results/$target")
        tasks.register<JavaExec>("generateUikit${id.replace("-", "")}${capitalized}ContractEvidence") {
            group = "verification"
            description = "Generates and validates $id evidence from the $target UIKit simulator JUnit reports."
            dependsOn("jvmMainClasses", ":kadre:contracts:driver:uikit:$junitExportTask")
            classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
            mainClass.set("org.graphiks.kadre.contracts.GenerateContractEvidenceKt")
            val output = artifactDirectory.resolve("contract-evidence/$id.json")
            args(
                uikitContractRegistry.absolutePath,
                uikitContractMapping.absolutePath,
                junitDirectory.absolutePath,
                output.absolutePath,
                contractEvidenceCommit.get(),
                id,
                target,
                uikitContractAdapter,
            )
            inputs.file(uikitContractRegistry)
            inputs.file(uikitContractMapping)
            inputs.dir(junitDirectory)
            inputs.property("contractCommit", contractEvidenceCommit)
            inputs.property("contractTarget", target)
            inputs.property("contractAdapter", uikitContractAdapter)
            outputs.file(output)
        }
    }
}
val uikitContractValidateTasks = uikitTargets.map { (target, _) ->
    val capitalized = target.replaceFirstChar { it.uppercase() }
    val artifactDirectory = rootProject.file("kadre/contracts/driver/uikit/build/contract-evidence/$target")
    val targetGateIds = activeUikitContractIds.filter { id ->
        target in uikitRequiredTargetsByContractId[id].orEmpty()
    }
    tasks.register<JavaExec>("validate${capitalized}UikitContractEvidence") {
        group = "verification"
        description = "Validates every active UIKit $target contract evidence artifact."
        dependsOn(uikitContractEvidenceTasks.filter { taskProvider ->
            taskProvider.name.endsWith("${capitalized}ContractEvidence")
        })
        classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
        mainClass.set("org.graphiks.kadre.contracts.ValidateContractEvidenceKt")
        args(
            uikitContractRegistry.absolutePath,
            uikitContractMapping.absolutePath,
            contractEvidenceCommit.get(),
            target,
            "junit",
            targetGateIds.joinToString(separator = ","),
            artifactDirectory.absolutePath,
            "test-results/$target",
        )
        inputs.file(uikitContractRegistry)
        inputs.file(uikitContractMapping)
        inputs.dir(artifactDirectory)
        inputs.property("contractCommit", contractEvidenceCommit)
        inputs.property("contractTarget", target)
        inputs.property("contractExecutions", "junit")
        inputs.property("contractGateIds", targetGateIds)
        inputs.property("junitReportRelativeDirectories", listOf("test-results/$target"))
    }
}

val generateUikitContractEvidence by tasks.registering {
    group = "verification"
    description = "Generates and validates evidence for every active UIKit simulator contract."
    dependsOn(uikitContractEvidenceTasks)
    dependsOn(uikitContractValidateTasks)
}

val androidContractIds = listOf("AND-001", "AND-002", "AND-003")
val androidContractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val androidContractMappings = listOf(
    rootProject.file("kadre/contracts/driver/android/contracts/evidence.tsv"),
)
// Racine des artefacts du producteur : en exécution JUnit le validateur lit l'artefact à
// `<racine>/contract-evidence/<contractId>.json` (la copie de gate écrite par le driver Android)
// et le JUnit associé relativement à la même racine.
val androidContractEvidenceDirectory = rootProject.file("kadre/contracts/driver/android/build")
val androidEnginesFile = rootProject.layout.projectDirectory
    .file("kadre/contracts/driver/android/build/contract-evidence/android/engines.txt")
// Lecture paresseuse : la liste des moteurs n'existe qu'après un smoke Android sur un émulateur
// démarré — la configuration ne doit pas échouer en son absence (le doFirst du gate échoue
// alors avec le message actionnable). Les moteurs alimentent des répertoires JUnit CONCRETS :
// en exécution JUnit, le validateur rejette le placeholder {engine} (réservé au mode browser).
val androidContractEngines = providers.fileContents(androidEnginesFile).asText
    .map { engines ->
        engines.split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .also { parsedEngines ->
                require(parsedEngines.isNotEmpty()) {
                    "Android evidence engines file must contain one or more comma-separated engines"
                }
            }
            .distinct()
    }
val androidJunitReportRelativeDirectories = androidContractEngines.map { engines ->
    engines.joinToString(separator = System.getProperty("path.separator")) { engine ->
        "test-results/android/$engine"
    }
}
val validateAndroidContractEvidence by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates every active Android contract evidence artifact."
    dependsOn("jvmMainClasses")
    // Le smoke Android est le seul producteur de ces artefacts : dependsOn, jamais mustRunAfter
    // (piège des phases web — un gate qui valide des artefacts périmés n'est pas un gate).
    dependsOn(":kadre:contracts:driver:android:androidDeviceSmoke")
    doFirst {
        require(androidEnginesFile.asFile.exists()) {
            "No Android evidence engines file — run :kadre:contracts:driver:android:androidDeviceSmoke on a booted emulator first"
        }
    }
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("org.graphiks.kadre.contracts.ValidateContractEvidenceKt")
    args(
        androidContractRegistry.absolutePath,
        androidContractMappings.joinToString(separator = ",") { it.absolutePath },
        contractEvidenceCommit.get(),
        "android",
        "junit",
        androidContractIds.joinToString(separator = ","),
        androidContractEvidenceDirectory.absolutePath,
    )
    // Provider<String> évalué à l'exécution, après le doFirst — moteurs réels du device.
    // Un `args(...)` direct stringifierait le Provider (pas d'unpacking) ; le
    // CommandLineArgumentProvider est le mécanisme paresseux canonique de JavaExec.
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(androidJunitReportRelativeDirectories.get())
        },
    )
    inputs.file(androidContractRegistry)
    inputs.files(androidContractMappings)
    inputs.dir(rootProject.file("kadre/contracts/driver/android/build/contract-evidence"))
    inputs.property("contractCommit", contractEvidenceCommit)
    inputs.property("contractTarget", "android")
    inputs.property("contractExecutions", "junit")
    inputs.property("contractGateIds", androidContractIds)
}

// Aggregate Android : le branchement CI (job android-contracts) est la phase 10.
// `:kadre:check` ne dépend PAS de ce task — pas d'émulateur sur les runners actuels ;
// l'aggregate exige un émulateur démarré (le smoke y relance l'instrumentation consumer).
tasks.register("androidContractsCheck") {
    group = "verification"
    description = "Registry + Android contract evidence gate (requires a booted emulator)."
    dependsOn(validateContractRegistry)
    dependsOn(validateAndroidContractEvidence)
}

tasks.named("check") {
    dependsOn(validateContractRegistry)
    dependsOn(generateRuntimeContractEvidence)
    dependsOn(browserContractEvidenceTasks)
}

//
// The nightly-engine validation invocations: one per engine of the roadmap's section 3.8 beyond the
// PR gate's Chromium. Each subtracts the engine's recorded exclusion file
// (`kadre/contracts/driver/web/contracts/engine-exclusions/<engine>.tsv`) from the expected contract
// set — the same data the engine's smoke feeds its evidence producer — and validates against that
// engine's own evidence root. The PR path above is untouched: `check` runs Chromium with no
// exclusions and every active web contract expected; these tasks are the nightly workflow's to
// invoke.
//

/** The nightly engines' exclusion files: `contractId[tab]target(js|wasmJs|*)[tab]cause`, `#` comments. */
val browserEngineExclusionFiles = mapOf(
    "firefox" to rootProject.file("kadre/contracts/driver/web/contracts/engine-exclusions/firefox.tsv"),
    "webkit" to rootProject.file("kadre/contracts/driver/web/contracts/engine-exclusions/webkit.tsv"),
)
val browserEngineExclusionHeader = "contractId\ttarget\tcause"

/** The contract ids one engine's exclusion file removes from the expected set of `target`. */
fun engineExcludedContractIds(engine: String, target: String): List<String> {
    val file = requireNotNull(browserEngineExclusionFiles[engine]) { "unknown nightly browser engine: $engine" }
    val rows = file.readLines()
        .map(String::trimEnd)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
    require(rows.firstOrNull() == browserEngineExclusionHeader) {
        "invalid engine exclusion header in $file"
    }
    val excluded = mutableListOf<String>()
    for (row in rows.drop(1)) {
        val columns = row.split('\t')
        require(columns.size == 3 && columns[2].isNotBlank()) {
            "invalid engine exclusion row in $file: $row"
        }
        require(columns[0] in webContractIds) {
            "engine exclusion file for $engine names a contract outside the browser gate: ${columns[0]}"
        }
        if (columns[1] == target || columns[1] == "*") {
            require(columns[0] !in excluded) {
                "duplicate engine exclusion in $file: ${columns[0]} for $target"
            }
            excluded += columns[0]
        }
    }
    return excluded
}

browserEngineExclusionFiles.keys.sorted().forEach { engine ->
    listOf("js", "wasmJs").forEach { target ->
        val engineName = engine.replaceFirstChar(Char::uppercase)
        val targetName = target.replaceFirstChar(Char::uppercase)
        val exclusionsFile = browserEngineExclusionFiles.getValue(engine)
        val excludedContractIds = engineExcludedContractIds(engine, target)
        val expectedContractIds = webContractIds - excludedContractIds.toSet()
        val artifactDirectory = rootProject.file("kadre/contracts/driver/web/build/contract-evidence/$engine/$target")
        tasks.register<JavaExec>("validate${engineName}${targetName}BrowserContractEvidence") {
            group = "verification"
            description = "Validates the $target browser contract evidence artifacts the $engineName nightly expects."
            dependsOn("jvmMainClasses")
            dependsOn(":kadre:contracts:driver:web:${engine}${targetName}BrowserSmoke")
            classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
            mainClass.set("org.graphiks.kadre.contracts.ValidateContractEvidenceKt")
            args(
                browserContractRegistry.absolutePath,
                browserContractMappings.joinToString(separator = ",") { it.absolutePath },
                contractEvidenceCommit.get(),
                target,
                engine,
                expectedContractIds.joinToString(separator = ","),
                artifactDirectory.absolutePath,
                "test-results/browser/{engine}",
            )
            inputs.file(browserContractRegistry)
            inputs.files(browserContractMappings)
            inputs.files(fileTree(artifactDirectory))
            inputs.file(exclusionsFile)
            inputs.property("contractCommit", contractEvidenceCommit)
            inputs.property("contractTarget", target)
            inputs.property("contractExecutions", engine)
            inputs.property("contractGateIds", expectedContractIds)
            inputs.property("kadreEngineContractExclusions", excludedContractIds)
            inputs.property("junitReportRelativeDirectories", listOf("test-results/browser/{engine}"))
        }
    }
}

// The registry exact-set test reads contracts.tsv at runtime (repositoryFile), so a registry edit
// alone left jvmTest UP-TO-DATE and reported a green that a fresh CI run disproved (PR #438's
// first red). Declaring the registry as a test input makes the stale-green impossible.
tasks.named<Test>("jvmTest") {
    inputs.file(rootProject.file("kadre/contracts/registry/contracts.tsv"))
}
