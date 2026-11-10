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
val uikitContractIds = listOf("BCK-012", "BCK-013", "BCK-014", "BCK-015", "BCK-016", "BCK-018", "BCK-019", "BCK-020", "BCK-021", "BCK-022", "BCK-023", "BCK-024", "BCK-026", "BCK-027", "BCK-028", "WIN-009")
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
// GMP-002 (refus honnête) est retiré : le service evdev le remplace (GMP-003)
val waylandContractIds = listOf("CLS-001", "BCK-017", "SES-001", "SUR-001", "WIN-010", "WIN-011", "WIN-012", "SUR-002", "DSP-002", "SUR-003", "INP-004", "INP-005", "INP-006", "WIN-013", "INT-005", "TXT-002", "TXT-003", "DRP-002", "DRP-003", "SES-003", "SUR-004", "INP-007")
val linuxContractIds = listOf("GMP-003", "GMP-004", "CAP-002", "CAP-003", "CAP-004")
val win32ContractIds = listOf("W32-001", "W32-002", "W32-003", "BCK-025", "SES-002", "PMP-001", "W32-004", "W32-005", "W32-006", "W32-007", "W32-008", "W32-009", "W32-010", "W32-011", "W32-012", "W32-013", "W32-014", "W32-015", "W32-016", "W32-017", "W32-018", "W32-019", "W32-020", "W32-021", "W32-022", "W32-023", "W32-024", "W32-025", "W32-026", "W32-027", "W32-028", "W32-029", "W32-030", "W32-031", "W32-032")
val contractEvidenceGateIds = appKitContractIds + runtimeContractIds + webContractIds + uikitContractIds + waylandContractIds + linuxContractIds + win32ContractIds
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
            rootProject.file("kadre/backend/wayland/contracts/evidence.tsv"),
            rootProject.file("kadre/backend/win32/contracts/evidence.tsv"),
            rootProject.file("kadre/backend/linux/contracts/evidence.tsv"),
        ).joinToString(separator = ",") { it.absolutePath },
        contractEvidenceGateIds.joinToString(separator = ","),
    )
    inputs.files(
        rootProject.file("kadre/runtime/contracts/evidence.tsv"),
        rootProject.file("kadre/backend/appkit/contracts/evidence.tsv"),
        rootProject.file("kadre/contracts/driver/web/contracts/evidence.tsv"),
        rootProject.file("kadre/contracts/driver/uikit/contracts/evidence.tsv"),
        rootProject.file("kadre/backend/wayland/contracts/evidence.tsv"),
        rootProject.file("kadre/backend/win32/contracts/evidence.tsv"),
        rootProject.file("kadre/backend/linux/contracts/evidence.tsv"),
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

val waylandContractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val waylandContractMapping = rootProject.file("kadre/backend/wayland/contracts/evidence.tsv")
val waylandJvmJUnitReports = rootProject.file("kadre/backend/wayland/build/test-results/jvmTest")
val waylandNativeJUnitReports = rootProject.file("kadre/backend/wayland/build/test-results/waylandNativeTests")
val waylandStressJUnitReports = rootProject.file("kadre/backend/wayland/build/test-results/waylandStressTests")
val waylandContractEvidenceDirectory = rootProject.file("kadre/backend/wayland/build/contract-evidence")
// La gate Wayland distingue adapter, libc, compositor/version et protocoles testés
// (roadmap §7) : adapter `wayland-jvm`, cible `jvm`, preuves exécutées sous Linux.
val waylandContractAdapter = "wayland-jvm"
val waylandContractEvidenceTasks = waylandContractIds.filter(activeContractIds::contains).map { contractId ->
    tasks.register<JavaExec>("generateWayland${contractId.replace("-", "")}ContractEvidence") {
        group = "verification"
        description = "Generates and validates $contractId evidence from the Wayland JUnit reports."
        dependsOn("jvmMainClasses", ":kadre:backend:wayland:waylandContractTests", ":kadre:backend:wayland:waylandStressTests")
        classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
        mainClass.set("org.graphiks.kadre.contracts.GenerateContractEvidenceKt")
        val output = waylandContractEvidenceDirectory.resolve("$contractId.json")
        args(
            waylandContractRegistry.absolutePath,
            waylandContractMapping.absolutePath,
            listOf(waylandJvmJUnitReports, waylandNativeJUnitReports, waylandStressJUnitReports).joinToString(
                separator = System.getProperty("path.separator"),
            ) {
                it.absolutePath
            },
            output.absolutePath,
            contractEvidenceCommit.get(),
            contractId,
            contractEvidenceTarget,
            waylandContractAdapter,
        )
        inputs.file(waylandContractRegistry)
        inputs.file(waylandContractMapping)
        inputs.dir(waylandJvmJUnitReports)
        inputs.dir(waylandStressJUnitReports)
        inputs.dir(waylandNativeJUnitReports)
        inputs.property("contractCommit", contractEvidenceCommit)
        inputs.property("contractTarget", contractEvidenceTarget)
        inputs.property("contractAdapter", waylandContractAdapter)
        outputs.file(output)
    }
}

val validateWaylandContractEvidence by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates every active Wayland JVM contract evidence artifact."
    dependsOn(waylandContractEvidenceTasks)
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("org.graphiks.kadre.contracts.ValidateContractEvidenceKt")
    args(
        waylandContractRegistry.absolutePath,
        waylandContractMapping.absolutePath,
        contractEvidenceCommit.get(),
        contractEvidenceTarget,
        "junit",
        waylandContractIds.filter(activeContractIds::contains).joinToString(separator = ","),
        rootProject.file("kadre/backend/wayland/build").absolutePath,
        listOf(
            "test-results/jvmTest",
            "test-results/waylandNativeTests",
            "test-results/waylandStressTests",
        ).joinToString(separator = System.getProperty("path.separator")),
    )
    inputs.file(waylandContractRegistry)
    inputs.file(waylandContractMapping)
    inputs.dir(waylandContractEvidenceDirectory)
    inputs.property("contractCommit", contractEvidenceCommit)
    inputs.property("contractTarget", contractEvidenceTarget)
    inputs.property("contractExecutions", "junit")
    inputs.property("contractGateIds", waylandContractIds)
    inputs.property(
        "junitReportRelativeDirectories",
        listOf("test-results/jvmTest", "test-results/waylandNativeTests", "test-results/waylandStressTests"),
    )
}

val generateWaylandContractEvidence by tasks.registering {
    group = "verification"
    description = "Generates and validates evidence for every active Wayland contract."
    dependsOn(validateWaylandContractEvidence)
    outputs.dir(waylandContractEvidenceDirectory)
}

val linuxContractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val linuxContractMapping = rootProject.file("kadre/backend/linux/contracts/evidence.tsv")
val linuxJvmJUnitReports = rootProject.file("kadre/backend/linux/build/test-results/jvmTest")
val linuxContractEvidenceDirectory = rootProject.file("kadre/backend/linux/build/contract-evidence")
// Le service evdev sert les sessions Wayland depuis :kadre:backend:linux :
// adapter `linux-evdev-jvm`, cible `jvm`, preuves O2 portables (l'oracle
// uinput O3 tourne sous conteneur privilégié, cf. LINUX-EVDEV-GAMEPAD-DESIGN).
val linuxContractAdapter = "linux-evdev-jvm"
val linuxContractEvidenceTasks = linuxContractIds.filter(activeContractIds::contains).map { contractId ->
    tasks.register<JavaExec>("generateLinux${contractId.replace("-", "")}ContractEvidence") {
        group = "verification"
        description = "Generates and validates $contractId evidence from the Linux evdev JUnit reports."
        dependsOn("jvmMainClasses", ":kadre:backend:linux:jvmTest")
        classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
        mainClass.set("org.graphiks.kadre.contracts.GenerateContractEvidenceKt")
        val output = linuxContractEvidenceDirectory.resolve("$contractId.json")
        args(
            linuxContractRegistry.absolutePath,
            linuxContractMapping.absolutePath,
            linuxJvmJUnitReports.absolutePath,
            output.absolutePath,
            contractEvidenceCommit.get(),
            contractId,
            contractEvidenceTarget,
            linuxContractAdapter,
        )
        inputs.file(linuxContractRegistry)
        inputs.file(linuxContractMapping)
        inputs.dir(linuxJvmJUnitReports)
        inputs.property("contractCommit", contractEvidenceCommit)
        inputs.property("contractTarget", contractEvidenceTarget)
        inputs.property("contractAdapter", linuxContractAdapter)
        outputs.file(output)
    }
}

val validateLinuxContractEvidence by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates every active Linux evdev contract evidence artifact."
    dependsOn(linuxContractEvidenceTasks)
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("org.graphiks.kadre.contracts.ValidateContractEvidenceKt")
    args(
        linuxContractRegistry.absolutePath,
        linuxContractMapping.absolutePath,
        contractEvidenceCommit.get(),
        contractEvidenceTarget,
        "junit",
        linuxContractIds.filter(activeContractIds::contains).joinToString(separator = ","),
        rootProject.file("kadre/backend/linux/build").absolutePath,
        "test-results/jvmTest",
    )
    inputs.file(linuxContractRegistry)
    inputs.file(linuxContractMapping)
    inputs.dir(linuxContractEvidenceDirectory)
    inputs.property("contractCommit", contractEvidenceCommit)
    inputs.property("contractTarget", contractEvidenceTarget)
    inputs.property("contractExecutions", "junit")
    inputs.property("contractGateIds", linuxContractIds)
    inputs.property("junitReportRelativeDirectories", listOf("test-results/jvmTest"))
}

val generateLinuxContractEvidence by tasks.registering {
    group = "verification"
    description = "Generates and validates evidence for every active Linux evdev contract."
    dependsOn(validateLinuxContractEvidence)
    outputs.dir(linuxContractEvidenceDirectory)
}

val win32ContractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val win32ContractMapping = rootProject.file("kadre/backend/win32/contracts/evidence.tsv")
val win32JvmJUnitReports = rootProject.file("kadre/backend/win32/build/test-results/jvmTest")
val win32NativeJUnitReports = rootProject.file("kadre/backend/win32/build/test-results/win32NativeTests")
val win32EmbeddedJUnitReports = rootProject.file("kadre/backend/win32/build/test-results/win32EmbeddedTests")
val win32ReuseJUnitReports = rootProject.file("kadre/backend/win32/build/test-results/win32ReuseTests")
val win32ContractEvidenceDirectory = rootProject.file("kadre/backend/win32/build/contract-evidence")
// La gate Win32 distingue l'adapter (décisions R3) : adapter `win32-jvm`, cible `jvm`,
// preuves exécutées sur le runner Windows (décisions D1).
val win32ContractAdapter = "win32-jvm"
val win32ContractEvidenceTasks = win32ContractIds.filter(activeContractIds::contains).map { contractId ->
    tasks.register<JavaExec>("generateWin32${contractId.replace("-", "")}ContractEvidence") {
        group = "verification"
        description = "Generates and validates $contractId evidence from the Win32 JUnit reports."
        dependsOn("jvmMainClasses", ":kadre:backend:win32:win32ContractTests")
        classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
        mainClass.set("org.graphiks.kadre.contracts.GenerateContractEvidenceKt")
        val output = win32ContractEvidenceDirectory.resolve("$contractId.json")
        args(
            win32ContractRegistry.absolutePath,
            win32ContractMapping.absolutePath,
            listOf(win32JvmJUnitReports, win32NativeJUnitReports, win32EmbeddedJUnitReports, win32ReuseJUnitReports).joinToString(
                separator = System.getProperty("path.separator"),
            ) {
                it.absolutePath
            },
            output.absolutePath,
            contractEvidenceCommit.get(),
            contractId,
            contractEvidenceTarget,
            win32ContractAdapter,
        )
        inputs.file(win32ContractRegistry)
        inputs.file(win32ContractMapping)
        inputs.dir(win32JvmJUnitReports)
        inputs.dir(win32NativeJUnitReports)
        inputs.property("contractCommit", contractEvidenceCommit)
        inputs.property("contractTarget", contractEvidenceTarget)
        inputs.property("contractAdapter", win32ContractAdapter)
        outputs.file(output)
    }
}

val validateWin32ContractEvidence by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates every active Win32 JVM contract evidence artifact."
    dependsOn(win32ContractEvidenceTasks)
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("org.graphiks.kadre.contracts.ValidateContractEvidenceKt")
    args(
        win32ContractRegistry.absolutePath,
        win32ContractMapping.absolutePath,
        contractEvidenceCommit.get(),
        contractEvidenceTarget,
        "junit",
        win32ContractIds.filter(activeContractIds::contains).joinToString(separator = ","),
        rootProject.file("kadre/backend/win32/build").absolutePath,
        listOf(
            "test-results/jvmTest",
            "test-results/win32NativeTests",
            "test-results/win32EmbeddedTests",
            "test-results/win32ReuseTests",
        ).joinToString(separator = System.getProperty("path.separator")),
    )
    inputs.file(win32ContractRegistry)
    inputs.file(win32ContractMapping)
    inputs.dir(win32ContractEvidenceDirectory)
    inputs.property("contractCommit", contractEvidenceCommit)
    inputs.property("contractTarget", contractEvidenceTarget)
    inputs.property("contractExecutions", "junit")
    inputs.property("contractGateIds", win32ContractIds)
    inputs.property(
        "junitReportRelativeDirectories",
        listOf("test-results/jvmTest", "test-results/win32NativeTests", "test-results/win32EmbeddedTests"),
    )
}

val generateWin32ContractEvidence by tasks.registering {
    group = "verification"
    description = "Generates and validates evidence for every active Win32 contract."
    dependsOn(validateWin32ContractEvidence)
    outputs.dir(win32ContractEvidenceDirectory)
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

// ownership-first: réactivé par le plan Android/UIKit/Web — les câblages de preuves
// browser (jsBrowserSmoke/wasmJsBrowserSmoke du driver web), UIKit (simulatorTests du
// driver uikit + tests platform:uikit) et Android (androidDeviceSmoke du driver android)
// sont retirés avec leurs modules ; les listes d'ids ci-dessus restent des DONNÉES de la
// gate de registre (les evidence.tsv des drivers élagués demeurent sur le disque).

tasks.named("check") {
    dependsOn(validateContractRegistry)
    dependsOn(generateRuntimeContractEvidence)
}

// The registry exact-set test reads contracts.tsv at runtime (repositoryFile), so a registry edit
// alone left jvmTest UP-TO-DATE and reported a green that a fresh CI run disproved (PR #438's
// first red). Declaring the registry as a test input makes the stale-green impossible.
tasks.named<Test>("jvmTest") {
    inputs.file(rootProject.file("kadre/contracts/registry/contracts.tsv"))
}
