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

val validateContractRegistry by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates the New Kadre machine-readable contract registry."
    dependsOn("jvmMainClasses")
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("org.graphiks.kadre.contracts.ValidateContractRegistryKt")
    args(rootProject.file("kadre/contracts/registry/contracts.tsv").absolutePath)
}

val appKitContractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val appKitContractMapping = rootProject.file("kadre/backend/appkit/contracts/evidence.tsv")
val appKitJUnitReports = rootProject.file("kadre/backend/appkit/build/test-results/jvmTest")
val appKitStandaloneLoopJUnitReports = rootProject.file("kadre/backend/appkit/build/test-results/appKitStandaloneLoopTest")
val appKitContractEvidenceDirectory = rootProject.file("kadre/backend/appkit/build/contract-evidence")
val appKitContractCommit = providers.gradleProperty("kadreContractCommit").orElse("local")
val appKitContractIds = listOf("APK-001", "APK-002")

val appKitContractEvidenceTasks = appKitContractIds.map { contractId ->
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
            appKitContractCommit.get(),
            contractId,
        )
        inputs.file(appKitContractRegistry)
        inputs.file(appKitContractMapping)
        inputs.dir(appKitJUnitReports)
        inputs.dir(appKitStandaloneLoopJUnitReports)
        inputs.property("contractCommit", appKitContractCommit)
        outputs.file(output)
    }
}

val generateAppKitContractEvidence by tasks.registering {
    group = "verification"
    description = "Generates and validates evidence for every active AppKit contract."
    dependsOn(appKitContractEvidenceTasks)
    outputs.dir(appKitContractEvidenceDirectory)
}

tasks.named("check") {
    dependsOn(validateContractRegistry)
}
