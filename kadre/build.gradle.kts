@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("maven-publish")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(25)
    android {
        compileSdk = 35
        minSdk = 24
        namespace = "org.graphiks.kadre"
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm()
    js { browser() }
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre:foundation"))
        }
        androidMain.dependencies {
            api(project(":kadre:platform:android"))
        }
        jvmMain.dependencies {
            api(project(":kadre:platform:desktop"))
        }
        jsMain.dependencies {
            api(project(":kadre:platform:web"))
        }
        wasmJsMain.dependencies {
            api(project(":kadre:platform:web"))
        }
        appleMain.dependencies {
            api(project(":kadre:platform:uikit"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            // Parseur du test de pureté (voir AndroidVariantPurityTest) : Json.parseToJsonElement
            // ne nécessite pas le plugin de sérialisation, juste le runtime.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

tasks.named("check") {
    dependsOn(":kadre:foundation:check")
    dependsOn(":kadre:contracts:validator:check")
    dependsOn(":kadre:backend:appkit:check")
    dependsOn(":kadre:backend:wayland:check")
    dependsOn(":kadre:platform:desktop:check")
    dependsOn(":kadre:platform:web:check")
    // pin : le contrat de thread phase 0 (AndroidAttachStateTest) tourne à chaque check local
    dependsOn(":kadre:platform:android:check")
    dependsOn(":kadre:runtime:check")
    dependsOn("validateKotlinConsumer")
    dependsOn("validateJavaConsumer")
    dependsOn("validateWebKotlinConsumer")
    dependsOn("validateTypeScriptConsumer")
    // pin : la pureté est vérifiée à chaque check local avec les artefacts publiés
    dependsOn("jvmTest")
}

val contractTestRepository = rootProject.layout.buildDirectory.dir("kadre-contract-repository")
val contractPublications = tasks.register("publishContractArtifacts") {
    dependsOn(":kadre:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:foundation:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:backend:appkit:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:backend:wayland:publishAllPublicationsToContractTestRepository")
    // phase 11 : le graphe runtime COMPLET — le consumer exécute une session
    // réelle, linux et win32 doivent rider l'agrégateur depuis ce repository
    dependsOn(":kadre:backend:linux:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:backend:win32:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:platform:desktop:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:platform:web:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:runtime:publishAllPublicationsToContractTestRepository")
    dependsOn(":kadre:platform:android:publishAllPublicationsToContractTestRepository")
    // Le porteur on-device des scénarios drop AND-007 : l'AAR est consommé androidTestImplementation
    // par le consumer — les classes de scénarios tournent dans le run connecté du consumer.
    dependsOn(":kadre:platform:android-devicetest:publishAllPublicationsToContractTestRepository")
    // La liaison Compose (phase 9) : le sample compose-showcase (AND-010) consomme l'AAR PUBLIÉ
    // de la liaison depuis le repository contractuel — le même parti pris consumateur que le
    // `android-devicetest` ci-dessus (jamais un `project(...)` qui contournerait la preuve).
    dependsOn(":kadre:integration:compose:publishAllPublicationsToContractTestRepository")
}

tasks.register<GradleBuild>("validateKotlinConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/kotlin")
    tasks = listOf("test")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

tasks.register<GradleBuild>("validateJavaConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/java")
    tasks = listOf("compileJava")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

tasks.register<GradleBuild>("validateWebKotlinConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/web")
    tasks = listOf("compileKotlinJs", "compileKotlinWasmJs")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

tasks.register<GradleBuild>("validateTypeScriptConsumer") {
    dependsOn(contractPublications)
    dir = file("consumers/typescript")
    tasks = listOf("check")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
}

tasks.named<Test>("jvmTest") {
    systemProperty("kadreContractRepository", rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.absolutePath)
    systemProperty("kadreVersion", project.version.toString())
    dependsOn("publishContractArtifacts")
}

// Le consumer Swift externe (phase 3/10) suit la discipline des consumers ci-dessus : les
// artefacts sont publiés D'ABORD dans le repository contractTest — uikit ET foundation EN
// PLUS des publications communes (l'agrégateur résout `org.graphiks.kadre:uikit` DEPUIS ce
// repository, jamais un `project(...)`), puis son `check` enchaîne xcodegen, les quatre
// runs xcodebuild (standard + lancement gated SwiftUI, iOS et tvOS) et leurs exports JUnit,
// la garde anti-duplication Obj-C du doLast comprise. macOS-ONLY (xcodebuild, simulateurs,
// xcodegen) : volontairement HORS `:kadre:check` — miroir de la posture des gates AppKit ;
// la tâche est le point d'entrée découvrable qu'un job CI macOS (dédié ou consolidé)
// invoque, et le gate `scripts/test-uikit-simulator.sh` pilote le MÊME agrégateur par
// famille (`run_consumer`). `--refresh-dependencies` : la même garde anti-repository-
// périmé que la gate. Sur un hôte non-macOS, aucun graphe n'est câblé et l'échec est
// EXPLICITE (jamais un downgrade silencieux).
val swiftConsumerOnMacOs = providers.systemProperty("os.name").get().lowercase().contains("mac")
tasks.register<GradleBuild>("validateSwiftConsumer") {
    group = "verification"
    description =
        "Runs the external swift consumer (consumers/swift) against the contract repository — " +
            "macOS only (xcodebuild + iOS/tvOS simulators), never wired into :kadre:check."
    if (swiftConsumerOnMacOs) {
        dependsOn(contractPublications)
        dependsOn(":kadre:platform:uikit:publishAllPublicationsToContractTestRepository")
        dependsOn(":kadre:foundation:publishAllPublicationsToContractTestRepository")
    }
    dir = file("consumers/swift")
    tasks = listOf("check")
    startParameter.projectProperties = mapOf(
        "kadreRepository" to contractTestRepository.get().asFile.absolutePath,
        "kadreVersion" to project.version.toString(),
    )
    startParameter.isRefreshDependencies = true
    doFirst {
        if (!swiftConsumerOnMacOs) {
            throw GradleException(
                ":kadre:validateSwiftConsumer exige macOS (xcodebuild, simulateurs iOS/tvOS, " +
                    "xcodegen) — tâche volontairement hors :kadre:check, miroir des gates " +
                    "AppKit ; utilisez un hôte macOS (local ou job CI)."
            )
        }
    }
}

/**
 * The browser entries of the TypeScript consumer, as the Web driver's host page consumes them.
 *
 * The browser consumer is part of the consumer build's own `check` (`emitBrowserConsumer` is a
 * dependency of it), so this task names that one nested invocation instead of starting a second one:
 * Gradle refuses two `GradleBuild` tasks on the same directory in one invocation, which is what the
 * Web smoke and this check would otherwise be. Everything the emission needs is already published by
 * `contractPublications`, which `validateTypeScriptConsumer` depends on.
 */
tasks.register("emitTypeScriptBrowserConsumer") {
    group = "build"
    description = "Names the browser consumers emitted by the TypeScript consumer check."
    dependsOn("validateTypeScriptConsumer")
}

publishing {
    repositories {
        maven {
            name = "contractTest"
            url = rootProject.layout.buildDirectory.dir("kadre-contract-repository").get().asFile.toURI()
        }
    }
}

// Les variantes natives iOS/tvOS de l'umbrella ne se publient PAS — sur AUCUN hôte : le
// module d'agrégation n'a aucune source native (compilations NO-SOURCE, aucun klib
// n'existe), sa publication maven échoue donc à la génération des métadonnées
// (FileNotFoundException sur le klib inexistant). Les variantes réellement consommées
// depuis contractTest sont android/jvm/js/wasm ; le natif consomme le LIEN framework de
// platform:uikit (le driver UIKit), pas une variante maven — les propres publications de
// platform:uikit, qui a des sources, restent actives. La résolution native depuis
// contractTest sera révisée quand platform:uikit sera officiellement publié.
tasks.configureEach {
    val nativePublicationTask = name.startsWith("publish") &&
        (name.contains("Ios") || name.contains("Tvos"))
    val nativeMetadataTask = name.startsWith("generateMetadataFileFor") &&
        (name.contains("Ios") || name.contains("Tvos"))
    if (nativePublicationTask || nativeMetadataTask) {
        enabled = false
    }
}
