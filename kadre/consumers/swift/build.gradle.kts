plugins {
    base
}

/*
 * L'agrégateur du consumer Swift externe (phase 3) — miroir consumers/typescript : un
 * module Gradle volontairement nu qui PILOTE des sous-builds (jamais `project(...)`) :
 *
 *   1. `consumer-kmp` — le module applicatif Kotlin du consumer, qui résout
 *      `org.graphiks.kadre:uikit` depuis le repository contractTest et lie ses frameworks
 *      STATIQUES `ConsumerKadre` par cible (GradleBuild, propriétés -PkadreRepository/
 *      -PkadreVersion transmises telles quelles).
 *   2. `:kadre:platform:uikit` (build racine du dépôt) — l'assemblage du XCFramework
 *      STATIQUE de distribution (Task 1).
 *   3. `consumer-app` — xcodegen puis `xcodebuild test` par destination : LA preuve
 *      anti-duplication (l'app se LANCE avec les deux archives statiques liées ; des
 *      classes Obj-C dupliquées crashent au lancement) ET la preuve d'identité de types
 *      (la factory `ConsumerApplicationFactory` de ConsumerKadre traverse Swift jusqu'à
 *      l'attach de KadreUikit).
 *
 * Comme consumers/kotlin, ce build REÇOIT `-PkadreRepository` et `-PkadreVersion` : le
 * repository doit être publié AU PRÉALABLE (root `publishContractArtifacts` +
 * `:kadre:platform:uikit:publishAllPublicationsToContractTestRepository`). macOS-only,
 * comme le gate AppKit — jamais câblé dans :kadre:check (xcodebuild + simulateurs).
 */

val kadreRepository = providers.gradleProperty("kadreRepository")
    .orNull
    ?: error("-PkadreRepository is required")
val kadreVersion = providers.gradleProperty("kadreVersion")
    .orNull
    ?: error("-PkadreVersion is required")

val repositoryDirectory = file(kadreRepository)
val consumerKmpDirectory = layout.projectDirectory.dir("consumer-kmp")
val consumerAppDirectory = layout.projectDirectory.dir("consumer-app")
// La racine du build Gradle du dépôt (trois niveaux au-dessus de consumers/swift).
val buildRootDirectory = layout.projectDirectory.dir("../../..")
val xcresultDirectory = layout.buildDirectory.dir("xcresult")
val logsDirectory = layout.buildDirectory.dir("logs")

/** Le repository contractTest doit porter l'artefact uikit AVEC ses métadonnées Gradle. */
val checkContractRepository by tasks.registering {
    group = "build"
    description = "Checks the contract test repository publishes the uikit artifact."
    inputs.property("kadreVersion", kadreVersion)
    inputs.dir(repositoryDirectory)
    doLast {
        val uikitModule = repositoryDirectory
            .resolve("org/graphiks/kadre/uikit/$kadreVersion/uikit-$kadreVersion.module")
        check(uikitModule.isFile) {
            "the contract repository must publish $uikitModule — run " +
                ":kadre:platform:uikit:publishAllPublicationsToContractTestRepository first"
        }
    }
}

/** Les frameworks STATIQUES du module applicatif consumer, slices simulateur (tests xcodebuild). */
val linkConsumerSimulatorFrameworks by tasks.registering(GradleBuild::class) {
    group = "build"
    description = "Links the static consumer KadreUikit frameworks for both simulator targets."
    dependsOn(checkContractRepository)
    dir = consumerKmpDirectory.asFile
    tasks = listOf(
        "linkDebugFrameworkIosSimulatorArm64",
        "linkDebugFrameworkTvosSimulatorArm64",
    )
    startParameter.projectProperties = mapOf(
        "kadreRepository" to kadreRepository,
        "kadreVersion" to kadreVersion,
    )
}

/** Le XCFramework STATIQUE de distribution uikit (Task 1) — slices simulateur inclus. */
val assembleKadreUikitXCFramework by tasks.registering(GradleBuild::class) {
    group = "build"
    description = "Assembles the static KadreUikit.xcframework from the repository root build."
    dir = buildRootDirectory.asFile
    tasks = listOf(":kadre:platform:uikit:assembleKadreUikitDebugXCFramework")
}

/** xcodegen régénère le projet si la spec change — le .xcodeproj n'est pas versionné. */
val generateConsumerProject by tasks.registering(Exec::class) {
    group = "build"
    description = "Generates KadreSwiftConsumer.xcodeproj from project.yml."
    dependsOn(assembleKadreUikitXCFramework, linkConsumerSimulatorFrameworks)
    workingDir(consumerAppDirectory)
    commandLine("xcodegen", "generate")
    inputs.file(consumerAppDirectory.file("project.yml"))
    inputs.dir(consumerAppDirectory.dir("apps"))
    inputs.dir(consumerAppDirectory.dir("tests"))
    inputs.dir(layout.projectDirectory.dir("../../platform/uikit/swift/Sources/KadreHost"))
    outputs.dir(consumerAppDirectory.dir("KadreSwiftConsumer.xcodeproj"))
}

/**
 * Un run `xcodebuild test` d'une famille du consumer. Le stdout est capturé dans un log
 * car la preuve anti-duplication s'y lit : tout « Class … is implemented in both » est
 * une duplication Obj-C au lancement — le run est alors UN ÉCHEC même si les tests
 * passent. Un `duplicate symbol` à l'édition de liens fait échouer xcodebuild de lui-même.
 */
fun consumerTestTask(
    name: String,
    scheme: String,
    destination: String,
    xcresult: Directory,
) = tasks.register<Exec>(name) {
    group = "verification"
    description = "Runs the $scheme consumer XCTest suite on $destination."
    dependsOn(generateConsumerProject)
    workingDir = consumerAppDirectory.asFile
    val logFile = logsDirectory.get().file("$name.log").asFile
    doFirst {
        // xcodebuild refuse un -resultBundlePath déjà existant : purge avant chaque run.
        delete(xcresult)
        logFile.parentFile.mkdirs()
        println("$name — log: $logFile")
        // Le flux est ouvert ICI (pas à la configuration) : le répertoire de logs
        // n'existe pas encore quand Gradle configure le graphe de tasks.
        standardOutput = logFile.outputStream().buffered()
    }
    commandLine(
        "xcodebuild",
        "-project", "KadreSwiftConsumer.xcodeproj",
        "-scheme", scheme,
        "-destination", destination,
        "-resultBundlePath", xcresult.asFile.absolutePath,
        "CODE_SIGNING_ALLOWED=NO",
        "test",
    )
    doLast {
        val log = logFile.readText()
        check(log.contains("TEST SUCCEEDED")) {
            "$name: xcodebuild did not report TEST SUCCEEDED (log: $logFile)"
        }
        check(!log.contains("is implemented in both")) {
            "$name: DUPLICATE Obj-C class detected at launch — the two static frameworks " +
                "must contribute exactly one implementation of each class (log: $logFile)"
        }
    }
}

// Destinations phase 1 (les mêmes que le driver).
val iosSimulatorTests = consumerTestTask(
    "iosSimulatorTests", "KadreConsumerIos",
    "platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0",
    xcresultDirectory.get().dir("ios.xcresult"),
)
val tvosSimulatorTests = consumerTestTask(
    "tvosSimulatorTests", "KadreConsumerTvOs",
    "platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0",
    xcresultDirectory.get().dir("tvos.xcresult"),
)

tasks.named("check") {
    group = "verification"
    description = "Runs both simulator families of the external swift consumer."
    dependsOn(iosSimulatorTests, tvosSimulatorTests)
}
