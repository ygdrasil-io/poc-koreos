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
val appKitContractEvidence = rootProject.file(
    "kadre/backend/appkit/build/contract-evidence/contract-evidence.json",
)
val appKitContractCommit = providers.gradleProperty("kadreContractCommit").orElse("local")

val generateAppKitContractEvidence by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Generates and validates APK-001 evidence from AppKit JUnit reports."
    dependsOn("jvmMainClasses")
    classpath(jvmMain.output.allOutputs, jvmMain.runtimeDependencyFiles)
    mainClass.set("org.graphiks.kadre.contracts.GenerateContractEvidenceKt")
    args(
        appKitContractRegistry.absolutePath,
        appKitContractMapping.absolutePath,
        appKitJUnitReports.absolutePath,
        appKitContractEvidence.absolutePath,
        appKitContractCommit.get(),
    )
    inputs.file(appKitContractRegistry)
    inputs.file(appKitContractMapping)
    inputs.dir(appKitJUnitReports)
    inputs.property("contractCommit", appKitContractCommit)
    outputs.file(appKitContractEvidence)
}

tasks.named("check") {
    dependsOn(validateContractRegistry)
}
