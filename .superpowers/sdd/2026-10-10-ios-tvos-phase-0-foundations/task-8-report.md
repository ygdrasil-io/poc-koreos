# Task 8 — Sweep du critère de sortie Phase 0 + note d'extraction phase 1

**Date :** 2026-10-10 · **Worktree :** `/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f` · **Branche :** `zcode/sess-56645e3f` · **HEAD :** `fea394e1` (ci(uikit): run the UIKit gate on the xcode-27 preview image)

**Statut global : PASS.** Les cinq critères de sortie de la roadmap Phase 0 sont vérifiés sur un sweep à froid complet (post `./gradlew clean`). Aucun défaut bloquant trouvé, **aucun commit requis** (l'arbre est resté propre, `git status` vide). Trois constats non bloquants sont consignés en fin de rapport (dont un écart lettre/intention sur le critère 5 et un écart de câblage CI sur le critère 2) — aucune retouche de code n'a été faite silencieusement.

---

## Step 1 — Sweep à froid (toutes commandes, toutes vertes)

État de départ : `git status --short` vide au commit `fea394e1`. Premier essai de compilation ayant révélé `12 up-to-date` (artefacts des tasks précédentes), un `./gradlew clean` explicite a été passé (**BUILD SUCCESSFUL in 2s**, 53 actionable tasks) pour garantir un sweep réellement à froid. Toutes les commandes ci-dessous sont ensuite exécutées depuis cet état nettoyé.

### 1a. Compilation — kadre:foundation (4/4 targets)

```
./gradlew :kadre:foundation:compileKotlinIosArm64 :kadre:foundation:compileKotlinIosSimulatorArm64
→ BUILD SUCCESSFUL in 6s   (13 actionable tasks: 4 executed, 9 up-to-date)

./gradlew :kadre:foundation:compileKotlinTvosArm64 :kadre:foundation:compileKotlinTvosSimulatorArm64
→ BUILD SUCCESSFUL in 3s   (13 actionable tasks: 3 executed, 10 up-to-date)
```

### 1b. Compilation — kadre:runtime (4/4 targets)

```
./gradlew :kadre:runtime:compileKotlinIosArm64 :kadre:runtime:compileKotlinIosSimulatorArm64
→ BUILD SUCCESSFUL in 6s   (19 actionable tasks: 7 executed, 12 up-to-date)

./gradlew :kadre:runtime:compileKotlinTvosArm64 :kadre:runtime:compileKotlinTvosSimulatorArm64
→ BUILD SUCCESSFUL in 5s   (19 actionable tasks: 6 executed, 13 up-to-date)
```

### 1c. Compilation — kadre:platform:uikit (4/4 targets)

```
./gradlew :kadre:platform:uikit:compileKotlinIosArm64 :kadre:platform:uikit:compileKotlinIosSimulatorArm64
→ BUILD SUCCESSFUL in 4s   (25 actionable tasks: 8 executed, 17 up-to-date)

./gradlew :kadre:platform:uikit:compileKotlinTvosArm64 :kadre:platform:uikit:compileKotlinTvosSimulatorArm64
→ BUILD SUCCESSFUL in 3s   (25 actionable tasks: 7 executed, 18 up-to-date)
```

### 1d. Compilation — umbrella kadre (4/4 targets)

```
./gradlew :kadre:compileKotlinIosArm64 :kadre:compileKotlinIosSimulatorArm64
→ BUILD SUCCESSFUL in 1s   (29 actionable tasks: 7 executed, 22 up-to-date)

./gradlew :kadre:compileKotlinTvosArm64 :kadre:compileKotlinTvosSimulatorArm64
→ BUILD SUCCESSFUL in 1s   (29 actionable tasks: 6 executed, 23 up-to-date)
```

**16/16 cibles `compileKotlin*` vertes à froid.**

### 1e. Gate UIKit complète

```
./scripts/test-uikit-simulator.sh all
→ OK: destination platform=iOS Simulator,name=iPhone 17,OS=27.0
→ > Task :kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64
→ > Task :kadre:contracts:validator:generateUikitBCK010IosSimulatorArm64ContractEvidence
→ > Task :kadre:contracts:validator:validateIosSimulatorArm64UikitContractEvidence
→ BUILD SUCCESSFUL in 50s
→ OK: destination platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0
→ > Task :kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64
→ > Task :kadre:contracts:validator:generateUikitBCK010TvosSimulatorArm64ContractEvidence
→ > Task :kadre:contracts:validator:validateTvosSimulatorArm64UikitContractEvidence
→ BUILD SUCCESSFUL in 28s
→ > Task :kadre:platform:uikit:linkDebugFrameworkIosArm64
→ > Task :kadre:platform:uikit:linkDebugFrameworkTvosArm64
→ BUILD SUCCESSFUL in 8s
→ OK: kadre/contracts/driver/uikit/build/contract-evidence/iosSimulatorArm64/contract-evidence/BCK-010.json
→ OK: kadre/contracts/driver/uikit/build/contract-evidence/tvosSimulatorArm64/contract-evidence/BCK-010.json
→ uikit gate (all): SUCCESS
```

Les **quatre** `linkDebugFramework*` sont passés dans ce run : les deux slices simulateur (exigées par la génération de preuve, app hôte Xcode liée à la framework) et les deux slices appareil (étape 3 du `all`, dont le commentaire du script rappelle : « Les slices appareil compilent et se lient (pas une preuve d'exécution — roadmap §7) »).

### 1f. Tests runtime JVM

```
./gradlew :kadre:runtime:jvmTest
→ BUILD SUCCESSFUL in 11s   (17 actionable tasks: 8 executed, 9 up-to-date)
```

### 1g. (complément nécessaire au critère 2) Tests natifs runtime sur les deux simulateurs

Le script de gate n'exécute pas les tests Kotlin/Native de `kadre/runtime/src/appleTest` (il exécute les apps driver Xcode) et le `clean` initial avait effacé les rapports : les deux tâches ont été relancées explicitement pour régénérer les preuves du critère 2.

```
./gradlew :kadre:runtime:iosSimulatorArm64Test
→ BUILD SUCCESSFUL in 19s   (28 actionable tasks: 10 executed, 18 up-to-date)

./gradlew :kadre:runtime:tvosSimulatorArm64Test
→ BUILD SUCCESSFUL in 20s   (28 actionable tasks: 8 executed, 20 up-to-date)
```

(Voir constat N2 : ces deux tâches ne sont câblées ni dans le script de gate ni dans CI.)

### 1h. (critère 4) Reproduction à froid de l'échec « simulateur manquant »

```
KADRE_UIKIT_TVOS_DESTINATION="platform=tvOS Simulator,name=Apple TV 4K (Missing),OS=99.0" \
  ./scripts/test-uikit-simulator.sh tvos
→ FAIL: simulateur 'Apple TV 4K (Missing)' (tvOS) introuvable — le job correspondant doit échouer.
→ exit code: 1
```

Échec fidèle avant tout travail Gradle (vérification `xcrun simctl list devices available` dans `require_destination`).

---

## Step 2 — Checklist d'exit (roadmap Phase 0), verdict par item

Critère de sortie exact (IOS-IMPLEMENTATION-ROADMAP.md, § Phase 0) : « compilation et linking des quatre targets ; tests des primitives natives exécutés sur les deux simulateurs ; une preuve O3 issue de chaque application UIKit iOS et tvOS démarrée, avec JSON/JUnit corrélés. Un simulateur manquant fait échouer le job correspondant. Ce jalon ne prétend pas livrer l'attach public complet. »

### (1) Compilation et linking des quatre targets — **PASS**

- Compilation : les 16 tâches `compileKotlin{IosArm64,IosSimulatorArm64,TvosArm64,TvosSimulatorArm64}` de `foundation`, `runtime`, `platform:uikit` et l'umbrella `kadre` — toutes vertes à froid (§ 1a–1d, lignes `BUILD SUCCESSFUL` citées ci-dessus).
- Linking : les 4 slices `linkDebugFramework*` passées dans le run `all` (§ 1e) :
  - `:kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64` (iOS simulateur, lien de l'app driver Xcode)
  - `:kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64` (tvOS simulateur, idem)
  - `:kadre:platform:uikit:linkDebugFrameworkIosArm64` + `linkDebugFrameworkTvosArm64` (slices appareil)

### (2) Tests des primitives natives sur les deux simulateurs — **PASS**

Sources : `kadre/runtime/src/appleTest/kotlin/org/graphiks/kadre/internal/runtime/NativePrimitivesConcurrencyTest.kt` (compilé dans les deux variantes simulateur via `applyDefaultHierarchyTemplate()`).

Preuves régénérées ce sweep (§ 1g) :

- `kadre/runtime/build/test-results/iosSimulatorArm64Test/TEST-*NativePrimitivesConcurrencyTest.xml` : `tests="6" skipped="0" failures="0" errors="0"`, cas : `lockProvidesRealMutualExclusionAcrossNativeThreads`, `lockIsReentrantWithinOneThread`, `isHeldByCurrentThreadIsPerThread`, `identityKeyedMapKeysByReferenceIdentity`, `interactionCallFrameIsPerThread`, `linkageFailureClassificationStaysFalseOnNative` (tous suffixés `[iosSimulatorArm64]`).
- `kadre/runtime/build/test-results/tvosSimulatorArm64Test/TEST-*NativePrimitivesConcurrencyTest.xml` : mêmes 6 cas, suffixés `[tvosSimulatorArm64]`, `tests="6" skipped="0" failures="0" errors="0"`.

Les autres suites natives passent également sur les deux simulateurs (12 suites/64 tests chacune, 0 échec, 0 skip) : RuntimeInteractionHandlerCommonTest 16, RuntimeCaptureSessionTest 14, RuntimeHostControllerCommonTest 9, RuntimeCaptureManagerTest 9, SurfaceAdmissionTest 6, RuntimeSurfaceInputCommonTest 5, RuntimeSurfaceInputConfigurationTest 5, RuntimePortabilityPrimitivesTest 4, RuntimePrimarySurfaceConfigurationTest 3, CapturePortFrameTest 2, RuntimeSurfaceInputTextInputObservationTest 1.

### (3) Une preuve O3 par app UIKit (iOS + tvOS), JSON/JUnit corrélés — **PASS**

Artefacts **régénérés par le run `all` de ce sweep** (horodatés 18:47/18:48, commit courant `fea394e1e370fdf4b1b2fbd70d98b1d90cfdae17`), distincts par cible :

- `kadre/contracts/driver/uikit/build/contract-evidence/iosSimulatorArm64/contract-evidence/BCK-010.json` — `target: "iosSimulatorArm64"`, scénarios O3 `uikit-driver-observes-real-window` → Passed et `uikit-driver-reports-detached-view` → Passed, sentinelle `uikit-driver-window-membership-sentinel` → Killed, `tests: {2, 0, 0, 0}`, `durationMillis: 6`.
- `kadre/contracts/driver/uikit/build/contract-evidence/tvosSimulatorArm64/contract-evidence/BCK-010.json` — `target: "tvosSimulatorArm64"`, mêmes scénarios/sentinelle, `tests: {2, 0, 0, 0}`, `durationMillis: 11`.

Corrélation avec les JUnit exportés du driver Xcode (`xcodebuild` → `tools/xctest_to_junit.py`) :

- `.../iosSimulatorArm64/test-results/iosSimulatorArm64/TEST-KadreUikitDriverTests.xml` : `<testsuite name="KadreUikitDriverTests" tests="2" failures="0" errors="0" skipped="0" time="0.006087">` — 6 ms = `durationMillis: 6`. Testcases : `testKotlinProbeObservesRealWindow()` (0.005276 s), `testProbeReportsViewOutsideWindowAsDetached()` (0.000811 s).
- `.../tvosSimulatorArm64/test-results/tvosSimulatorArm64/TEST-KadreUikitDriverTests.xml` : `tests="2" failures="0" errors="0" skipped="0" time="0.010606"` — ≈11 ms = `durationMillis: 11`. Mêmes deux testcases.

La validation indépendante (`validateIos…`/`validateTvos…UikitContractEvidence`, génération séparée du corrélateur JVM) a validé chaque artefact dans le run `all` (§ 1e). Les apps hôtes ont bien été **démarrées** sur simulateur (phase-0 : vrai lifecycle UIKit, pas un test unitaire K/N).

### (4) Simulateur manquant ⇒ échec du job — **PASS**

- Preuve Task 7 (revue) : vérification du `name=` ET de l'`OS=` dans `require_destination` avant tout travail Gradle ; les deux branches (`simulateur introuvable`, `runtime introuvable`) émettent `FAIL … le job correspondant doit échouer` et `exit 1`.
- Reproduction à frais ce sweep (§ 1h) : override `KADRE_UIKIT_TVOS_DESTINATION` avec un nom inexistant → `FAIL: simulateur 'Apple TV 4K (Missing)' (tvOS) introuvable — le job correspondant doit échouer.` + `exit code: 1`, sans aucune tâche Gradle. Pas de downgrade silencieux de destination possible.

### (5) Aucune prétention d'attach public — **PASS (avec un constat de lettre vs intention, voir N1)**

- `grep -rn "KadreIos" kadre/ --include="*.kt"` → **1 seul hit**, en commentaire KDoc : `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreUikitProbe.kt:29` — « Scaffolding du driver — remplacé par `KadreIos.attach` en phase 1 ; ne fait partie d'aucune façade promise. » **Aucun symbole** `KadreIos` n'existe dans le code livré ; la surface publique de `platform:uikit` se limite à `KadreUikitWindowMembership`, `KadreUikitObservation` et `KadreUikitProbe.observe(window, view)` (tous sous opt-in `@KadrePlatformApi`). Le commentaire est une clause de non-garantie, pas une prétention d'attach. Le catalogue normatif (`kadre/PUBLIC-API-CATALOG.md:30`) mentionne `KadreIos` comme entrée planifiée — hors code, donc conforme.
- `platform:uikit` absent de l'agrégation umbrella : `kadre/build.gradle.kts` — `tasks.named("check")` (lignes 39–50) liste foundation/validator/appkit/desktop/web/runtime + consumers, **pas** `:kadre:platform:uikit:check` ; `publishContractArtifacts` (lignes 53–60) publie kadre/foundation/appkit/desktop/web/runtime, **pas** platform:uikit (le module déclare bien un repo `contractTest` mais aucune tâche d'agrégation ne publie ses artefacts — l'adapter reste « hors publication », conformément au checklist item 7 de la Phase 0).
- Verdict : **PASS**. L'intention du critère (aucune API d'attach déclarée/prétendue) est satisfaite sans ambiguïté ; l'écart mécanique (1 hit de commentaire) est consigné en N1 sans retouche.

---

## Step 3 — Note de design : minimum de fenêtre à extraire en phase 1

**Destination :** brancher `RuntimeSessionComponentsFactory` + `RuntimePrimarySurface` sous UIKit en extrayant le minimum de `kadre/runtime/src/jvmMain` vers commonMain.

**Ce qui n'a PAS besoin d'être extrait (déjà commonMain) :** `RuntimeSessionComponents.kt` (déclare `public fun interface RuntimeSessionComponentsFactory { public fun create(sessionId: SessionId, rootScope: CoroutineScope): RuntimeSessionComponents }`, `RuntimeSessionComponents` avec `public val windows: WindowManager` et `public val primarySurface: HostSurface?`, `RuntimePrimarySurface`, `RuntimePrimarySurfaceConfiguration`), `RuntimeHostController.kt`, `SessionRuntime.kt`. Ils consomment les interfaces `WindowManager`/`HostSurface` communes. La preuve que le motif fonctionne existe déjà côté AppKit : `AppKitWindowCommandPort : WindowCommandPort, SurfaceCommandPort` (`kadre/backend/appkit/src/jvmMain/.../AppKitWindowRuntimeDriver.kt:274-297`) compose `RuntimeWindowManager(...)` (même fichier, ligne 126) et l'umbrella l'agrège via `appleMain.dependencies { api(project(":kadre:platform:uikit")) }`.

**Le minimum à extraire — cinq fichiers, signatures verbatim (relues ce jour) :**

### 3.1 `RuntimeWindowManager` — `RuntimeWindowManager.kt:94-109` (public, le reducer portable)

```kotlin
public class RuntimeWindowManager public constructor(
    private val resources: ResourceBudgetPolicy,
    private val commandPort: WindowCommandPort,
    private val surfaceCommandPort: SurfaceCommandPort = UnsupportedSurfaceCommandPort,
    private val platform: KadrePlatform,
    private val failureReporter: RuntimeFailureReporter,
    private val publicWindowCapabilities: Boolean = false,
    private val enabledWindowUpdateCapabilities: Set<WindowProperty> = emptySet(),
    private val attentionPort: WindowAttentionPort? = null,
    private val acceptedAttention: Set<WindowAttention> = emptySet(),
    private val fullscreenAvailabilityFailure: KadreFailure.PlatformFailure? = null,
    private val publicSurfaceCapabilities: Boolean = false,
    private val enabledSurfaceCapabilities: SurfaceCapabilities = unsupportedSurfaceCapabilities(),
    private val textInputPortFactory: TextInputPortFactory = TextInputPortFactory { UnsupportedTextInputPort },
    private val onLastWindowClosed: (() -> Unit)? = null,
) : WindowManager, AutoCloseable, RuntimeFullscreenObservationSink, RuntimeSessionWindowManager
```

Membres consommés par les hosts (extrait verbatim) : `override val state: StateFlow<WindowManagerState>` ; `override suspend fun requestWindow(spec: WindowSpec): KadreResult<WindowRequest>` ; `override fun close()` ; `public fun installExclusiveFullscreenPort(port: ExclusiveFullscreenPort)` ; `public fun acceptSurfaceStimulus(stimulus: SurfaceStimulus): Boolean` ; `public fun dispatchSynchronousInteraction(surfaceId: SurfaceId, event: RuntimeSynchronousInteraction, supported: Set<InteractionKind>, invokeNative: (InteractionAction) -> KadreResult<Unit>): Boolean` ; `public fun dispatchSynchronousDrop(surfaceId: SurfaceId, source: DropTransferSource, position: LogicalPoint, invokeNative: (DropOfferId) -> KadreResult<Unit>): DropOfferId?` ; `public fun acceptWindowGeometryObservation(windowId: WindowId, state: WindowState): Boolean`. Le fichier fait 3 648 lignes : c'est LE reducer de fenêtre (admission pending/committed, close/update, attention, fullscreen, raw input) qu'il ne faut pas dupliquer.

Dépendances JVM à rendre portables lors de l'extraction : `java.util.concurrent.atomic.AtomicLong` (l.85/135), `System.nanoTime()` (l.136), `ThreadLocal.withInitial` (l.119-120), et le moniteur `synchronized(lock)` (remplaçable par `RuntimeLock`, déjà présent en commonMain dans RuntimeSessionComponents.kt:73).

### 3.2 `RuntimeWindowEventFlow` — `RuntimeWindowEventFlow.kt:20-26` (internal)

```kotlin
internal class RuntimeWindowEventFlow(
    private val policy: WindowDeliveryPolicy,
    private val eventCollectorGate: RuntimeEventCollectorGate,
    private val failureReporter: RuntimeFailureReporter,
    private val sessionFailureHandler: (KadreFailure) -> Unit,
    private val closeWindow: () -> Unit,
)
```

Surface consommée : `val events: Flow<WindowEvent>` (l.37), `fun publish(event: WindowEvent)` (l.51), `fun close()` (l.120). Apporte les lanes Discrete/Geometry + coalescing (`BoundedWindowEventScheduler`, privé au fichier — à extraire avec lui).

### 3.3 `WindowCommandPort` + ses types de commande — `WindowCommandPort.kt:114-140` (public)

```kotlin
public interface WindowCommandPort {
    public fun requestOpen(command: WindowOpenCommand)
    public fun requestUpdate(command: WindowUpdateCommand)
    public fun requestUpdateCancellation(
        command: WindowUpdateCancellationCommand,
    ): WindowUpdateCancellationOutcome = WindowUpdateCancellationOutcome.TooLate
    public fun requestPendingCancellation(
        command: PendingWindowCancellationCommand,
    ): PendingWindowCancellationOutcome
    public fun requestOpenedClose(command: OpenedWindowCloseCommand): OpenedWindowCloseOutcome
    public fun closeRequestRejected(command: OpenedWindowCloseCommand): CloseRequestRejectionOutcome =
        CloseRequestRejectionOutcome.Rejected
}
```

Types du même fichier à extraire avec l'interface : `WindowOpenCommand` (l.405, avec `commit(owner, effectiveSpec, initialSurfaceSnapshot, onSurfaceReady)` / `fail` / `nativeClosed` / `closeRequested`), `WindowUpdateCommand` (l.158, avec `applied`/`partiallyApplied`/`rejected`/`failed`/`committedFailure`/`fullscreen*`), `WindowUpdateCommandStimulus` (l.275), `WindowPeerOwner` (l.375, `public fun interface WindowPeerOwner : AutoCloseable`), `PendingWindowCancellationCommand/Intent/Outcome`, `OpenedWindowCloseCommand/Outcome`, `CloseRequestRejectionOutcome`, `WindowUpdateCancellationCommand/Outcome`, `WindowAttentionPort` (l.143), `RuntimeFullscreenObservation(Sink)` (l.348-360), `ExclusiveFullscreen*` (l.23-105). Dépendance JVM : `java.util.IdentityHashMap` dans `WindowOpenCommand` (l.413) et `AtomicBoolean` dans `ManagedWindowPeerOwner` (l.524-541, internal mais extrait avec).

### 3.4 `SurfaceCommandPort` + ses types — `SurfaceCommandPort.kt:40-46` (public)

```kotlin
public interface SurfaceCommandPort {
    /** Admits one coalesced native invalidation request. */
    public fun requestRedraw(command: SurfaceRedrawCommand): KadreResult<Unit>

    /** Applies the requested fields and returns one explicit outcome for every admitted field. */
    public suspend fun apply(command: SurfaceUpdateCommand): KadreResult<SurfaceUpdateCommandOutcome>
}
```

Types du même fichier : `SurfaceRedrawCommand` (l.49), `SurfaceInitialSnapshot` (l.55, le snapshot immuable d'initialisation — c'est la passe rôle host→Kotlin), `SurfaceUpdateCommand` (l.64), `SurfaceUpdateCommandOutcome` (l.73), `SurfaceFieldOutcome<T>` (l.81), et l'`internal object UnsupportedSurfaceCommandPort` (l.87, valeur par défaut du constructeur 3.1).

### 3.5 `RuntimeWindowSurface` — `MinimalWindowSurface.kt:91-115` (internal, implémentation `HostSurface`)

```kotlin
internal class RuntimeWindowSurface(
    override val id: SurfaceId,
    initialSnapshot: SurfaceInitialSnapshot,
    private val commandPort: SurfaceCommandPort,
    private val textInputPort: TextInputPort = UnsupportedTextInputPort,
    private val rawInputCoordinator: RawInputCoordinator? = null,
    rawInputCapability: Capability<Unit> = unsupported(KadreOperation.RawInputAccess),
    private val commandsEnabled: Boolean,
    enabledCapabilities: SurfaceCapabilities,
    private val eventStampSource: () -> EventStamp,
    private val platform: KadrePlatform = KadrePlatform.Fake,
    private val failureReporter: RuntimeFailureReporter = RuntimeFailureReporter { },
    private val deliveryPolicy: WindowDeliveryPolicy = KadrePolicies.Default.window,
    private val inputDeliveryPolicy: InputDeliveryPolicy = KadrePolicies.Default.input,
    private val resources: ResourceBudgetPolicy = KadrePolicies.Default.resources,
    private val dropTransferBudget: RuntimeDropTransferBudget = RuntimeDropTransferBudget(
        resources.maxConcurrentDropTransfers,
    ),
    private val dropTransferScope: kotlinx.coroutines.CoroutineScope? = null,
    private val maxCollectorsPerFlow: Int = KadrePolicies.Default.resources.maxEventCollectorsPerFlow,
    private val collectorAllocator: RuntimeEventCollectorAllocator = RuntimeEventCollectorAllocator(
        KadrePolicies.Default.resources.maxEventCollectorsPerSession,
    ),
    private val sessionFailureHandler: (KadreFailure) -> Unit = {},
) : HostSurface
```

Overrides `HostSurface` : `state: StateFlow<SurfaceState>`, `capabilities: StateFlow<SurfaceCapabilities>`, `events: Flow<SurfaceEvent>`, `input: SurfaceInput`, `installInteractionHandler(handler): KadreResult<InteractionRegistration>`, `suspend fun armInteraction(action, options)`, `fun requestRedraw(): KadreResult<Unit>`, `suspend fun apply(update: SurfaceUpdate): KadreResult<SurfaceUpdateOutcome>`. Points d'entrée backend (internal) que le driver UIKit utilisera, comme AppKit le fait déjà : `accept(stimulus: SurfaceStimulus): Boolean`, `detach(): Boolean`, `dispatchSynchronousInteraction(...)` ×2 + `dispatchSynchronousDrop(...)`, `updateRawInputCapability(capability)`. Dépendance JVM : `java.util.concurrent.locks.ReentrantLock` + condition (l.13/169-170, pour la sérialisation des interactions synchrones — à remplacer par `RuntimeLock`/`kotlin.concurrent.Lock`).

**Contraintes (citations) :**
- Roadmap Phase 1 item 1 : « Extraire le minimum portable de fenêtre et brancher `RuntimeSessionComponentsFactory`, `RuntimePrimarySurface` et le contrôleur commun. **Préserver les tests AppKit ; ne pas dupliquer le reducer de fenêtre dans UIKit.** » → l'extraction déplace les cinq fichiers tels quels vers commonMain ; le backend UIKit n'écrit QUE les ports (patron `AppKitWindowCommandPort`), jamais une seconde machine à états.
- Tests AppKit à préserver : `kadre/backend/appkit/src/jvmTest/.../AppKitBackendProviderTest.kt` et `RuntimeSessionComponentsSpiTest.kt` exercent `RuntimeWindowManager` au travers de `AppKitWindowCommandPort` ; l'extraction vers commonMain laisse ces tests JVM compiler et passer (seules les dépendances JVM listées doivent passer par des expect/actual ou `RuntimeLock`).
- Roadmap Phase 0 checklist item 7 : « Fermer les décisions du §2 et décrire le minimum de fenêtre à extraire pour la phase 1. Garder l'adapter hors publication officielle tant que ses garanties structurelles ne sont pas satisfaites. » → cette note est le livrable ; l'exclusion de `platform:uikit` des agrégations check/publish (critère 5) reste la garde.

**Ordre d'extraction suggéré (dépendances) :** 3.4 (`SurfaceCommandPort`, sans dépendance runtime interne) → 3.3 (`WindowCommandPort` + commandes/stimuli) → 3.2 (`RuntimeWindowEventFlow`) → 3.5 (`RuntimeWindowSurface`) → 3.1 (`RuntimeWindowManager`, couronne le tout). Aucune signature publique ne change ; les deux interfaces restent `public` sous `explicitApi()`, les implémentations restent `internal`.

---

## Step 4 — État final de l'arbre

```
git status --short   → (vide)
```

**Aucun commit** : aucune retouche n'était nécessaire ; tout ce que le sweep a produit vit dans `build/` (ignoré). Le HEAD reste `fea394e1e370fdf4b1b2fbd70d98b1d90cfdae17`.

---

## Constats et auto-revue (non bloquants, rien patché)

- **N1 — Critère 5, lettre vs intention :** le grep mécanique `KadreIos` retourne 1 hit en commentaire KDoc (`KadreUikitProbe.kt:29`), qui est une clause de non-garantie explicite. Consigné plutôt que corrigé : éditer le commentaire ne ferait que masquer l'information utile (« la façade arrive en phase 1 ») ; si un jour un tripwire automatique greppe ce symbole, il faudra soit l'ancrer sur les déclarations (`public class|object|interface .*KadreIos`), soit décider que le commentaire doit disparaître à l'activation de la façade.
- **N2 — Câblage CI du critère 2 :** les tests natifs runtime sur simulateurs (`:kadre:runtime:iosSimulatorArm64Test` / `tvosSimulatorArm64Test`) ne sont exécutés ni par `scripts/test-uikit-simulator.sh` (qui lance les apps driver Xcode), ni par CI (`.github/workflows/kadre-uikit-contracts.yml` ne lance que le script), ni par `check` (l'umbrella agrège `:kadre:runtime:check`, JVM uniquement). Le critère de sortie demande que les tests soient exécutés (ils le sont, preuves § 2) mais rien ne les automatise : proposition phase 1 — ajouter les deux tâches au run `all` du script (ou à un job CI dédié) pour que les rapports XML soient régénérés à chaque gate.
- **N3 — `platform:uikit` sans source set de test unitaire :** le module ne contient que `uikitMain` (pas de `uikitTest`). Conforme à l'intention Phase 0 (« distinguer les unit tests Kotlin/Native d'un test dans une application UIKit exécutant son vrai lifecycle ») : la sonde est éprouvée par l'app driver Xcode sur chaque simulateur, pas par un test unitaire. À garder à l'esprit si des tests unitaires K/N deviennent utiles en phase 1.
- **N4 — Métadonnées de publication de l'umbrella :** `appleMain.dependencies { api(project(":kadre:platform:uikit")) }` (kadre/build.gradle.kts:33-35) fait référence `kadre-platform-uikit` dans le module metadata apple de l'umbrella publié, alors que l'artefact lui-même n'est pas publié par `publishContractArtifacts` (voulu — N/garde « hors publication »). Inoffensif en phase 0 (aucun consumer apple validé), à résoudre en phase 1/3 quand un consumer iOS résoudra réellement ces variantes.
- **Auto-revue du sweep :** le `clean` initial a effacé toutes les preuves antérieures ; tout ce qui est cité ici (JSON BCK-010, XML JUnit driver, XML natifs runtime) a été régénéré pendant CE sweep au commit `fea394e1` — les horodatages et le champ `commit` des JSON le confirment. Les résultats sont identiques à ceux des tasks 4–7 : aucun drift introduit par les commits T5→T7.

---

## Final review fix wave

Commit unique : `30217a9a` — `fix(uikit): harden the junit converter and correct the native flag in the architecture doc` (2 fichiers, +8/−2 ; `git status` propre ensuite).

### F1 — Convertisseur JUnit : fin du canal faux-vert (`kadre/contracts/driver/uikit/tools/xctest_to_junit.py`)

Avant : `write_junit` comptait comme Passed toute valeur de `result` différente de "failed"/"skipped" (un « Expected Failure », un statut inconnu ou une clé vide devenaient silencieusement verts). Après : dans `walk()`, seul `result == "passed"` (après `.lower()`, calculé exactement comme avant) produit un testcase passant ; toute autre valeur lève une `ValueError` nommant la valeur brute et le nom du cas :

```python
raw_result = node.get("result", "")
result = raw_result.lower()
if result not in ("passed", "failed", "skipped"):
    raise ValueError(
        f"résultat XCTest inattendu « {raw_result} » pour le cas « {name} » — "
        "refus de le compter comme Passed (politique never-false-green)"
    )
```

Stdlib only, adaptateur de schéma `parse_nodes` inchangé (la garde vit après le `.lower()`, en aval du point de drift schéma). Contrôle de non-régression sur le chemin heureux (statuses Passed/Failed/Skipped synthétiques) : parse + export identiques (testsuite `tests="2" failures="0" skipped="0"`).

Preuve du chemin fail-loud — JSON synthétique /tmp/fake_xcresult.json portant un Test Case `result: "Expected Failure"`, injecté via un harnais `python3 -c` important le module et appelant `parse_nodes` (même code path que `main()`) :

```
RAISED ValueError: résultat XCTest inattendu « Expected Failure » pour le cas « testInternalDeclarationsVisibleFromRuntime() » — refus de le compter comme Passed (politique never-false-green)
```

### F2 (finding F3) — Doc normative (`kadre/PROJECT-ARCHITECTURE.md`, §5)

Le paragraphe du verdict de visibilité native Task 2 citait `-Xfriend-modules` ; le câblage réel des tasks `KotlinNativeCompile` (kadre/runtime/build.gradle.kts:80) est `-friend-modules=` (le `-Xfriend-modules=` du build script ne concerne que js/wasmJs). Corrigé en une seule occurrence, rien d'autre touché dans le fichier.

### Vérification gate complet (chemin de production du fix)

État purgé (`kadre/contracts/driver/uikit/build/{xcresult,contract-evidence}` supprimés) puis :

1. `./gradlew :kadre:contracts:driver:uikit:simulatorTests` → **BUILD SUCCESSFUL in 1m 07s** ; les deux familles `** TEST SUCCEEDED **` (2/2 chacune, ex. tvOS : « Executed 2 tests, with 0 failures (0 unexpected) ») ; les deux exports JUnit (qui invoquent `python3 tools/xctest_to_junit.py`) ont réexporté du neuf : `TEST-KadreUikitDriverTests.xml` ios et tvos avec `tests="2" failures="0" errors="0" skipped="0"`.
2. `./gradlew :kadre:contracts:validator:generateUikitContractEvidence :kadre:contracts:validator:validateIosSimulatorArm64UikitContractEvidence :kadre:contracts:validator:validateTvosSimulatorArm64UikitContractEvidence :kadre:contracts:validator:validateContractRegistry` → **BUILD SUCCESSFUL in 59s** : `generateUikitBCK010TvosSimulatorArm64ContractEvidence`, les deux `validate*UikitContractEvidence` et `validateContractRegistry` tous exécutés et verts, preuve régénérée pour le HEAD courant.
3. `git diff` avant commit : uniquement les deux fichiers visés (`.md` 2 lignes, `.py` +8/−2). `git status --short` après commit : vide.
