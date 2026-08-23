# New Kadre Foundation API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Sub-agents are forbidden in the side-conversation workspace. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** publier sur JVM 25 le catalogue Kotlin commun et Desktop de New Kadre, avec ses invariants locaux, ses profils de policy exacts, son ABI et des consumer compile tests autonomes Kotlin/Java.

**Architecture:** `foundation` possède exclusivement le value model, les contrats coroutine publics et le Host SPI commun. `platform:desktop` possède uniquement les options et entry points Desktop ; avant l'arrivée du runtime/provider, ses appels échouent honnêtement avec `Unsupported(HostAttach)`. L'umbrella agrège les deux variants, tandis que les consumers résolvent leurs publications depuis un repository Maven temporaire.

**Tech Stack:** Kotlin Multiplatform 2.4.0, kotlinx.coroutines 1.10.1, Gradle 9.5, JDK 25, Kotlin ABI validation, kotlin-test, Maven Publish.

**Spec:** `new-kadre/DESIGN.md`, `new-kadre/PUBLIC-API-CATALOG.md`, `new-kadre/POLICY-PROFILES.md`, `new-kadre/BACKEND-CAPABILITIES.md`, `new-kadre/INTEROP-EXPORTS.md`, `new-kadre/PROJECT-ARCHITECTURE.md`, `new-kadre/TEST-STRATEGY.md`.

## Global Constraints

- La branche `codex/foundation-api` part du head de la PR 1 `codex/first-implementation`; la PR 2 cible cette branche, jamais `master`.
- Seules les targets metadata commune et JVM/JDK 25 existent dans ce chantier.
- Tous les artifacts contractuels utilisent `explicitApi()` et un dump ABI revu.
- `foundation` n'importe aucun SDK host, runtime Kadre, backend, KFFI ou type `org.graphiks.kadre.internal.*`.
- `platform:desktop` dépend uniquement de `foundation` dans cette PR ; aucun provider, `ServiceLoader`, runtime ou fallback n'est simulé.
- Les signatures sont copiées littéralement depuis `PUBLIC-API-CATALOG.md`, complétées uniquement par celles que ce catalogue délègue explicitement à `DESIGN.md`.
- Les collections d'un value object construit localement sont revalidées et copiées à l'admission par leur futur owner. Toutes les collections publiées par Kadre seront déjà owned et immuables ; cette PR ne prétend pas rendre magiquement immuable une `MutableList` conservée par le caller d'un constructeur de `data class`.
- Les constructeurs vérifient seulement les invariants intrinsèques indépendants de `KadrePolicy`; les limites liées à une policy appartiennent à l'admission runtime.
- Aucun test historique n'entre dans la gate. Les seules commandes de sortie commencent par `:kadre-new:` ou exécutent les consumers autonomes de cette PR.
- Aucun test de réflexion tautologique n'est ajouté : l'ABI prouve la forme, les consumers prouvent l'utilisabilité et les tests runtime couvrent seulement les invariants qui peuvent réellement régresser.

---

### Task 1: Étendre le build contractuel et ouvrir le premier cycle TDD

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `new-kadre/build.gradle.kts`
- Modify: `new-kadre/foundation/build.gradle.kts`
- Create: `new-kadre/platform/desktop/build.gradle.kts`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/diagnostics/KadreResultTest.kt`

**Interfaces:**
- Consumes: `libs.kotlinx.coroutines.core`, plugin `maven-publish`, repository Maven temporaire sous `build/new-kadre-contract-repository`.
- Produces: projets `:kadre-new:platform:desktop`; publication JVM de `kadre-new`, `foundation` et `desktop`; dépendance API coroutines de `foundation`.

- [ ] **Step 1: Écrire le premier test rouge de résultat**

```kotlin
package org.graphiks.kadre.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KadreResultTest {
    @Test
    fun mapPreservesFailureAndDoesNotInvokeTransform() {
        val failure = KadreFailure.Closed(KadreResourceKind.Host)
        var invoked = false
        val result = KadreResult.Failure(failure).map {
            invoked = true
            Unit
        }
        assertEquals(false, invoked)
        assertEquals(KadreResult.Failure(failure), result)
    }

    @Test
    fun getOrThrowKeepsTheFailureIdentity() {
        val failure = KadreFailure.ApplicationFailure
        val exception = assertFailsWith<KadreException> {
            KadreResult.Failure(failure).getOrThrow()
        }
        assertEquals(failure, exception.failure)
    }
}
```

- [ ] **Step 2: Prouver le rouge**

Run:

```bash
rtk ./gradlew :kadre-new:foundation:jvmTest --tests org.graphiks.kadre.diagnostics.KadreResultTest
```

Expected: FAIL à la compilation sur `KadreResult`, `KadreFailure` et `KadreResourceKind` absents.

- [ ] **Step 3: Configurer les modules sans ajouter de target prématurée**

Ajouter `include(":kadre-new:platform:desktop")`. Dans `foundation`, ajouter `api(libs.kotlinx.coroutines.core)` à `commonMain`. Appliquer `maven-publish` aux trois artifacts contractuels, pointer leur repository `contractTest` vers `rootProject.layout.buildDirectory.dir("new-kadre-contract-repository")`, et activer `explicitApi()`/`abiValidation()` dans `platform:desktop`. L'umbrella expose `api(project(":kadre-new:platform:desktop"))` et agrège son `check`.

- [ ] **Step 4: Vérifier la nouvelle topologie**

Run:

```bash
rtk ./gradlew projects
rtk ./gradlew :kadre-new:platform:desktop:tasks --all
```

Expected: `platform:desktop` existe avec JVM uniquement ; aucun autre enfant réservé n'apparaît.

---

### Task 2: Diagnostics, IDs, temps et géométrie

**Files:**
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/diagnostics/Results.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/diagnostics/Capabilities.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/diagnostics/Diagnostics.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/application/Identity.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/surface/Geometry.kt`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/surface/GeometryTest.kt`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/application/EventStampTest.kt`

**Interfaces:**
- Produces: `KadreResult`, failures, operations/resources/platforms, capabilities, diagnostic types, opaque IDs/revisions/timestamps, geometry, `PropertyChange`, images.
- Consumes later: tous les packages de domaine et les tests consumers.

- [ ] **Step 1: Ajouter les tests rouges des invariants numériques**

Les tests couvrent exactement : géométrie finie et `-0.0` canonicalisé, tailles strictement positives, insets positifs ou nuls, scale strictement positif, arrondis `Floor/Ceil/NearestTiesToEven/TowardZero`, overflow `Int`, `EventDeliverySpan` invalide, `TextDocumentRevision` négative, copie défensive de `BinaryImage.bytes`, longueur RGBA exacte et hotspot de `CursorImage`.

```kotlin
@Test fun nearestTiesToEvenIsDeterministic() {
    assertEquals(PhysicalPoint(2, 4), LogicalPoint(1.25, 1.75).toPhysical(2.0))
}

@Test fun binaryImageOwnsItsBytes() {
    val bytes = ByteArray(4) { it.toByte() }
    val image = BinaryImage(bytes, ImageFormat.Rgba8, PhysicalSize(1, 1))
    bytes[0] = 99
    assertEquals(0, image.bytes[0])
    val read = image.bytes
    read[1] = 99
    assertEquals(1, image.bytes[1])
}
```

- [ ] **Step 2: Prouver le rouge**

Run: `rtk ./gradlew :kadre-new:foundation:jvmTest`

Expected: FAIL à la compilation sur les nouvelles primitives.

- [ ] **Step 3: Implémenter les déclarations fermées**

Implémenter littéralement les sections `DESIGN.md` 7.1, 9.7, 12 et 13, ainsi que `PUBLIC-API-CATALOG.md` 3 et 8. Les IDs opaques utilisent une classe à constructeur `internal`, champ privé, égalité/hachage par valeur et `toString()` sous la forme stable `TypeName(<redacted>)`. Les révisions refusent une valeur négative. Les helpers de `KadreResult` branchent exhaustivement sans capturer les exceptions des lambdas.

- [ ] **Step 4: Passer les tests du noyau de valeurs**

Run: `rtk ./gradlew :kadre-new:foundation:jvmTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add new-kadre/foundation settings.gradle.kts new-kadre/build.gradle.kts new-kadre/platform/desktop/build.gradle.kts
rtk git commit -m "feat: add New Kadre value foundation"
```

---

### Task 3: Policies fermées et profils exacts

**Files:**
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/policy/DeliveryPolicies.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/policy/KadrePolicy.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/policy/KadrePolicies.kt`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/policy/KadrePoliciesTest.kt`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/policy/PolicyValidationTest.kt`

**Interfaces:**
- Produces: toutes les déclarations du package `org.graphiks.kadre.policy`, y compris les trois values exactes `Default`, `Realtime`, `Recording`.
- Consumes: `Duration`, `CancellationException` et les enums diagnostics déjà créées.

- [ ] **Step 1: Tester les empreintes complètes avant implémentation**

Construire dans le test trois `KadrePolicy` expected en recopiant chaque cellule de `POLICY-PROFILES.md`, puis vérifier l'égalité avec les profils intégrés. Les expected comprennent les quinze champs `ResourceBudgetPolicy`; aucune assertion partielle ou réflexion n'est admise.

- [ ] **Step 2: Tester chaque classe d'invariant**

Cas minimaux distincts : capacité `0`, budget `Long` nul, collectors/flow supérieurs au total session, chunk/image supérieurs au payload retenu, buffer capture nul, `Buffered(0, ...)`, timeout nul/négatif/infini et diagnostic buffer nul.

- [ ] **Step 3: Prouver le rouge**

Run: `rtk ./gradlew :kadre-new:foundation:jvmTest --tests 'org.graphiks.kadre.policy.*'`

Expected: FAIL à la compilation sur `KadrePolicy` et ses composants.

- [ ] **Step 4: Implémenter les policies et profils**

Recopier les signatures de `DESIGN.md` section 8. Chaque constructeur exécute ses invariants dans `init`; les trois profils utilisent les nombres entiers exacts de `POLICY-PROFILES.md`. Aucun profil ne partage d'objet mutable et aucune normalisation n'est faite.

- [ ] **Step 5: Passer la suite policy**

Run: `rtk ./gradlew :kadre-new:foundation:jvmTest --tests 'org.graphiks.kadre.policy.*'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
rtk git add new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/policy new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/policy
rtk git commit -m "feat: define immutable Kadre policy profiles"
```

---

### Task 4: Catalogue application, surfaces, displays, fenêtres et interactions

**Files:**
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/application/Application.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/application/Lifecycle.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/surface/Surface.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/display/Display.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/window/Window.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/interaction/Interaction.kt`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/window/WindowValuesTest.kt`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/interaction/InteractionValuesTest.kt`

**Interfaces:**
- Produces: toutes les déclarations des six packages listées dans l'index fermé, hors implémentations runtime.
- Consumes: coroutines `CoroutineScope`, `Flow`, `StateFlow`; diagnostics, policy, geometry et IDs des Tasks 2–3.

- [ ] **Step 1: Écrire les tests rouges des invariants réellement locaux**

Tester `WindowSpec`/`LogicalSizeRange` min-max, content size hors intervalle, increments positifs, `WindowSpecBuilder` via l'extension `requestWindow(configure)` avec un manager spy, `InteractionArmOptions.expiresAfter` et non-vacuité des sets `ArmedInteractionConstraints`.

- [ ] **Step 2: Prouver le rouge**

Run: `rtk ./gradlew :kadre-new:foundation:jvmTest --tests 'org.graphiks.kadre.window.*' --tests 'org.graphiks.kadre.interaction.*'`

Expected: FAIL à la compilation sur les types de domaine.

- [ ] **Step 3: Implémenter les signatures sans implémentation fictive**

Recopier les sections `DESIGN.md` 5–7 et 9, puis les compléments exacts de `PUBLIC-API-CATALOG.md` 4–5. Les interfaces ne possèdent aucune classe concrète. Les events dont la révision est dérivée exposent un getter calculé, jamais un second champ stocké. Le builder porte exactement les defaults de `WindowSpec()` et l'extension l'exécute avant d'appeler `requestWindow(builder.build())`.

- [ ] **Step 4: Passer les tests du groupe**

Run: `rtk ./gradlew :kadre-new:foundation:jvmTest`

Expected: PASS.

- [ ] **Step 5: Vérifier l'ABI intermédiaire**

Run:

```bash
rtk ./gradlew :kadre-new:foundation:updateKotlinAbi
rtk ./gradlew :kadre-new:foundation:checkKotlinAbi
```

Expected: PASS ; aucun package interne ou SDK n'apparaît.

- [ ] **Step 6: Commit**

```bash
rtk git add new-kadre/foundation
rtk git commit -m "feat: add application window and surface contracts"
```

---

### Task 5: Catalogue input, gamepad, IME, drop et capture

**Files:**
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/input/Devices.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/input/SurfaceInput.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/input/Gamepad.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/input/TextDropRaw.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/capture/Capture.kt`
- Create: `new-kadre/foundation/src/commonMain/kotlin/org/graphiks/kadre/capture/Frames.kt`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/input/InputValuesTest.kt`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/input/GamepadValuesTest.kt`
- Test: `new-kadre/foundation/src/commonTest/kotlin/org/graphiks/kadre/capture/CaptureValuesTest.kt`

**Interfaces:**
- Produces: le reste du catalogue commun fermé, sans fake ni backend.
- Consumes: primitives/revisions, `KadreResult`, capabilities, policy, `Flow`/`StateFlow`.

- [ ] **Step 1: Tester les invariants input/gamepad/capture significatifs**

Cas obligatoires : HID hors `0..65535`, logical character vide, pression/tilt/twist hors domaine, combinaison `Gesture` incohérente, descriptor gamepad dupliqué, valeur de contrôle hors domaine, effet ou cadence non positive/infinie, `TextRange` invalide, sélection IME hors texte, MIME non canonique, format opaque invalide, chromaticité/HDR incohérentes, cadence variable inversée et layouts de planes trop petits.

- [ ] **Step 2: Prouver le rouge**

Run: `rtk ./gradlew :kadre-new:foundation:jvmTest`

Expected: FAIL à la compilation sur les types input/capture.

- [ ] **Step 3: Implémenter les signatures et invariants fermés**

Recopier `DESIGN.md` sections 10–11 et `PUBLIC-API-CATALOG.md` sections 6–7. Les events sont exhaustifs, les owners ne possèdent que leurs opérations documentées et aucun `Flow` ne contient une ressource closeable. `CopiedPixelPlane` garde son constructeur `internal`; aucune implémentation de frame n'entre dans `foundation`.

- [ ] **Step 4: Passer toute la suite foundation**

Run: `rtk ./gradlew :kadre-new:foundation:jvmTest`

Expected: PASS.

- [ ] **Step 5: Régénérer et vérifier l'ABI complet**

Run:

```bash
rtk ./gradlew :kadre-new:foundation:updateKotlinAbi
rtk ./gradlew :kadre-new:foundation:checkKotlinAbi
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
rtk git add new-kadre/foundation
rtk git commit -m "feat: complete the common Kadre API catalog"
```

---

### Task 6: Surface Desktop honnêtement indisponible avant runtime

**Files:**
- Create: `new-kadre/platform/desktop/src/jvmMain/kotlin/org/graphiks/kadre/platform/desktop/DesktopHost.kt`
- Generate: `new-kadre/platform/desktop/api/desktop.api`
- Test: `new-kadre/platform/desktop/src/jvmTest/kotlin/org/graphiks/kadre/platform/desktop/DesktopHostTest.kt`

**Interfaces:**
- Produces: `DesktopBackend`, `DesktopIntegration`, `DesktopHostOptions`, les quatre overloads attach/run exacts.
- Consumes: application, policy et diagnostics de `foundation`.

- [ ] **Step 1: Tester les quatre formes publiques**

Le test Kotlin compile et invoque les overloads factory/lambda. `attachKadreDesktop` retourne exactement `Failure(Unsupported(HostAttach))`; `runKadreApplication` lève `KadreException` portant cette même failure. Vérifier que les lambdas ne sont jamais invoquées sans session admise.

- [ ] **Step 2: Prouver le rouge**

Run: `rtk ./gradlew :kadre-new:platform:desktop:jvmTest`

Expected: FAIL à la compilation sur `DesktopHostOptions` et les entry points.

- [ ] **Step 3: Implémenter la surface Desktop**

Recopier `BACKEND-CAPABILITIES.md` section 6.4. Ne valider aucune sélection inexistante et ne créer aucun scope/job : tant que le runtime/provider n'existe pas, toutes les options cohérentes sont structurellement `Unsupported(HostAttach)`; le runner traduit cette failure par `getOrThrow()`.

- [ ] **Step 4: Passer les tests et ABI Desktop**

Run:

```bash
rtk ./gradlew :kadre-new:platform:desktop:jvmTest
rtk ./gradlew :kadre-new:platform:desktop:updateKotlinAbi
rtk ./gradlew :kadre-new:platform:desktop:checkKotlinAbi
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add new-kadre/platform/desktop new-kadre/build.gradle.kts
rtk git commit -m "feat: expose the Desktop host contract"
```

---

### Task 7: Publications, consumers autonomes et contrats actifs

**Files:**
- Create: `new-kadre/consumers/kotlin/settings.gradle.kts`
- Create: `new-kadre/consumers/kotlin/build.gradle.kts`
- Create: `new-kadre/consumers/kotlin/src/main/kotlin/Consumer.kt`
- Create: `new-kadre/consumers/java/settings.gradle.kts`
- Create: `new-kadre/consumers/java/build.gradle.kts`
- Create: `new-kadre/consumers/java/src/main/java/org/graphiks/kadre/consumer/Consumer.java`
- Modify: `new-kadre/build.gradle.kts`
- Modify: `new-kadre/contracts/registry/contracts.tsv`

**Interfaces:**
- Produces: tasks `validateKotlinConsumer` et `validateJavaConsumer`; premières lignes `API-*` actives.
- Consumes: artifacts publiés, jamais `project(...)`, depuis `build/new-kadre-contract-repository`.

- [ ] **Step 1: Écrire les consumers avant leur gate**

Le consumer Kotlin construit et copie les trois policies, utilise les combinators `KadreResult`, compile une `KadreApplication` coroutine, branche exhaustivement sur `SessionOutcome` et appelle les overloads Desktop avec trailing lambda. Le consumer Java lit les profils intégrés et le value model diagnostics sans `Continuation`, backend interne ou ancien type Kadre.

- [ ] **Step 2: Publier vers le repository temporaire**

Run:

```bash
rtk ./gradlew :kadre-new:publishAllPublicationsToContractTestRepository :kadre-new:foundation:publishAllPublicationsToContractTestRepository :kadre-new:platform:desktop:publishAllPublicationsToContractTestRepository
```

Expected: PASS ; les trois builds consumers n'ont encore aucune task root qui les masque.

- [ ] **Step 3: Prouver les consumers autonomes**

Run:

```bash
rtk ./gradlew -p new-kadre/consumers/kotlin compileKotlin -PkadreRepository=../../../build/new-kadre-contract-repository -PkadreVersion=1.0.0
rtk ./gradlew -p new-kadre/consumers/java compileJava -PkadreRepository=../../../build/new-kadre-contract-repository -PkadreVersion=1.0.0
```

Expected: PASS. Le numéro est uniquement la coordonnée de test héritée du build existant, pas une version New Kadre déclarée par les specs.

- [ ] **Step 4: Câbler les deux gates depuis l'umbrella**

Créer deux `GradleBuild` dépendant des publications, avec repository absolu et `project.version`; faire dépendre `:kadre-new:check` des deux.

- [ ] **Step 5: Activer les contrats structurels réels**

Ajouter au TSV, avec tabulations réelles :

```text
API-001	active	PUBLIC-API-CATALOG.md#2	foundation ABI	public surface drift	O1	foundation-abi	jvm	-	public-api-leak	-
API-002	active	PUBLIC-API-CATALOG.md#3	common values	invalid scalar publication	O2	value-invariants	jvm	-	non-finite-value	-
API-003	active	POLICY-PROFILES.md#8	policy profiles	invalid or drifting policy	O2	policy-profiles,policy-validation	jvm	-	invalid-policy-accepted	-
API-004	active	DESIGN.md#12	KadreResult	failure identity loss	O2	result-combinators	jvm	-	transform-exception-captured	-
API-005	active	INTEROP-EXPORTS.md#1	Kotlin consumer	unusable published Kotlin API	O1	kotlin-consumer	jvm	-	internal-type-leak	-
API-006	active	INTEROP-EXPORTS.md#4	Java consumer	unusable JVM value model	O1	java-consumer	jvm	-	continuation-leak	-
```

- [ ] **Step 6: Passer le gate root**

Run: `rtk ./gradlew :kadre-new:check :kadre-new:foundation:checkKotlinAbi :kadre-new:platform:desktop:checkKotlinAbi`

Expected: PASS, consumers compris.

- [ ] **Step 7: Commit**

```bash
rtk git add new-kadre/build.gradle.kts new-kadre/consumers new-kadre/contracts/registry/contracts.tsv
rtk git commit -m "test: gate the published foundation API"
```

---

### Task 8: Audit de fermeture et PR 2 stackée

**Files:**
- Verify only.

- [ ] **Step 1: Vérifier les APIs et la target**

Run:

```bash
rtk ./gradlew :kadre-new:check :kadre-new:foundation:checkKotlinAbi :kadre-new:platform:desktop:checkKotlinAbi
rtk ./gradlew :kadre-new:foundation:buildKotlinToolingMetadata :kadre-new:platform:desktop:buildKotlinToolingMetadata
```

Expected: PASS ; chaque metadata liste seulement `common` et `jvmTarget = 25`.

- [ ] **Step 2: Interdire les dépendances hors scope**

Run:

```bash
rtk rg -n "kffi|kadre-core|kadre-appkit|androidTarget|iosArm64|iosX64|wasmJs|js\\s*\\{|org.graphiks.kadre.internal" new-kadre --glob '*.kts' --glob '*.kt'
```

Expected: aucune correspondance dans les builds/sources publiés.

- [ ] **Step 3: Vérifier le diff et l'historique**

Run:

```bash
rtk git diff --check
rtk git status --short
rtk git log --oneline --decorate codex/first-implementation..HEAD
```

Expected: arbre propre et uniquement les commits de ce plan.

- [ ] **Step 4: Push et PR stackée**

```bash
rtk git push -u origin codex/foundation-api
rtk gh pr create --base codex/first-implementation --head codex/foundation-api --title "feat: define the New Kadre JVM foundation API"
```

Le body liste les packages ajoutés, les invariants prouvés, les consumers autonomes et la commande de gate exacte. La PR reste bloquée par la PR 1 et devient la base de la PR 3 runtime.

## Critères de sortie PR 2

- Toute déclaration commune du catalogue compile dans `foundation` et apparaît dans son ABI, sans déclaration publique improvisée.
- Les quatre entry points Desktop et leurs options compilent dans `platform:desktop`; leur indisponibilité pré-runtime est explicite.
- Les profils `Default`, `Realtime` et `Recording` sont égaux champ par champ aux tables normatives.
- Les invariants numériques et structurels intrinsèques échouent au constructeur, sans clamp ou valeur non finie.
- Les consumers Kotlin et Java résolvent les publications depuis un repository Maven temporaire.
- Le registre contient uniquement des contrats `API-*` dont les preuves existent dans cette PR.
- Aucun code runtime, fake, backend, AppKit, KFFI ou SDK host n'est ajouté.
- La gate ne dépend d'aucun test historique.
