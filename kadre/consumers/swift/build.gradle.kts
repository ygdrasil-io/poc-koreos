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
    // Le sous-build IMBRIQUÉ reconfigure le build racine ENTIer — or un GradleBuild ne
    // transmet AUCUN systemProp (ni -D, ni startParameter.systemPropertiesArgs, ni le
    // gradle.properties parent — Gradle 9.8, vérifié) : la compilation des scripts
    // android samples (forme DSL héritée, phases 9/11) y échoue sur un démon frais. Le
    // property racine `kadre.androidSamples=false` les exclut de la configuration de CE
    // sous-build, qui n'en résout AUCUNE tâche (voir settings.gradle.kts). Miroir du
    // startParameter.projectProperties déjà posé sur linkConsumerSimulatorFrameworks.
    startParameter.projectProperties = mapOf("kadre.androidSamples" to "false")
}

/** xcodegen régénère le projet à chaque évolution de la spec — le .xcodeproj GÉNÉRÉ est
 *  commité (convention des drivers, cf. contracts/driver/uikit) pour que la revue lise
 *  les diffs et que le consumer compile sans xcodegen préalable. */
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
 *
 * `skipTesting` porte le scénario de lancement gated SwiftUI (voir `gatedSwiftuiTestTask`) :
 * le run STANDARD ne l'exécute PAS (plutôt qu'un XCTSkip) — la gate contractuelle refuse
 * tout JUnit porteur d'un cas skipped, et la preuve gated est FAITE, sur le même bundle,
 * par le run dédié ci-dessous (env TEST_RUNNER_ + -only-testing). Aucune preuve n'est
 * perdue : le scénario passe de « skippé ailleurs » à « exécuté dans son run dédié
 * obligatoire ».
 */
fun consumerTestTask(
    name: String,
    scheme: String,
    destination: String,
    xcresult: Directory,
    skipTesting: List<String> = emptyList(),
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
        *skipTesting.flatMap { listOf("-skip-testing", it) }.toTypedArray(),
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

/**
 * Le run DÉDIÉ du scénario de lancement gated SwiftUI (`uikit-swiftui-consumer-gated-launch-
 * scene-hosts-the-wrapper` — la fenêtre SwiftUI posée À LA PLACE du store UIKit au
 * lancement) : la gate ne peut être armée qu'AU LANCEMENT du process (env `TEST_RUNNER_`
 * transmise à l'app hôte par xcodebuild, mécanique phase 1 `DriverSceneRequest`) — d'où un
 * relancement de l'app avec `-only-testing` du seul scénario. Mêmes gardes que le run
 * standard : TEST SUCCEEDED + zéro duplication Obj-C au lancement.
 */
fun gatedSwiftuiTestTask(
    name: String,
    scheme: String,
    destination: String,
    xcresult: Directory,
    onlyTesting: String,
) = tasks.register<Exec>(name) {
    group = "verification"
    description = "Runs the gated SwiftUI launch scenario of $scheme on $destination."
    dependsOn(generateConsumerProject)
    workingDir = consumerAppDirectory.asFile
    environment(
        "TEST_RUNNER_KADRE_CONSUMER_SWIFTUI" to "1",
        "TEST_RUNNER_KADRE_CONSUMER_SWIFTUI_FACTORY" to "held",
    )
    val logFile = logsDirectory.get().file("$name.log").asFile
    doFirst {
        delete(xcresult)
        logFile.parentFile.mkdirs()
        println("$name — log: $logFile")
        standardOutput = logFile.outputStream().buffered()
    }
    commandLine(
        "xcodebuild",
        "-project", "KadreSwiftConsumer.xcodeproj",
        "-scheme", scheme,
        "-destination", destination,
        "-resultBundlePath", xcresult.asFile.absolutePath,
        "-only-testing", onlyTesting,
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

/**
 * L'export JUnit d'un xcresult consumer — MIROIR EXACT du `junitExportTask` du driver
 * uikit (le même adaptateur `tools/xctest_to_junit.py`, pris par chemin relatif depuis ce
 * build). Les XML alimentent la gate contractuelle : le bloc uikit de
 * kadre/contracts/validator/build.gradle.kts les Sync près des preuves du driver
 * (`test-results/<target>ConsumerTest` et `test-results/<target>ConsumerTestGated`).
 */
fun consumerJunitExportTask(
    name: String,
    xcresult: Directory,
    output: Directory,
    upstream: TaskProvider<Exec>,
) = tasks.register<Exec>(name) {
    group = "verification"
    description = "Exports ${xcresult.asFile.name} to JUnit XML under ${output.asFile.path}."
    dependsOn(upstream)
    workingDir = projectDir
    // Purge des XML d'un run précédent : un testcase disparu de la suite ne doit pas
    // survivre dans le rapport exporté.
    doFirst { delete(output) }
    commandLine(
        "python3",
        "../../contracts/driver/uikit/tools/xctest_to_junit.py",
        xcresult.asFile.absolutePath,
        output.asFile.absolutePath,
    )
}

// Destinations phase 1 (les mêmes que le driver).
val iosSimulatorTests = consumerTestTask(
    "iosSimulatorTests", "KadreConsumerIos",
    "platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0",
    xcresultDirectory.get().dir("ios.xcresult"),
    skipTesting = listOf(
        "KadreConsumerIosTests/KadreSwiftUIConsumerTests/testLaunchGatedSwiftUISceneHostsTheWrapperAndAttachesOnce",
    ),
)
val tvosSimulatorTests = consumerTestTask(
    "tvosSimulatorTests", "KadreConsumerTvOs",
    "platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0",
    xcresultDirectory.get().dir("tvos.xcresult"),
    skipTesting = listOf(
        "KadreConsumerTvOsTests/KadreSwiftUIConsumerTests/testLaunchGatedSwiftUISceneHostsTheWrapperAndAttachesOnce",
    ),
)

// Les runs DÉDIÉS du lancement gated SwiftUI — une famille chacun, le même bundle.
val iosSimulatorGatedSwiftuiTests = gatedSwiftuiTestTask(
    "iosSimulatorGatedSwiftuiTests", "KadreConsumerIos",
    "platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0",
    xcresultDirectory.get().dir("ios-gated.xcresult"),
    onlyTesting = "KadreConsumerIosTests/KadreSwiftUIConsumerTests/testLaunchGatedSwiftUISceneHostsTheWrapperAndAttachesOnce",
)
val tvosSimulatorGatedSwiftuiTests = gatedSwiftuiTestTask(
    "tvosSimulatorGatedSwiftuiTests", "KadreConsumerTvOs",
    "platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0",
    xcresultDirectory.get().dir("tvos-gated.xcresult"),
    onlyTesting = "KadreConsumerTvOsTests/KadreSwiftUIConsumerTests/testLaunchGatedSwiftUISceneHostsTheWrapperAndAttachesOnce",
)

// Les exports JUnit par famille — standard ET gated (répertoires distincts : le même
// TEST-<suite>.xml des deux runs ne doit JAMAIS fusionner, l'identité testcase serait
// dupliquée et la gate refuserait).
val junitDirectory = layout.buildDirectory.dir("junit")
val junitGatedDirectory = layout.buildDirectory.dir("junit-gated")
val iosSimulatorJunitExport = consumerJunitExportTask(
    "iosSimulatorJunitExport", xcresultDirectory.get().dir("ios.xcresult"),
    junitDirectory.get().dir("iosSimulatorArm64"), iosSimulatorTests,
)
val tvosSimulatorJunitExport = consumerJunitExportTask(
    "tvosSimulatorJunitExport", xcresultDirectory.get().dir("tvos.xcresult"),
    junitDirectory.get().dir("tvosSimulatorArm64"), tvosSimulatorTests,
)
val iosSimulatorGatedJunitExport = consumerJunitExportTask(
    "iosSimulatorGatedJunitExport", xcresultDirectory.get().dir("ios-gated.xcresult"),
    junitGatedDirectory.get().dir("iosSimulatorArm64"), iosSimulatorGatedSwiftuiTests,
)
val tvosSimulatorGatedJunitExport = consumerJunitExportTask(
    "tvosSimulatorGatedJunitExport", xcresultDirectory.get().dir("tvos-gated.xcresult"),
    junitGatedDirectory.get().dir("tvosSimulatorArm64"), tvosSimulatorGatedSwiftuiTests,
)

tasks.named("check") {
    group = "verification"
    description = "Runs both simulator families of the external swift consumer (standard + gated SwiftUI launch) and exports their JUnit evidence."
    dependsOn(
        iosSimulatorTests, tvosSimulatorTests,
        iosSimulatorGatedSwiftuiTests, tvosSimulatorGatedSwiftuiTests,
        iosSimulatorJunitExport, tvosSimulatorJunitExport,
        iosSimulatorGatedJunitExport, tvosSimulatorGatedJunitExport,
    )
}
