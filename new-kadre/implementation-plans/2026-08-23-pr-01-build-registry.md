# New Kadre PR 1 — Build and Contract Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduire la racine Gradle `kadre-new`, un premier artifact `foundation` JVM réel et le registre contractuel validé qui permettra aux PRs suivantes d'activer leurs preuves progressivement.

**Architecture:** Cette PR n'introduit que les composants possédant déjà une responsabilité effective : l'umbrella, `foundation` et `contracts:validator`. Elle n'utilise pas la convention KMP historique, car celle-ci matérialise Android et iOS ; chaque nouveau projet configure uniquement une target JVM avec JDK 25. Le registre est un TSV relisible sans dépendance de sérialisation et son validateur reste entièrement séparé du code de production.

**Tech Stack:** Kotlin 2.4.0, Kotlin Multiplatform JVM, Gradle Kotlin DSL, JVM/JDK 25, `kotlin.test`, validation ABI Kotlin.

**Spec:** `new-kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md`

## Global Constraints

- Seule la target JVM est créée, compilée et testée dans cette PR.
- Le toolchain et le runtime minimum sont JDK 25.
- Aucun enfant de `kadre-new` ne contient `kadre` dans son propre nom.
- Aucun projet vide n'entre dans `settings.gradle.kts`.
- `foundation` ne dépend d'aucun autre projet Kadre.
- Aucune couche FFI, aucun binding et aucune dépendance KFFI n'entrent dans cette PR.
- L'API publiée utilise `explicitApi()` dès son introduction.
- Le statut contractuel est `planned`, `active` ou `retired`; il ne constitue pas une version.
- Un contrat `planned` ne peut accompagner aucune capability annoncée `Supported`.
- Aucun test tautologique de data class, getter ou enum n'est ajouté.
- Toutes les commandes shell du dépôt sont préfixées par `rtk`.

---

## Position dans la stack

Branche d’exécution : `codex/first-implementation`.

Cette PR est basée sur la branche contenant les spécifications mergées. Les PRs 2 à 8 seront basées successivement sur elle. Aucun module Desktop, runtime, fake ou AppKit n'est créé ici : chacun entrera dans le build dans la PR où il recevra son premier comportement et ses preuves.

Avant Task 1, vérifier que le worktree isolé utilise cette branche :

```bash
rtk git status --short
rtk git branch --show-current
```

Expected: seuls le snapshot et ce plan sont initialement non suivis ; la seconde commande affiche `codex/first-implementation`.

## Arborescence verrouillée par cette PR

```text
settings.gradle.kts                                      modifié
new-kadre/
|-- build.gradle.kts                                    créé
|-- TEST-STRATEGY.md                                    modifié
|-- foundation/
|   |-- build.gradle.kts                                créé
|   |-- api/foundation.api                              généré puis versionné
|   `-- src/commonMain/kotlin/org/graphiks/kadre/diagnostics/
|       `-- OptInAnnotations.kt                         créé
`-- contracts/
    |-- registry/
    |   |-- README.md                                   créé
    |   `-- contracts.tsv                               créé
    `-- validator/
        |-- build.gradle.kts                            créé
        |-- src/jvmMain/kotlin/org/graphiks/kadre/contracts/
        |   |-- ContractRegistry.kt                     créé
        |   `-- ValidateContractRegistry.kt             créé
        `-- src/jvmTest/kotlin/org/graphiks/kadre/contracts/
            `-- ContractRegistryTest.kt                 créé
```

Responsabilité de chaque fichier :

- `new-kadre/build.gradle.kts` configure l'umbrella JVM et agrège les checks des seuls enfants présents.
- `foundation/build.gradle.kts` configure l'artifact contractuel public JVM et son ABI.
- `OptInAnnotations.kt` introduit les trois annotations publiques déjà fermées par la spec.
- `contracts.tsv` est la donnée normative lisible par machine ; il commence avec son header, sans faux contrat actif.
- `ContractRegistry.kt` parse et valide le schéma minimal du registre.
- `ValidateContractRegistry.kt` fournit le point d'entrée utilisé par le gate Gradle.
- `ContractRegistryTest.kt` prouve les seules règles structurelles qui ne peuvent pas être laissées à la code review : unicité, complétude d'un contrat actif et retirement explicite.

---

### Task 1: Amendement du statut contractuel

**Files:**
- Modify: `new-kadre/TEST-STRATEGY.md`
- Create: `new-kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md`
- Create: `new-kadre/implementation-plans/2026-08-23-pr-01-build-registry.md`

**Interfaces:**
- Consumes: le schéma du registre défini en section 3 de `TEST-STRATEGY.md`.
- Produces: les champs `status` et `retirementRef`, ainsi que la règle de complétude limitée aux contrats `active`.

- [ ] **Step 1: Vérifier que le statut progressif est absent**

Run:

```bash
rtk rg -n "planned.*active.*retired|status.*retirementRef" new-kadre/TEST-STRATEGY.md
```

Expected: aucune correspondance décrivant les trois statuts et leur gate.

- [ ] **Step 2: Ajouter les champs au tableau du registre**

Ajouter exactement deux lignes au tableau des champs :

```markdown
| `status` | `planned`, `active` ou `retired` ; ce statut décrit l'activation de la preuve, jamais une version |
| `retirementRef` | référence obligatoire uniquement pour `retired`, `null` autrement |
```

- [ ] **Step 3: Fermer la sémantique des trois statuts**

Ajouter après le tableau :

```markdown
Un contrat `planned` appartient au design fermé mais n'est pas encore promis par le livrable courant. Il ne peut correspondre à aucune capability annoncée `Supported`; l'opération reste explicitement `Unsupported` ou l'artifact concerné n'est pas publié. Un contrat `active` possède toutes les preuves imposées par cette stratégie et entre dans chaque gate applicable. Un contrat `retired` ne possède plus de scénario exécutable et conserve une `retirementRef` non vide vers la décision de suppression. Le passage `planned → active` et `active → retired` est revu dans le même changement que le registre et les preuves concernés.
```

- [ ] **Step 4: Limiter explicitement la complétude aux contrats actifs**

Remplacer l'introduction de la section 3.1 par :

```markdown
Le registre actif est complet uniquement si chaque contrat dont `status == active` satisfait les règles suivantes. Les contrats `planned` restent visibles dans l'audit mais ne produisent aucune preuve factice, aucun skip et aucune capability `Supported`. Les contrats `retired` sont exclus de l'ensemble exécutable et exigent leur référence de retrait.
```

Dans les sections décrivant les gates PR, nightly et release, remplacer toute exigence ambiguë portant sur « tous les contrats » par « tous les contrats `active` requis par la target ». Ne modifier aucune exigence comportementale applicable à un contrat actif.

- [ ] **Step 5: Vérifier le texte normatif**

Run:

```bash
rtk rg -n -C 2 "planned|active|retired|retirementRef" new-kadre/TEST-STRATEGY.md
```

Expected: les champs, transitions, contraintes de capability et règles de gate apparaissent ; aucun statut supplémentaire n'existe.

- [ ] **Step 6: Commit**

```bash
rtk git add new-kadre/TEST-STRATEGY.md new-kadre/APPKIT-JVM-FIRST-IMPLEMENTATION.md new-kadre/implementation-plans/2026-08-23-pr-01-build-registry.md
rtk git commit -m "docs: define AppKit JVM implementation baseline"
```

---

### Task 2: Racine Gradle JVM et premier artifact foundation

**Files:**
- Modify: `settings.gradle.kts`
- Create: `new-kadre/build.gradle.kts`
- Create: `new-kadre/foundation/build.gradle.kts`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/diagnostics/OptInAnnotations.kt`
- Generate: `new-kadre/foundation/api/foundation.api`

**Interfaces:**
- Consumes: plugins `org.jetbrains.kotlin.multiplatform` déjà disponibles depuis `buildSrc`; JDK 25 installé.
- Produces: projets Gradle `:kadre-new` et `:kadre-new:foundation`; annotations `ExperimentalKadreApi`, `KadrePlatformApi` et `DelicateKadreApi`.

- [ ] **Step 1: Prouver que les nouveaux projets n'existent pas encore**

Run:

```bash
rtk ./gradlew projects
```

Expected: aucune entrée `Project ':kadre-new'`.

- [ ] **Step 2: Enregistrer uniquement l'umbrella et foundation**

Ajouter à `settings.gradle.kts` :

```kotlin
include(":kadre-new")
project(":kadre-new").projectDir = file("new-kadre")
include(":kadre-new:foundation")
```

Ne pas inclure `runtime`, `platform`, `backend`, `test`, `contracts:model`, `contracts:suite`, les drivers, consumers ou samples.

- [ ] **Step 3: Créer le build de foundation**

Créer `new-kadre/foundation/build.gradle.kts` :

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(25)
    jvm()
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

Ne pas appliquer `ygdrasil.conventions.kmp-library` : ce plugin ajouterait Android et iOS.

- [ ] **Step 4: Écrire les annotations publiques exactes**

Créer `OptInAnnotations.kt` :

```kotlin
package org.graphiks.kadre.diagnostics

@MustBeDocumented
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalKadreApi

@MustBeDocumented
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class KadrePlatformApi

@MustBeDocumented
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class DelicateKadreApi
```

La compilation et l'ABI constituent la preuve structurelle appropriée ; ne pas ajouter un test runtime de réflexion, car la retention est `BINARY` et ce test serait trompeur.

- [ ] **Step 5: Créer le build de l'umbrella**

Créer `new-kadre/build.gradle.kts` :

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(25)
    jvm()
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(project(":kadre-new:foundation"))
        }
    }
}

tasks.named("check") {
    dependsOn(":kadre-new:foundation:check")
}
```

L'umbrella est volontairement mince : sa responsabilité effective est l'agrégation de la bonne variante.

- [ ] **Step 6: Compiler avant de figer l'ABI**

Run:

```bash
rtk ./gradlew :kadre-new:foundation:compileKotlinJvm :kadre-new:compileKotlinJvm
```

Expected: PASS sous JDK 25 ; aucune task Android, iOS, JS ou Wasm n'est créée pour ces projets.

- [ ] **Step 7: Générer puis vérifier l'ABI**

Run:

```bash
rtk ./gradlew :kadre-new:foundation:updateKotlinAbi
rtk ./gradlew :kadre-new:foundation:checkKotlinAbi
```

Expected: `foundation.api` contient exactement les trois annotations et le check passe.

- [ ] **Step 8: Vérifier la topologie**

Run:

```bash
rtk ./gradlew projects
rtk ./gradlew :kadre-new:tasks --all
```

Expected: seuls `:kadre-new` et `:kadre-new:foundation` existent sous la nouvelle racine ; aucune task liée à une autre target n'apparaît.

- [ ] **Step 9: Commit**

```bash
rtk git add settings.gradle.kts new-kadre/build.gradle.kts new-kadre/foundation
rtk git commit -m "build: add JVM-only new Kadre foundation"
```

---

### Task 3: Parser TDD du registre contractuel

**Files:**
- Modify: `settings.gradle.kts`
- Create: `new-kadre/contracts/validator/build.gradle.kts`
- Create: `new-kadre/contracts/validator/src/jvmTest/kotlin/org/graphiks/kadre/contracts/ContractRegistryTest.kt`
- Create: `new-kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ContractRegistry.kt`

**Interfaces:**
- Consumes: texte TSV UTF-8 avec header fermé.
- Produces: `ContractRegistry.parse(text: String): List<ContractRecord>` et `ContractRegistry.validate(records: List<ContractRecord>): List<String>` internes au validateur.

Le header exact est :

```text
contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef
```

Les listes utilisent `,` comme séparateur et `-` pour l'ensemble vide. Les commentaires commencent par `#` en première colonne.

- [ ] **Step 1: Créer le projet validator et l'inclure avec son premier test**

Ajouter à `settings.gradle.kts` :

```kotlin
include(":kadre-new:contracts:validator")
```

Créer `new-kadre/contracts/validator/build.gradle.kts` :

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(25)
    jvm()

    sourceSets {
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

Créer `ContractRegistryTest.kt` avec le premier scénario :

```kotlin
package org.graphiks.kadre.contracts

import kotlin.test.Test
import kotlin.test.assertEquals

class ContractRegistryTest {
    @Test
    fun parsesAnActiveContractWithEvidenceLists() {
        val text = """
            contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef
            SES-001\tactive\tDESIGN.md#5\tKadreSession\tterminal race\tO2\tstop-wins,failure-wins\tjvm\t-\tlate-resource\t-
        """.trimIndent()

        val record = ContractRegistry.parse(text).single()

        assertEquals("SES-001", record.contractId)
        assertEquals(ContractStatus.Active, record.status)
        assertEquals(listOf("stop-wins", "failure-wins"), record.scenarios)
        assertEquals(listOf("jvm"), record.requiredTargets)
        assertEquals(emptyList(), record.conditionalCapabilities)
    }
}
```

- [ ] **Step 2: Exécuter le test pour observer l'échec**

Run:

```bash
rtk ./gradlew :kadre-new:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ContractRegistryTest.parsesAnActiveContractWithEvidenceLists
```

Expected: FAIL à la compilation, `Unresolved reference 'ContractRegistry'`.

- [ ] **Step 3: Implémenter le modèle et le parser minimaux**

Créer `ContractRegistry.kt` :

```kotlin
package org.graphiks.kadre.contracts

internal enum class ContractStatus {
    Planned,
    Active,
    Retired;

    companion object {
        fun parse(value: String): ContractStatus = when (value) {
            "planned" -> Planned
            "active" -> Active
            "retired" -> Retired
            else -> error("unknown contract status: $value")
        }
    }
}

internal enum class ContractOracle { O1, O2, O3, O4 }

internal data class ContractRecord(
    val contractId: String,
    val status: ContractStatus,
    val source: String,
    val subject: String,
    val risk: String,
    val oracle: ContractOracle,
    val scenarios: List<String>,
    val requiredTargets: List<String>,
    val conditionalCapabilities: List<String>,
    val sentinels: List<String>,
    val retirementRef: String?,
)

internal object ContractRegistry {
    private const val Header =
        "contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef"

    fun parse(text: String): List<ContractRecord> {
        val lines = text.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") }
            .toList()
        require(lines.firstOrNull() == Header) { "invalid contract registry header" }
        return lines.drop(1).mapIndexed { index, line ->
            val columns = line.split('\t')
            require(columns.size == 11) { "line ${index + 2}: expected 11 columns" }
            ContractRecord(
                contractId = columns[0],
                status = ContractStatus.parse(columns[1]),
                source = columns[2],
                subject = columns[3],
                risk = columns[4],
                oracle = ContractOracle.valueOf(columns[5]),
                scenarios = columns[6].asRegistryList(),
                requiredTargets = columns[7].asRegistryList(),
                conditionalCapabilities = columns[8].asRegistryList(),
                sentinels = columns[9].asRegistryList(),
                retirementRef = columns[10].takeUnless { it == "-" },
            )
        }
    }

    private fun String.asRegistryList(): List<String> =
        if (this == "-") emptyList() else split(',').map(String::trim)
}
```

- [ ] **Step 4: Exécuter le test vert**

Run:

```bash
rtk ./gradlew :kadre-new:contracts:validator:jvmTest --tests org.graphiks.kadre.contracts.ContractRegistryTest.parsesAnActiveContractWithEvidenceLists
```

Expected: PASS.

- [ ] **Step 5: Ajouter les scénarios invalides significatifs**

Compléter `ContractRegistryTest.kt` :

```kotlin
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val HEADER =
    "contractId\tstatus\tsource\tsubject\trisk\toracle\tscenarios\trequiredTargets\tconditionalCapabilities\tsentinels\tretirementRef"

@Test
fun rejectsUnknownStatusBeforeValidation() {
    val failure = assertFailsWith<IllegalStateException> {
        ContractRegistry.parse(
            "$HEADER\nSES-001\tdraft\tDESIGN.md#5\tKadreSession\trisk\tO2\tcase\tjvm\t-\tsentinel\t-"
        )
    }
    assertContains(failure.message.orEmpty(), "unknown contract status")
}

@Test
fun activeContractRequiresScenariosTargetsAndSentinels() {
    val records = ContractRegistry.parse(
        "$HEADER\nSES-001\tactive\tDESIGN.md#5\tKadreSession\trisk\tO2\t-\t-\t-\t-\t-"
    )
    val errors = ContractRegistry.validate(records)
    assertTrue(errors.any { "scenarios" in it })
    assertTrue(errors.any { "requiredTargets" in it })
    assertTrue(errors.any { "sentinels" in it })
}

@Test
fun duplicateContractIdsAreRejected() {
    val record = "SES-001\tplanned\tDESIGN.md#5\tKadreSession\trisk\tO2\t-\t-\t-\t-\t-"
    val errors = ContractRegistry.validate(ContractRegistry.parse("$HEADER\n$record\n$record"))
    assertEquals(listOf("duplicate contractId: SES-001"), errors)
}

@Test
fun retiredContractRequiresReferenceAndNoExecutableScenarios() {
    val records = ContractRegistry.parse(
        "$HEADER\nSES-001\tretired\tDESIGN.md#5\tKadreSession\trisk\tO2\told-case\tjvm\t-\tsentinel\t-"
    )
    val errors = ContractRegistry.validate(records)
    assertTrue(errors.any { "retirementRef" in it })
    assertTrue(errors.any { "must not keep executable evidence" in it })
}
```

- [ ] **Step 6: Exécuter les nouveaux tests pour observer l'échec**

Run:

```bash
rtk ./gradlew :kadre-new:contracts:validator:jvmTest
```

Expected: FAIL à la compilation, `Unresolved reference 'validate'`.

- [ ] **Step 7: Implémenter les règles de validation fermées**

Ajouter à `ContractRegistry` :

```kotlin
private val ContractId = Regex("[A-Z]{3}-[0-9]{3}")

fun validate(records: List<ContractRecord>): List<String> = buildList {
    records.groupingBy { it.contractId }.eachCount()
        .filterValues { it > 1 }
        .keys.sorted()
        .forEach { add("duplicate contractId: $it") }

    records.forEach { record ->
        if (!ContractId.matches(record.contractId)) {
            add("${record.contractId}: invalid contractId")
        }
        if (record.source.isBlank()) add("${record.contractId}: source is required")
        if (record.subject.isBlank()) add("${record.contractId}: subject is required")
        if (record.risk.isBlank()) add("${record.contractId}: risk is required")

        when (record.status) {
            ContractStatus.Planned -> {
                if (record.retirementRef != null) {
                    add("${record.contractId}: planned contract cannot have retirementRef")
                }
            }
            ContractStatus.Active -> {
                if (record.scenarios.isEmpty()) add("${record.contractId}: scenarios are required")
                if (record.requiredTargets.isEmpty()) add("${record.contractId}: requiredTargets are required")
                if (record.sentinels.isEmpty()) add("${record.contractId}: sentinels are required")
                if (record.retirementRef != null) {
                    add("${record.contractId}: active contract cannot have retirementRef")
                }
            }
            ContractStatus.Retired -> {
                if (record.retirementRef.isNullOrBlank()) {
                    add("${record.contractId}: retirementRef is required")
                }
                if (record.scenarios.isNotEmpty() ||
                    record.requiredTargets.isNotEmpty() ||
                    record.sentinels.isNotEmpty()
                ) {
                    add("${record.contractId}: retired contract must not keep executable evidence")
                }
            }
        }
    }
}
```

- [ ] **Step 8: Exécuter toute la suite du validator**

Run:

```bash
rtk ./gradlew :kadre-new:contracts:validator:jvmTest
```

Expected: PASS, quatre scénarios significatifs en plus du parsing positif.

- [ ] **Step 9: Commit**

```bash
rtk git add settings.gradle.kts new-kadre/contracts/validator
rtk git commit -m "test: add contract registry validator"
```

---

### Task 4: Registre réel et gate Gradle

**Files:**
- Create: `new-kadre/contracts/registry/contracts.tsv`
- Create: `new-kadre/contracts/registry/README.md`
- Create: `new-kadre/contracts/validator/src/jvmMain/kotlin/org/graphiks/kadre/contracts/ValidateContractRegistry.kt`
- Modify: `new-kadre/contracts/validator/build.gradle.kts`
- Modify: `new-kadre/build.gradle.kts`

**Interfaces:**
- Consumes: `ContractRegistry.parse` et `ContractRegistry.validate` de Task 3.
- Produces: task Gradle `:kadre-new:contracts:validator:validateContractRegistry`, appelée par `:kadre-new:check`.

- [ ] **Step 1: Créer un registre vide mais valide**

Créer `contracts.tsv` avec une seule ligne :

```text
contractId	status	source	subject	risk	oracle	scenarios	requiredTargets	conditionalCapabilities	sentinels	retirementRef
```

Aucun contrat artificiel n'est ajouté uniquement pour rendre la PR verte. Les premières entrées `active` seront ajoutées avec leurs preuves dans la PR 2.

- [ ] **Step 2: Documenter le format éditorial**

Créer `contracts/registry/README.md` :

```markdown
# Registre contractuel New Kadre

`contracts.tsv` est la source lisible par machine des contrats `planned`, `active` et `retired` définis par `TEST-STRATEGY.md`.

- Une ligne par `contractId` stable.
- Tabulation entre colonnes ; virgule entre valeurs d'une liste.
- `-` représente une liste vide ou une `retirementRef` absente.
- Aucun texte utilisateur ni tabulation n'entre dans une cellule.
- Une entrée devient `active` dans le même commit que ses scénarios, preuves target-specific et sentinelles.
- Une entrée devient `retired` dans le même commit que la suppression de ses scénarios et l'ajout de sa référence de retrait.

Le validateur vérifie uniquement la structure que la CI doit compter. La correspondance sémantique avec les specs et les capabilities reste une responsabilité de code review et de contract suite.
```

- [ ] **Step 3: Écrire le point d'entrée de validation**

Créer `ValidateContractRegistry.kt` :

```kotlin
package org.graphiks.kadre.contracts

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

internal fun validateContractRegistry(path: Path): List<String> {
    require(Files.isRegularFile(path)) { "contract registry does not exist: $path" }
    return ContractRegistry.validate(ContractRegistry.parse(path.readText()))
}

public fun main(args: Array<String>) {
    require(args.size == 1) { "expected the contract registry path" }
    val errors = validateContractRegistry(Path.of(args.single()))
    check(errors.isEmpty()) { errors.joinToString(separator = "\n") }
}
```

Le `main` est techniquement public pour le launcher JVM de cet artifact interne ; il n'appartient à aucune API Kadre publiée.

- [ ] **Step 4: Ajouter le test de fichier absent avant de câbler Gradle**

Ajouter à `ContractRegistryTest.kt` :

```kotlin
import java.nio.file.Path

@Test
fun missingRegistryFileIsRejected() {
    val failure = assertFailsWith<IllegalArgumentException> {
        validateContractRegistry(Path.of("does-not-exist", "contracts.tsv"))
    }
    assertContains(failure.message.orEmpty(), "does not exist")
}
```

- [ ] **Step 5: Exécuter le test du point d'entrée**

Run:

```bash
rtk ./gradlew :kadre-new:contracts:validator:jvmTest
```

Expected: PASS.

- [ ] **Step 6: Câbler la task Gradle**

Ajouter à `contracts/validator/build.gradle.kts` :

```kotlin
val validateContractRegistry by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates the New Kadre machine-readable contract registry."
    dependsOn("jvmMainClasses")
    classpath = configurations.named("jvmRuntimeClasspath").get()
    mainClass.set("org.graphiks.kadre.contracts.ValidateContractRegistryKt")
    args(rootProject.file("new-kadre/contracts/registry/contracts.tsv").absolutePath)
}

tasks.named("check") {
    dependsOn(validateContractRegistry)
}
```

Compléter `new-kadre/build.gradle.kts` :

```kotlin
tasks.named("check") {
    dependsOn(":kadre-new:contracts:validator:check")
}
```

- [ ] **Step 7: Prouver que le registre réel passe**

Run:

```bash
rtk ./gradlew :kadre-new:contracts:validator:validateContractRegistry
```

Expected: PASS sans warning ni entrée synthétique.

- [ ] **Step 8: Commit**

```bash
rtk git add new-kadre/build.gradle.kts new-kadre/contracts/registry new-kadre/contracts/validator
rtk git commit -m "build: gate the contract registry"
```

---

### Task 5: Vérification intégrée et préparation de la PR

**Files:**
- Verify only; no production file should be added in this task.

**Interfaces:**
- Consumes: tous les livrables des Tasks 1 à 4.
- Produces: une branche PR 1 propre, vérifiée et prête à recevoir la PR 2 comme branche fille.

- [ ] **Step 1: Exécuter le gate de la nouvelle racine**

Run:

```bash
rtk ./gradlew :kadre-new:check :kadre-new:foundation:checkKotlinAbi
```

Expected: PASS ; les tests du validator et la validation du registre sont exécutés transitivement.

- [ ] **Step 2: Vérifier la target et le toolchain**

Run:

```bash
rtk ./gradlew :kadre-new:foundation:kotlinToolingMetadata
rtk ./gradlew :kadre-new:foundation:tasks --all
```

Expected: seule la compilation JVM est configurée pour `foundation`; aucune target Android, Native, JS ou Wasm.

- [ ] **Step 3: Vérifier l'absence de dépendance interdite**

Run:

```bash
rtk rg -n "kffi|kadre-core|kadre-appkit|androidTarget|iosArm64|iosX64|wasmJs|js\s*\{" new-kadre --glob '*.kts' --glob '*.kt'
```

Expected: aucune correspondance dans les nouveaux build scripts et sources ; les mentions documentaires du snapshot ne sont pas incluses par les globs.

- [ ] **Step 4: Vérifier le diff**

Run:

```bash
rtk git diff --check
rtk git status --short
rtk git diff --stat HEAD~4..HEAD
```

Expected: aucun whitespace error ; uniquement les fichiers listés par ce plan et les quatre commits attendus.

- [ ] **Step 5: Vérifier l'historique de la PR**

Run:

```bash
rtk git log --oneline --decorate -5
```

Expected, dans l'ordre inverse :

```text
build: gate the contract registry
test: add contract registry validator
build: add JVM-only new Kadre foundation
docs: define AppKit JVM implementation baseline
```

- [ ] **Step 6: Push et création de la PR**

```bash
rtk git push -u origin codex/first-implementation
rtk gh pr create \
  --base "$(rtk gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name')" \
  --head codex/first-implementation \
  --title "build: bootstrap New Kadre JVM contracts" \
  --body $'## Summary\n- bootstrap the JVM-only New Kadre umbrella and foundation\n- define progressive contract activation in the normative test strategy\n- add and gate the machine-readable contract registry validator\n\n## Verification\n- ./gradlew :kadre-new:check :kadre-new:foundation:checkKotlinAbi'
```

La PR 1 cible la branche par défaut, qui contient déjà les spécifications mergées. Si ce prérequis est faux au moment de l'exécution, arrêter avant le push et faire corriger la base plutôt que créer une PR incohérente.

## Critères de sortie de PR 1

- `:kadre-new` et `:kadre-new:foundation` sont les seuls nouveaux projets publiables.
- `:kadre-new:contracts:validator` est interne et possède une responsabilité effective.
- Aucun autre sous-projet réservé n'existe physiquement dans le build.
- Les trois annotations publiques correspondent exactement à `DESIGN.md`.
- `foundation` utilise `explicitApi()` et possède un dump ABI revu.
- Le registre machine-readable accepte les trois statuts fermés.
- Un contrat actif incomplet et un ID dupliqué font échouer le validator.
- Le registre réel ne contient aucun faux contrat actif.
- `:kadre-new:check` agrège le validator.
- Toute la nouvelle racine utilise uniquement JVM/JDK 25.

## Handoff vers PR 2

La branche PR 2 sera créée depuis `codex/first-implementation`. Elle étendra `foundation` avec le catalogue public commun/Desktop, ajoutera les invariants non triviaux et introduira les premières entrées `API-*` actives avec leurs consumer compile tests. Elle ne doit pas modifier la sémantique du registre définie ici sans un amendement normatif séparé.
