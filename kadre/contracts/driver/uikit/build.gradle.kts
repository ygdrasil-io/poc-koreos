// Driver UIKit : orchestre la preuve O3 par famille (xcodebuild sur simulateurs, puis export
// JUnit des xcresult). Module Gradle volontairement nu — aucun plugin : il n'assemble rien,
// il pilote le projet Xcode voisin. macOS-only, comme le gate AppKit ; jamais câblé dans
// :kadre:check (la gate contractuelle passe par kadre/contracts/validator, pilote hors CI
// macOS par le script de Task 7).

val simulatorXcresultDirectory = layout.projectDirectory.dir("build/xcresult")
val contractEvidenceDirectory = layout.projectDirectory.dir("build/contract-evidence")

/**
 * Un run xcodebuild d'une famille de tests XCTest du driver. Les Chemins passés à xcodebuild
 * sont absolus : le `-resultBundlePath` relatif se résout depuis le workingDir, mais l'absolu
 * ôte toute ambiguïté entre le CWD de l'Exec et celui du démon Gradle.
 */
fun simulatorTestTask(name: String, scheme: String, destination: String, xcresult: Directory, frameworkTasks: List<String>) =
    tasks.register<Exec>(name) {
        group = "verification"
        description = "Runs the $scheme XCTest suite on $destination."
        dependsOn(frameworkTasks)
        workingDir = projectDir
        // xcodebuild refuse un -resultBundlePath déjà existant : purge avant chaque exécution.
        doFirst { delete(xcresult) }
        commandLine(
            "xcodebuild",
            "-project", "KadreUikitDriver.xcodeproj",
            "-scheme", scheme,
            "-destination", destination,
            "-resultBundlePath", xcresult.asFile.absolutePath,
            "CODE_SIGNING_ALLOWED=NO",
            "test",
        )
    }

fun junitExportTask(name: String, xcresult: Directory, output: Directory, upstream: TaskProvider<Exec>) =
    tasks.register<Exec>(name) {
        group = "verification"
        description = "Exports ${xcresult.asFile.name}.xcresult to JUnit XML under ${output.asFile.path}."
        dependsOn(upstream)
        workingDir = projectDir
        // Purge des XML d'un run précédent : un testcase disparu de la suite ne doit pas
        // survivre dans le rapport exporté.
        doFirst { delete(output) }
        commandLine(
            "python3",
            "tools/xctest_to_junit.py",
            xcresult.asFile.absolutePath,
            output.asFile.absolutePath,
        )
    }

val iosSimulatorTests = simulatorTestTask(
    "iosSimulatorTests", "KadreUikitDriverIos",
    "platform=iOS Simulator,name=iPhone 17,OS=27.0",
    simulatorXcresultDirectory.dir("ios.xcresult"),
    listOf(":kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64"),
)
val tvosSimulatorTests = simulatorTestTask(
    "tvosSimulatorTests", "KadreUikitDriverTvOs",
    "platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0",
    simulatorXcresultDirectory.dir("tvos.xcresult"),
    listOf(":kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64"),
)

// Les rapports JUnit vivent SOUS le répertoire d'artefacts par target du validateur
// (build/contract-evidence/<target>/test-results/<target>) : ValidateContractEvidence résout
// les répertoires JUnit relativement au répertoire d'artefacts et refuse tout chemin contenant "..".
val iosJunit = junitExportTask(
    "iosSimulatorJunitExport", simulatorXcresultDirectory.dir("ios.xcresult"),
    contractEvidenceDirectory.dir("iosSimulatorArm64/test-results/iosSimulatorArm64"), iosSimulatorTests,
)
val tvosJunit = junitExportTask(
    "tvosSimulatorJunitExport", simulatorXcresultDirectory.dir("tvos.xcresult"),
    contractEvidenceDirectory.dir("tvosSimulatorArm64/test-results/tvosSimulatorArm64"), tvosSimulatorTests,
)

tasks.register("simulatorTests") {
    group = "verification"
    description = "Runs both simulator families and exports their JUnit evidence."
    dependsOn(iosSimulatorTests, tvosSimulatorTests, iosJunit, tvosJunit)
}
