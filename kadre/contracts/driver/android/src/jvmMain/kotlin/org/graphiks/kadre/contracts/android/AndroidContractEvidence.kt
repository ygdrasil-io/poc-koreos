package org.graphiks.kadre.contracts.android

import java.io.File

/**
 * Le producteur de preuves du driver Android (phase 0).
 *
 * Lit les XML JUnit produits par le test instrumenté du consumer, détecte l'identité du device
 * (`adb shell getprop ro.build.version.sdk` + `ro.product.cpu.abi` — jamais étiquetée à la main),
 * copie le XML sous `test-results/android/<engine>/` et écrit les JSON canoniques sous
 * `contract-evidence/android/<engine>/` via le générateur du validateur
 * (`GenerateContractEvidenceKt.main` — les mêmes classes, le même mainClass et les mêmes huit
 * arguments que les producteurs runtime/AppKit ; aucune réimplémentation du format).
 *
 * Une seconde copie de chaque document est écrite à `<gateRoot>/contract-evidence/<contractId>.json` :
 * l'emplacement exact que `validateContractEvidence` (exécution JUnit) lit — l'artefact relatif
 * `contract-evidence/<contractId>.json` et le JUnit associé `test-results/android/<engine>`
 * résolus depuis la même racine.
 *
 * [args] : répertoire des résultats connectés du consumer, racine des preuves
 * (`contract-evidence/android`), racine des copies JUnit (`test-results/android`), racine du
 * gate (le `build` du driver), registre, mapping, commit.
 */
fun main(args: Array<String>) {
    require(args.size == 7) {
        "expected consumer results, evidence root, JUnit root, gate root, registry, mapping and commit arguments"
    }
    val consumerResultsDir = File(args[0])
    val evidenceDir = File(args[1])
    val junitDir = File(args[2])
    val gateDir = File(args[3])
    val registry = File(args[4])
    val mapping = File(args[5])
    val commit = args[6]

    // Phase 0 : AND-001 est l'unique contrat de target `android` ; le générateur rejette de toute
    // façon un registre/mapping qui ne déclarerait pas exactement ce couple.
    val contractId = "AND-001"
    val target = "android"
    val adapter = "android-emulator"

    val adb = androidDebugBridge()
    val serial = System.getenv("ANDROID_SERIAL")?.takeUnless(String::isBlank)
    fun prop(name: String): String {
        val command = buildList {
            add(adb.absolutePath)
            if (serial != null) addAll(listOf("-s", serial))
            addAll(listOf("shell", "getprop", name))
        }
        val process = ProcessBuilder(command).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exit = process.waitFor()
        check(exit == 0 && output.isNotBlank()) { "adb getprop $name failed (exit $exit)" }
        return output
    }

    // L'étiquette moteur vient du device réellement attaché — jamais codée en dur.
    val sdk = prop("ro.build.version.sdk")
    val abi = prop("ro.product.cpu.abi")
    val engine = "emulator-api-$sdk-$abi"

    // Le nom du fichier XML varie avec l'AVD/device : GLOB sur TEST-*.xml.
    val xmlFiles = consumerResultsDir.walkTopDown()
        .filter { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" }
        .toList()
    require(xmlFiles.isNotEmpty()) {
        "no JUnit XML found under ${consumerResultsDir.path} — the instrumented smoke did not run"
    }

    // Répertoire du moteur réinitialisé : les copies JUnit sont exactement celles de CE run
    // (un XML périmé d'un run antérieur redéclarerait des testcase et casserait l'identité).
    val targetJunit = File(junitDir, engine).apply { deleteRecursively(); mkdirs() }
    val targetEvidence = File(evidenceDir, engine).apply { mkdirs() }
    for (xml in xmlFiles) xml.copyTo(File(targetJunit, xml.name), overwrite = true)

    // Génération des JSON canoniques : mêmes classes que les producteurs runtime/AppKit du
    // validateur (registre, mapping, répertoires JUnit, sortie, commit, contractId, target, adapter).
    val output = File(targetEvidence, "$contractId.json")
    invokeGenerateContractEvidence(
        registry = registry.absolutePath,
        mapping = mapping.absolutePath,
        junitDirectories = targetJunit.absolutePath,
        output = output.absolutePath,
        commit = commit,
        contractId = contractId,
        target = target,
        adapter = adapter,
    )

    // Copie à l'emplacement du gate JUnit (`validateContractEvidence`).
    val gateOutput = File(File(gateDir, "contract-evidence"), "$contractId.json")
    gateOutput.parentFile.mkdirs()
    output.copyTo(gateOutput, overwrite = true)

    // La liste des moteurs réellement producteurs, consommée par le gate du validateur.
    File(evidenceDir, "engines.txt").writeText(engine)
    println("android evidence engine: $engine")
    println("android evidence artifact: ${output.path}")
}

/**
 * Invoque `org.graphiks.kadre.contracts.GenerateContractEvidenceKt.main` — le point d'entrée
 * public du générateur du validateur — avec son contrat exact à huit arguments.
 *
 * L'appel passe par le nom de classe JVM (comme le `mainClass` d'un JavaExec) : Kotlin 2.4 ne
 * résout plus les imports de classes façades (`…Kt`) dans le scope Kotlin, et les surcharges
 * `generateContractEvidence` restent `internal` au validateur. La classe et ses dépendances
 * (kotlinx.serialization) viennent du classpath de CE processus — celui du JavaExec, câblé sur
 * `jvmRuntimeElements` du validateur.
 */
private fun invokeGenerateContractEvidence(
    registry: String,
    mapping: String,
    junitDirectories: String,
    output: String,
    commit: String,
    contractId: String,
    target: String,
    adapter: String,
) {
    val facade = Class.forName("org.graphiks.kadre.contracts.GenerateContractEvidenceKt")
    val main = facade.getDeclaredMethod("main", Array<String>::class.java)
    val arguments = arrayOf(
        registry,
        mapping,
        junitDirectories,
        output,
        commit,
        contractId,
        target,
        adapter,
    )
    main.invoke(null, arguments)
}

/**
 * L'adb du SDK — `ANDROID_HOME`, sinon `ANDROID_SDK_ROOT`. `ANDROID_SERIAL`, s'il est défini,
 * sélectionne le device (plusieurs émulateurs attachés).
 */
private fun androidDebugBridge(): File {
    val home = System.getenv("ANDROID_HOME")?.takeUnless(String::isBlank)
        ?: System.getenv("ANDROID_SDK_ROOT")?.takeUnless(String::isBlank)
        ?: error("ANDROID_HOME is required")
    val adb = listOf(home, "platform-tools", "adb").joinToString(File.separator)
    return File(adb).also { require(it.isFile) { "adb not found at ${it.path}" } }
}

