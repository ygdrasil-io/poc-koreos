import org.gradle.api.tasks.PathSensitivity
import java.util.Properties

// Driver du contrat Android (phase 0) : module JVM pur — aucun plugin Android. Il orchestre le
// consumer instrumenté (`kadre/consumers/android`) en forkant son build Gradle, puis produit les
// preuves canoniques AND-001 avec le générateur du validateur (jamais une réimplémentation du format).
plugins {
    kotlin("jvm")
}

group = "org.graphiks.kadre.contracts.driver"

kotlin {
    jvmToolchain(25)
}

// Le producteur vit dans le layout `jvmMain` du brief — remappé sur le source set main du
// module JVM pur (le chemin `src/jvmMain/kotlin` n'est pas celui par défaut de kotlin("jvm")).
sourceSets {
    main {
        kotlin.srcDir("src/jvmMain/kotlin")
    }
}

dependencies {
    // `GenerateContractEvidenceKt` vit dans le validateur ; le producteur l'invoque avec les
    // mêmes arguments que les producteurs runtime/AppKit (registre, mapping, JUnit, sortie,
    // commit, contractId, target, adapter). La configuration `jvmRuntimeElements` est visée
    // explicitement : la résolution variante par défaut d'un consommateur `kotlin("jvm")`
    // retombe sur la variante métadonnées du projet multiplateforme, sans ses classes.
    implementation(project(path = ":kadre:contracts:validator", configuration = "jvmRuntimeElements"))
}

// Le smoke du consumer instrumenté : assemble, installe et exécute sur le device attaché,
// contre les artefacts kadre PUBLIÉS à HEAD (même mécanique que validateKotlinConsumer).
val kadreRepository = rootProject.layout.buildDirectory.dir("kadre-contract-repository")
val consumerProjectDir = rootProject.file("kadre/consumers/android")
val consumerResultsDir = consumerProjectDir.resolve("build/outputs/androidTest-results/connected")
val evidenceDir = layout.buildDirectory.dir("contract-evidence/android")
val junitDir = layout.buildDirectory.dir("test-results/android")
// Racine du gate : `validateContractEvidence` (exécution JUnit) lit l'artefact à
// `<racine>/contract-evidence/<contractId>.json` et le JUnit associé relativement à la même racine.
val gateEvidenceDir = layout.buildDirectory

/** Le registre et le mapping qui décident des documents canoniques qu'un smoke écrit. */
val contractRegistry = rootProject.file("kadre/contracts/registry/contracts.tsv")
val contractMapping = layout.projectDirectory.file("contracts/evidence.tsv")
val androidContractId = "AND-001"

/**
 * Le commit auquel la preuve canonique appartient.
 *
 * `kadre/contracts/validator/build.gradle.kts` dérive la valeur qu'il valide avec la même règle —
 * l'override `kadreContractCommit`, sinon le HEAD du dépôt — les deux ne peuvent que concorder.
 * C'est une entrée du producteur : un document épinglé à un commit est périmé par définition
 * dès que HEAD avance.
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

// 1. Exécuter le smoke instrumenté du consumer sur le device attaché.
// Le SDK Android — `ANDROID_HOME`, sinon `ANDROID_SDK_ROOT`, sinon `sdk.dir` du local.properties
// racine, sinon l'emplacement par défaut AGP (`~/Library/Android/sdk`) — injecté au producteur :
// l'environnement du daemon Gradle n'est pas celui du shell interactif.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val androidSdkHome = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
    .orElse(providers.provider { localProperties.getProperty("sdk.dir") })
    .orElse(providers.provider { "${System.getProperty("user.home")}/Library/Android/sdk" })
    .map { it.takeUnless(String::isBlank) ?: error("no Android SDK location: set ANDROID_HOME or sdk.dir in local.properties") }

// 1. Exécuter le smoke instrumenté du consumer sur le device attaché — en forkant `gradlew`
//    DANS le répertoire du consumer (l'invocation exacte du Task 5). Le `GradleBuild` imbriqué
//    exécute ici le build enfant DANS le daemon parent et retombe sur un graphe de tâches
//    fantôme (préfixe `:android:`, zéro instrumentation exécutée) : le fork client est
//    l'invocation qui produit réellement le XML connecté.
val androidDeviceSmokeRun = tasks.register<Exec>("androidDeviceSmokeRun") {
    group = "verification"
    description = "Runs the instrumented Android consumer smoke on the attached emulator."
    dependsOn(":kadre:publishContractArtifacts")
    workingDir(consumerProjectDir)
    commandLine(
        rootProject.file("gradlew").absolutePath,
        "connectedDebugAndroidTest",
        "--console=plain",
        // `1.0.0` est une version RELEASE : sans revalidation, le cache de dépendances du
        // consumer servirait un AAR périmé après une republication — le smoke prouverait
        // alors un artefact qui n'est plus celui de HEAD (piège des phases web).
        "--refresh-dependencies",
        "-PkadreRepository=${kadreRepository.get().asFile.absolutePath}",
        "-PkadreVersion=${project.version}",
    )
    // AGP du build enfant : SDK explicite, indépendant de l'environnement du shell.
    environment("ANDROID_HOME", androidSdkHome.get())
}

// 2. Produire les preuves canoniques depuis le XML réel + étiqueter le moteur (adb getprop).
//    `dependsOn` le run réel — jamais `mustRunAfter` (piège documenté des phases web : un gate
//    qui valide des artefacts périmés ne passe que là où quelqu'un a déjà lancé le smoke à HEAD).
tasks.register<JavaExec>("androidDeviceSmoke") {
    group = "verification"
    description = "Runs the Android consumer smoke on the attached emulator and produces the $androidContractId evidence."
    dependsOn(androidDeviceSmokeRun)
    classpath(sourceSets.main.get().runtimeClasspath)
    mainClass.set("org.graphiks.kadre.contracts.android.AndroidContractEvidenceKt")
    environment("ANDROID_HOME", androidSdkHome.get())
    args(
        consumerResultsDir.absolutePath,
        evidenceDir.get().asFile.absolutePath,
        junitDir.get().asFile.absolutePath,
        gateEvidenceDir.get().asFile.absolutePath,
        contractRegistry.absolutePath,
        contractMapping.asFile.absolutePath,
        contractEvidenceCommit.get(),
    )
    inputs.dir(consumerResultsDir)
        .withPathSensitivity(PathSensitivity.NONE)
        .optional()
    inputs.file(contractRegistry)
    inputs.file(contractMapping)
    inputs.property("contractEvidenceCommit", contractEvidenceCommit)
    outputs.dir(evidenceDir)
    outputs.dir(junitDir)
    outputs.dir(gateEvidenceDir.dir("contract-evidence"))
}
