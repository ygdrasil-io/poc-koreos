# iOS/tvOS Phase 1 — Attach public, lifecycle et propriétaires par scène — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer `KadreIos.attach(windowScene, window, surfaceView, applicationFactory, policy)` — une session conforme par scène, avec sa fenêtre primaire et sa surface initiales, le relais de lifecycle par notifications de scène, des propriétaires par scène isolés — prouvé par les apps driver sur les simulateurs iOS multi-scène (iPadOS) et tvOS mono-scène, contrats BCK-013 + WIN-009 activés avec preuves.

**Architecture:** Le runtime commun reste l'autorité : les cinq fichiers de fenêtre de `kadre/runtime/src/jvmMain` migrent vers `commonMain` tels quels (portabilisation mécanique des types JVM, aucune signature changée, tests AppKit préservés), et `platform:uikit` n'écrit QUE les ports — un `UIKitWindowRuntime` qui compose `RuntimeWindowManager` (patron `AppKitWindowRuntimeDriver`), un registre par scène (patron `WebHostRegistry`), une source de lifecycle par `NSNotificationCenter` filtrée par identité de scène (le host garde son `UISceneDelegate`), et un ladder de rollback par étape (patron `WebHostSession.attach`).

**Tech Stack:** Kotlin 2.4.20 (K/N, `uikitMain` + friend-modules), Xcode 27.0 / simulateurs iOS 27.0 + tvOS 27.0, XcodeGen, XCTest (les notifications de scène réelles servent de frontière O3 ; la destruction réelle de scène iOS prouve le disconnect), pipeline evidence/validateur existant (BCK-012 est le gabarit).

**Spec:** `kadre/IOS-IMPLEMENTATION-ROADMAP.md` §3.1/§3.2 (invariants attach/lifecycle) + §5 Phase 1 (checklist et critère de sortie) ; `kadre/DESIGN.md` §15.2 et §7 (mapping normatif) ; `kadre/BACKEND-CAPABILITIES.md` §6.2. Le plan argumente depuis ces documents.

## Décisions fermées par ce plan

| Décision | Résultat fermé ici |
|---|---|
| Portabilisation des types JVM | `IdentityHashMap` → `IdentityKeyedMap` (runtime, expect/actual existants) ; `AtomicBoolean` → `kotlin.concurrent.AtomicBoolean` ; `AtomicLong` → `kotlin.concurrent.atomics.AtomicLong` (`@OptIn(ExperimentalAtomicApi::class)`) ; `System.nanoTime()` → `kotlin.time.TimeSource.Monotonic` (markNow/elapsedNanos, sémantique monotone identique) ; moniteurs `synchronized` → `RuntimeLock.withLock` (récursif, sémantique monitor) ; `ThreadLocal.withInitial` (RuntimeWindowManager) → décision guidée à la lecture de l'usage (cf. Task 2 Step 2) ; `ReentrantLock`+`Condition` (RuntimeWindowSurface) → `RuntimeLock` + restructuration du wait (cf. Task 2 Step 4). |
| Source de lifecycle | `NSNotificationCenter.defaultCenter.addObserverForName(_:object:queue:usingBlock:)` avec `object = scene` (filtre par identité, `isEqual`), queue main. Mapping : `DidActivate` → (Foreground, Active) ; `WillDeactivate` → (Foreground, Inactive) ; `DidEnterBackground` → (Background, Inactive) ; `WillEnterForeground` → (Foreground, Inactive) ; `DidDisconnect` → terminaison de la session. Aucune conformité `UISceneDelegate` côté Kotlin (le host garde son delegate). |
| Scène « connectée » | `windowScene.session != nil` (et `activationState != Unattached`). Scène déconnectée → `InvalidRequest("window")` (fields fermés `window`/`surfaceView`, jamais `windowScene`). |
| Identity d'objets host | Tout test d'identité UIKit passe par `isEqual`/`isEqual:` (phase 0 : `===` ne traverse pas le pont). Les tests d'identité côté Kotlin (surface primaire) comparent des objets Kotlin originaires — `===` Swift valable, à re-dériver si le pont surprend. |
| Fenêtre primaire | Le driver `UIKitWindowRuntime`, à la construction sur le main thread, appelle `manager.requestWindow(spec)` et le port `UIKitWindowCommandPort` committe la `UIWindow` fournie comme peer (patron `AppKitWindowCommandPort.prepare`). `primarySurface` est exactement `primary.surface` (même instance). Capacités annoncées : `publicWindowCapabilities=false`, `publicSurfaceCapabilities=false`, updates vides (style embedded AppKit) — `requestWindow`/updates restent hors périmètre (phase 4). |
| Managers avant Running | La factory `withComponents` retourne `RuntimeSessionComponents(windows = driver.manager, primarySurface = RuntimePrimarySurface(surface, ::detach))` sans ports display/device/capture → fallbacks `Unsupported*` honnêtes ; diagnostics = `RuntimeDiagnostics` du runtime. |
| parentScope | `CoroutineScope(Job() + Dispatchers.Main)` possédée par l'adapter (`KadreIos.attach` n'est pas suspendue et ne devient pas un dispatch caché : tout s'exécute de façon synchrone sur le thread appelant = main). |
| Retrait temporaire de la vue | Phase 1 : AUCUNE observation dynamique de la vue (métriques figées au snapshot d'attach ; refresh = phase 2). Le retrait d'une vue ne publie donc jamais `Detached` (terminal) — conforme roadmap l.116. Seuls les notifications de scène et la terminaison ferment la session. |
| Multi-scène iPadOS | Plist iOS `UIApplicationSupportsMultipleScenes: true` ; deuxième scène demandée par `UIApplication.shared.requestSceneSessionActivation(nil, nil, nil, nil, errorHandler)` ; disconnect réel par `requestSceneSessionDestruction`. tvOS : plist reste `false`. |
| Preuve tvOS des transitions | Les tests tvOS postent les notifications réelles (`NotificationCenter.default.post(name:object:)`) sur la scène VIVANTE — c'est la frontière SDK exacte que l'adapter observe ; le parcours hardware (home réel, interruption) reste cahier manuel. iOS prouve en plus le disconnect RÉEL (destruction de scène → UIKit poste lui-même la notification). |
| Contrats | `BCK-013` « UIKit scene host attach and lifecycle » + `WIN-009` « UIKit primary window and surface identity », tous deux `active`, requiredTargets `iosSimulatorArm64,tvosSimulatorArm64`, preuves par le driver. |
| Native tests dans le gate | Les deux tâches `:kadre:runtime:iosSimulatorArm64Test` / `tvosSimulatorArm64Test` sont ajoutées au mode `all` du script (mineur N2 de phase 0, promis phase 1). |

## Global Constraints

- AUCUNE signature publique du runtime ne change lors de l'extraction ; les implémentations restent `internal` ; `explicitApi()` partout ; les tests AppKit JVM (`AppKitBackendProviderTest`, `RuntimeSessionComponentsSpiTest`) restent verts sans modification.
- Ne JAMAIS dupliquer le reducer de fenêtre : UIKit n'écrit que `WindowCommandPort`/`SurfaceCommandPort` (roadmap l.188).
- `AlreadyInUse(Host)` avant TOUTE installation ou invocation de la factory (roadmap l.107) ; rollback par étape avec `releaseAfterAttachFailure()` + `PlatformFailure(UIKit, domaine, code)` spécifique (patron Web l.531–584).
- La fermeture révoque d'abord l'admission (revocation handler), puis observers/callbacks, puis ressources ; elle ne détruit JAMAIS la `UIWindow`/`UIView` du host (roadmap l.120).
- `SurfaceState.Detached` est terminal et ne peut être publié par un retrait temporaire de vue (roadmap l.116, DESIGN l.332).
- Identity UIKit = `isEqual` (jamais `===`) ; object Kotlin = `===` fiable.
- Friend-modules uikit : `-friend-modules=<uikit klib>:<runtime klib>` (et `+:<foundation klib>` pour les compiles de test), last-write-wins — miroir de `kadre/runtime/build.gradle.kts:95–119`.
- Preuves séparées par famille obligatoires ; activation de contrat `planned → active` dans le commit des scénarios+preuves+sentinelles ; la preuve uikit reste hors `:kadre:check` (gate = script + CI `xcode-27`).
- Gradle : depuis l'intérieur du worktree, une commande par appel Bash (< ~9 min).
- Le simulateur manquant fait échouer le job ; aucun skip/retry/continue-on-error.

## Review Focus

1. **Condition await / restructuration du `ReentrantLock`** (Task 2) — le wait d'origine sert une passation inter-threads ; le remplacer par un exécution directe sous `RuntimeLock` change la sémantique si un chemin non-main l'atteint. L'implémenteur doit prouver par lecture quel thread atteint l'attente et reporter le raisonnement ; un test AppKit qui devient vert « par hasard » est un échec.
2. **Ordre d'admission de l'attach** — toute installation (ownership, observers, driver) qui précède le `registry.reserve` peut faire fuiter une ressource sur `AlreadyInUse`. Test : scène occupée ne doit laisser AUCUN observer installé (compteur d'observateurs du fixture).
3. **Sentinelle à rayon global** — une mutation dans le préambule partagé (fixture, attach utilitaire) qui tue tous les scénarios ne prouve rien ; chaque mutation de sentinelle doit tuer SON scénario sur SA propre assertion, blast radius consigné (Task 6).
4. **Deux scènes partagent du state** — le registre par scène, l'ownership et la source de lifecycle doivent être par instance ; un singleton même « technique » croiserait les sessions. Test : deux scènes attachées, terminer l'une, l'autre reste `Running` avec ses compteurs d'événements intacts (Task 5).
5. **Callbacks natifs tardifs après teardown** — observer non retiré = crash ou mutation d'état après terminaison. Test : terminer, poster `DidActivate`, rien ne change et rien ne crashe ; sentinelle dédiée (Task 6).

---

### Task 1: Extraction runtime A — les ports et le flux d'événements vers commonMain

**Files:**
- Modify (git mv): `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/SurfaceCommandPort.kt` → `kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/SurfaceCommandPort.kt`
- Modify (git mv): `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt` → `kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/WindowCommandPort.kt`
- Modify (git mv): `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowEventFlow.kt` → `kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowEventFlow.kt`
- Test: aucun nouveau — les suites existantes (`jvmTest` runtime + AppKit) sont le filet.

**Interfaces:**
- Consumes: état master `303c9d67` (les trois fichiers sont en jvmMain ; `SurfaceCommandPort.kt` et `RuntimeWindowEventFlow.kt` n'ont AUCUN import JVM ; `WindowCommandPort.kt` importe `java.util.IdentityHashMap` (l.19) et `java.util.concurrent.atomic.AtomicBoolean` (l.20)).
- Produces: `SurfaceCommandPort` (+ `SurfaceRedrawCommand`, `SurfaceInitialSnapshot`, `SurfaceUpdateCommand`, `SurfaceUpdateCommandOutcome`, `SurfaceFieldOutcome<T>`, `UnsupportedSurfaceCommandPort`), `WindowCommandPort` (+ tous ses types : `WindowOpenCommand`, `WindowUpdateCommand`, `WindowUpdateCommandStimulus`, `WindowPeerOwner`, `PendingWindowCancellation*`, `OpenedWindowClose*`, `CloseRequestRejectionOutcome`, `WindowUpdateCancellation*`, `WindowAttentionPort`, `RuntimeFullscreenObservation(Sink)`, `ExclusiveFullscreen*`, `ManagedWindowPeerOwner`), `RuntimeWindowEventFlow` (+ `BoundedWindowEventScheduler`) résolus depuis `commonMain` — consommables par `platform:uikit` en Task 3–4.

- [ ] **Step 1: Déplacer les fichiers (git mv) sans les modifier**

```bash
cd /Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f
git mv kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/SurfaceCommandPort.kt kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/SurfaceCommandPort.kt
git mv kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowEventFlow.kt kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowEventFlow.kt
```

- [ ] **Step 2: Vérifier la compilabilité immédiate de ces deux fichiers**

Run: `./gradlew :kadre:runtime:compileKotlinJvm :kadre:runtime:compileKotlinIosSimulatorArm64`
Expected: SUCCESS (aucun import JVM dans ces deux fichiers — vérifié par l'exploration ; si un import JVM se révèle, le portabiliser selon le tableau des décisions et le reporter).

- [ ] **Step 3: Portabiliser WindowCommandPort.kt puis déplacer**

Dans `kadre/runtime/src/jvmMain/.../WindowCommandPort.kt` AVANT le `git mv` :
1. `java.util.IdentityHashMap` (l.19, usage dans `WindowOpenCommand` l.413) → remplacer par la primitive runtime existante : champ `private val committedSurfaces = IdentityKeyedMap<...>()` (import `org.graphiks.kadre.internal.runtime.IdentityKeyedMap` — même package, pas d'import nécessaire) en préservant les opérations exactes (put/get/remove par identité). Lire les 10 lignes d'usage avant de choisir les paramètres de type.
2. `java.util.concurrent.atomic.AtomicBoolean` (l.20, usage `ManagedWindowPeerOwner` l.524–541) → `kotlin.concurrent.AtomicBoolean` (API : `.value`, `compareAndSet`, `getAndSet` — mêmes noms que java.util.concurrent.atomic pour ces trois opérations ; vérifier chaque call-site).
Puis `git mv` vers commonMain.

- [ ] **Step 4: Compiler le runtime pour jvm + les 4 targets natives**

```bash
./gradlew :kadre:runtime:compileKotlinJvm
./gradlew :kadre:runtime:compileKotlinIosSimulatorArm64 :kadre:runtime:compileKotlinTvosSimulatorArm64
```

Expected: SUCCESS. Si un autre type JVM se révèle dans `WindowCommandPort.kt`, l'ajouter au tableau de portabilisation et le reporter.

- [ ] **Step 5: Filet existant au vert**

```bash
./gradlew :kadre:runtime:jvmTest
./gradlew :kadre:backend:appkit:appKitNativeTests
```

Expected: SUCCESS — les tests AppKit consomment ces types via jvmMain (commonMain en fait partie) sans modification.

- [ ] **Step 6: Commit**

```bash
git add kadre/runtime/src
git commit -m "refactor(runtime): move the window ports and event flow to commonMain"
```

---

### Task 2: Extraction runtime B — RuntimeWindowSurface et RuntimeWindowManager vers commonMain

**Files:**
- Modify (git mv): `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/MinimalWindowSurface.kt` → commonMain (même arborescence)
- Modify (git mv): `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt` → commonMain (même arborescence)
- Modify: `kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeLock.kt` (seulement si la decision tree du Step 2 exige une extension d'API — aucune signature publique attendue)
- Test: les suites existantes (jvmTest runtime, AppKit) + un nouveau test de non-régression concurrentiel si le Step 2 restructure un wait.

**Interfaces:**
- Consumes: Task 1 (les ports sont en commonMain) ; `RuntimeLock` (expect/actual, récursif, `isHeldByCurrentThread`) ; `IdentityKeyedMap`.
- Produces: `RuntimeWindowManager` et `RuntimeWindowSurface` résolus depuis commonMain pour toutes les targets — la condition sine qua non des Tasks 3–4.

- [ ] **Step 1: Lire AVANT de toucher — les deux usages JVM délicats**

1. `RuntimeWindowManager.kt` l.110–140 : documenter (dans le rapport) ce que guardent exactement le `ThreadLocal.withInitial` (l.119–120) et l'`AtomicLong` (l.85/135) et l'appel `System.nanoTime()` (l.136).
2. `MinimalWindowSurface.kt` l.1–200 : documenter qui acquiert le `ReentrantLock` (l.13), où `Condition.await()` (l.169–170) est atteint, et depuis quel thread.

- [ ] **Step 2: Portabiliser RuntimeWindowManager.kt puis déplacer**

Remplacements (tableau de décisions) :
- `AtomicLong` → `kotlin.concurrent.atomics.AtomicLong` + `@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)` (opérations `.value`, `incrementAndGet`, `addAndGet` — vérifier chaque call-site).
- `System.nanoTime()` → `kotlin.time.TimeSource.Monotonic.markNow().elapsedNanos()` (via un petit `private val monotonic = TimeSource.Monotonic` en compagnon pour éviter une allocation par appel : `monotonic.markNow().elapsedNanos()`).
- moniteurs `synchronized(lock) { ... }` → `lock.withLock { ... }` sur une `RuntimeLock` (récursif = sémantique monitor préservée ; le membre `lock` existant devient `private val lock = RuntimeLock()`).
- `ThreadLocal.withInitial` → **decision tree** : (a) si l'usage est une assertion de confinement au thread principal (marqueur « je suis sur le main »), remplacer par un booléen sous `RuntimeLock` combiné à `isHeldByCurrentThread` OU par un paramètre constructeur injecté par le backend (AppKit/UIKit connaissent leur main thread) — documenter le choix ; (b) si l'usage porte un état PAR THREAD réellement requis, ajouter `internal expect class RuntimeCallFrame<T>` miroir de `InteractionCallFrame` (actuals JVM ThreadLocal / js-wasm singleton / apple `@ThreadLocal var` top-level) — dans ce cas, tests natifs requis. Reporter le choix et sa justification.
Puis `git mv` vers commonMain.

- [ ] **Step 3: Compiler**

```bash
./gradlew :kadre:runtime:compileKotlinJvm :kadre:runtime:compileKotlinIosSimulatorArm64 :kadre:runtime:compileKotlinTvosSimulatorArm64
```

- [ ] **Step 4: Portabiliser MinimalWindowSurface.kt puis déplacer**

`ReentrantLock` + `Condition` (l.13, await l.169–170) : **lire d'abord** (Step 1). Le wait d'origine sérialise des interactions synchrones entre le thread appelant et le thread runtime. Sur UIKit comme AppKit, le backend marshal sur le main thread AVANT d'appeler le driver (AppKit : `onMainThread`, l.314) — l'attente croisée se réduit donc à une exécution directe sous `RuntimeLock` quand l'appelant partage le thread runtime. Remplacement : `private val lock = RuntimeLock()` ; les blocs `withLock` remplacent lock/unlock ; le `await` devient soit (a) un appel direct de la continuation sous verrou (si l'appelant est le thread runtime prouvé par lecture), soit (b) restructuré en `suspend` via le canal existant que le fichier utilise déjà pour les interactions — choisir la forme qui préserve le comportement observable, et AJOUTER dans `kadre/runtime/src/appleTest/.../NativePrimitivesConcurrencyTest.kt` (ou un nouveau fichier appleTest) un test qui exerce le chemin restructuré (ex. interaction synchrone dispatchée depuis un `Worker` ne bloque ni ne perd l'outcome). Reporter le raisonnement thread-par-thread EXIGÉ par Review Focus 1.
Puis `git mv` vers commonMain.

- [ ] **Step 5: Filet complet**

```bash
./gradlew :kadre:runtime:jvmTest
./gradlew :kadre:runtime:iosSimulatorArm64Test
./gradlew :kadre:runtime:tvosSimulatorArm64Test
./gradlew :kadre:backend:appkit:appKitNativeTests
```

Expected: SUCCESS partout — c'est le filet qui prouve « préserver les tests AppKit » (roadmap l.188).

- [ ] **Step 6: Commit**

```bash
git add kadre/runtime/src
git commit -m "refactor(runtime): move the window manager and surface to commonMain"
```

---

### Task 3: UIKit attach infrastructure — registre par scène, ownership, lifecycle, validations

**Files:**
- Modify: `kadre/platform/uikit/build.gradle.kts` (friend-modules wiring + `create("uikitTest")`)
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitSceneRegistry.kt`
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitOwnership.kt`
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitLifecycleSource.kt`
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitLifecycleReducer.kt`
- Test: `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitLifecycleReducerTest.kt`

**Interfaces:**
- Consumes: `KadreFailure` (`InvalidRequest(field)`, `AlreadyInUse(KadreResourceKind.Host)`, `PlatformFailure(KadrePlatform.UIKit, domain, code)`), `LifecycleState`/`LifecycleCapabilities` (foundation), `RuntimeFailureReporter`, pattern `WebHostRegistry` (`kadre/platform/web/src/webMain/.../WebHostRegistry.kt:13-45`).
- Produces (consommés par Task 4) :
  - `internal class UIKitSceneRegistry { fun reserve(scene: UIWindowScene): KadreResult<UIKitSceneReservation> }` + `internal class UIKitSceneReservation { fun release() }` (idempotent, `AlreadyInUse(Host)` si la scène est déjà réservée — comparaison `isEqual`).
  - `internal class UIKitOwnership(private val reservation: UIKitSceneReservation)` : `fun observeLifecycle(source: UIKitLifecycleSource, onSnapshot: (UIKitLifecycleSnapshot) -> Unit, onDisconnect: () -> Unit, onLateCallback: () -> Unit): KadreResult<Unit>` (installe les observers NSNotificationCenter filtrés par la scène, retourne une failure `PlatformFailure(UIKit, "uikit-host", "lifecycle-install-failed")` si une installation lève), `fun releaseAfterAttachFailure()`, `fun release()` (retire tous les observer tokens — ordre : observers d'abord, réservation ensuite).
  - `internal data class UIKitLifecycleSnapshot(val foreground: LifecycleForeground, val activation: LifecycleActivation)` + `internal object UIKitLifecycleReducer { fun initial(scene: UIWindowScene): UIKitLifecycleSnapshot?; fun reduce(notificationName: String, snapshot: UIKitLifecycleSnapshot): UIKitLifecycleSnapshot? }` (mapping du tableau de décisions ; retourne null pour une notification sans effet).
  - `internal class UIKitLifecycleSource` : wrapper des observer tokens (création via `NSNotificationCenter.defaultCenter.addObserverForName(name, scene, NSOperationQueue.mainQueue(), block)` ; `.object` casté/`isEqual`-vérifié contre la scène) ; `fun close()` (removeObserver de tous les tokens).

- [ ] **Step 1: Friend-modules + source set de test**

Dans `kadre/platform/uikit/build.gradle.kts`, après le bloc `sourceSets` existant, ajouter le wiring `-friend-modules` (miroir de `kadre/runtime/build.gradle.kts:95–119`) pour que `uikitMain` voie les internals de runtime :

```kotlin
// Phase 1 : uikitMain consomme les internals de runtime (RuntimeWindowManager et ses ports).
// Miroir du wiring runtime→foundation : -friend-modules est mono-valeur last-write-wins,
// donc le klib propre d'uikit est répété avec runtime (et foundation pour les tests).
val uikitNativeTargets = listOf("IosArm64", "IosSimulatorArm64", "TvosArm64", "TvosSimulatorArm64")
val runtimeProject = project(":kadre:runtime")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    val targetSegment = uikitNativeTargets.firstOrNull { name.endsWith(it) } ?: return@configureEach
    val lowerSegment = targetSegment.replaceFirstChar(Char::lowercase)
    val ownMainKlib = layout.buildDirectory.dir("classes/kotlin/$lowerSegment/main/klib/${project.name}")
    val runtimeMainKlib = runtimeProject.layout.buildDirectory
        .dir("classes/kotlin/$lowerSegment/main/klib/${runtimeProject.name}")
    dependsOn(runtimeProject.tasks.named("compileKotlin$targetSegment"))
    compilerOptions.freeCompilerArgs.add(
        ownMainKlib.zip(runtimeMainKlib) { own, runtime ->
            "-friend-modules=${own.asFile.absolutePath}${File.pathSeparator}${runtime.asFile.absolutePath}"
        },
    )
}
```

Et dans `sourceSets { }` : `val uikitTest = create("uikitTest"); iosTest.get().dependsOn(uikitTest); tvosTest.get().dependsOn(uikitTest)`.

- [ ] **Step 2: Écrire le test du reducer d'abord (TDD)**

`UIKitLifecycleReducerTest.kt` — test K/N pur (pas d'objet UIKit) :

```kotlin
package org.graphiks.kadre.internal.uikit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UIKitLifecycleReducerTest {
    // Les constantes exactes sont celles de platform.UIKit (NSNotificationName importées comme String) ;
    // les retrouver par référence (platform.UIKit.UISceneDidActivateNotification) dans le code du reducer,
    // pas en copiant des littéraux ici.

    @Test
    fun activationSequenceFollowsTheNormativeMapping() {
        var s = UIKitLifecycleReducer.reduce(
            platform.UIKit.UISceneWillEnterForegroundNotification,
            UIKitLifecycleSnapshot(foreground = LifecycleForeground.Background, activation = LifecycleActivation.Inactive),
        )!!
        assertEquals(LifecycleForeground.Foreground, s.foreground)
        assertEquals(LifecycleActivation.Inactive, s.activation)
        s = UIKitLifecycleReducer.reduce(platform.UIKit.UISceneDidActivateNotification, s)!!
        assertEquals(LifecycleActivation.Active, s.activation)
        s = UIKitLifecycleReducer.reduce(platform.UIKit.UISceneWillDeactivateNotification, s)!!
        assertEquals(LifecycleActivation.Inactive, s.activation)
        assertEquals(LifecycleForeground.Foreground, s.foreground)
        s = UIKitLifecycleReducer.reduce(platform.UIKit.UISceneDidEnterBackgroundNotification, s)!!
        assertEquals(LifecycleForeground.Background, s.foreground)
        assertEquals(LifecycleActivation.Inactive, s.activation)
    }

    @Test
    fun unknownNotificationIsIgnored() {
        assertNull(UIKitLifecycleReducer.reduce("inconnue", UIKitLifecycleSnapshot(LifecycleForeground.Foreground, LifecycleActivation.Active)))
    }

    @Test
    fun disconnectedNotificationIsNotAReducibleState() {
        // DidDisconnect n'est PAS un snapshot : c'est une terminaison routée à part.
        assertNull(UIKitLifecycleReducer.reduce(platform.UIKit.UISceneDidDisconnectNotification, UIKitLifecycleSnapshot(LifecycleForeground.Foreground, LifecycleActivation.Active)))
    }
}
```

(Réimporter proprement : `platform.UIKit.*` pour les noms de notifications, `org.graphiks.kadre.application.LifecycleForeground/LifecycleActivation` — vérifier les noms réels des types foundation dans `kadre/foundation/src/commonMain/.../application/Lifecycle.kt` et adapter les imports SANS changer les assertions.)

- [ ] **Step 3: Exécuter — échec attendu (types absents)**

```bash
./gradlew :kadre:platform:uikit:iosSimulatorArm64Test
```
Expected: FAIL (compilation — `UIKitLifecycleReducer` n'existe pas).

- [ ] **Step 4: Implémenter reducer, registry, ownership, source**

Implémentation minimale conforme aux signatures « Produces » ci-dessus. Points exacts :
- Registry : `private val reserved = mutableListOf<UIWindowScene>()` sous `RuntimeLock` ; `reserve` rejette si `any { it.isEqual(scene) }` → `KadreResult.Failure(KadreFailure.AlreadyInUse(KadreResourceKind.Host))`.
- Source : le block NSNotificationCenter reçoit `NSNotification?` ; vérifier `notification?.`object`?.let { it.isEqual(scene) } == true` avant de router (défense en profondeur : le `object =` du filter suffit, la double vérification est gratuite) ; sur `UISceneDidDisconnectNotification` → `onDisconnect()` ; sinon `reduce(...)?.let(onSnapshot)`.
- Ownership : `release()` retire d'abord les observers (`source.close()`), puis `reservation.release()`.

- [ ] **Step 5: Tests natifs au vert sur les deux simulateurs**

```bash
./gradlew :kadre:platform:uikit:iosSimulatorArm64Test :kadre:platform:uikit:tvosSimulatorArm64Test
./gradlew :kadre:platform:uikit:compileKotlinIosArm64 :kadre:platform:uikit:compileKotlinTvosArm64
```

- [ ] **Step 6: Commit**

```bash
git add kadre/platform/uikit
git commit -m "feat(uikit): add per-scene registry, ownership and the scene lifecycle source"
```

---

### Task 4: UIKit window runtime et `KadreIos.attach` complet

**Files:**
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitWindowRuntime.kt` (driver : compose `RuntimeWindowManager` + `UIKitWindowCommandPort`)
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitWindowCommandPort.kt`
- Create: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreIos.kt`
- Modify: `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreUikitProbe.kt` (KDoc : « remplacé par KadreIos.attach en phase 1 » devient « complété par KadreIos.attach » — la clause de non-garantie est retirée, cf. sweep N1)
- Test: `kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitAttachValidationTest.kt` (validations purses avec faux objets si possible) — sinon preuve par driver en Task 5.

**Interfaces:**
- Consumes: Tasks 1–3 ; `RuntimeHostController.withComponents(...)` (`kadre/runtime/src/commonMain/.../RuntimeHostController.kt:304/329`), `RuntimeSessionComponents` (constructor), `RuntimePrimarySurface(surface, teardown)`, `RuntimeWindowManager` constructor (note phase 0, 13 params), `KadreIos.attach` normatif (DESIGN.md:1884).
- Produces (consommés par Task 5) :
  - `public object KadreIos { public fun attach(windowScene: UIWindowScene, window: UIWindow, surfaceView: UIView, applicationFactory: KadreApplicationFactory, policy: KadrePolicy = KadrePolicies.Default): KadreResult<KadreSession> }` — signature EXACTE de DESIGN.md §15.2.
  - `internal class UIKitWindowRuntime(window: UIWindow, view: UIView, policy: KadrePolicy, failureReporter: RuntimeFailureReporter) : AutoCloseable` — expose `val manager: RuntimeWindowManager`, `val surface: RuntimeWindowSurface` (la surface primaire), `override fun close()` (ordre AppKit l.146–158 : `commandPort.beginClose()` → `manager.close()` → `commandPort.finishClose()`).

- [ ] **Step 1: Étudier le patron AppKit AVANT d'écrire** (exigé)

Lire intégralement `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt` lignes 72–160 (composition), 274–320 (port), 430–460 (requestOpen), 590–711 (close, prepare, commit, `toRuntimeSnapshot` l.2378–2384). Le rapport de tâche cite ces lectures.

- [ ] **Step 2: Implémenter UIKitWindowCommandPort**

Un seul objet implémente `WindowCommandPort, SurfaceCommandPort` (comme AppKit). Contenu exact :
- Constructeur : `(resources: ResourceBudgetPolicy, failureReporter: RuntimeFailureReporter, stimulusSink: (SurfaceStimulus) -> Boolean, windowStateSink: (WindowId, WindowState) -> Boolean, readySink: (SurfaceId) -> Unit)` — les sinks pointent vers le manager (créé après le port, filé ensuite).
- Registre de peers : `UIKitWindowPeerOwner(private val window: UIWindow, private val view: UIView) : WindowPeerOwner` — `close()` NE touche JAMAIS aux objets UIKit (contrainte roadmap l.120) : il ne fait rien côté natif (destruct endogène).
- `requestOpen(command: WindowOpenCommand)` : enregistrer l'entry sous `RuntimeLock`, puis **commit immédiat** (le window UIKit existe déjà — pas de préparation asynchrone AppKit) : `command.commit(owner, effectiveSpec, initialSnapshot) { readySink(surfaceId) }` où `initialSnapshot = SurfaceInitialSnapshot(metrics, focus, visibility, occlusion, appearance)` construit depuis la vue : `logical = bounds.useContents { LogicalSize(width=size.width, height=size.height) }` (vérifier le nom réel du type metrics dans foundation — `SurfaceMetrics(logical, physical, scaleFactor, safeAreaInsets)`, lire le constructeur réel), `physical = logical × displayScale`, `scaleFactor = traitCollection.displayScale`, `safeAreaInsets = view.safeAreaInsets.useContents { ... }` (champs foundation réels), `visibility = if (window.isHidden) SurfaceVisibility.Hidden else SurfaceVisibility.Visible`, `occlusion = SurfaceOcclusion.Visible`, `focus = <valeur « aucun focus » de SurfaceFocus — lire l'enum>`, `appearance = selon traitCollection.userInterfaceStyle (Dark → SurfaceAppearance.Dark, sinon Light/Unknown selon l'enum réel)`. Tous les noms d'enums/valeurs se lisent dans foundation — adapter les imports, JAMAIS les valeurs sémantiques.
- `requestUpdate`/`apply` : `TemporarilyUnavailable(retryable=false)` pour toute mise à jour (phase 1 = snapshot initial figé ; phase 2 livre les updates) — même posture que AppKit l.593–598.
- `requestRedraw(command)` : enregistre puis `stimulusSink(SurfaceStimulus.RedrawDrawn(...))`? NON — phase 1 : retourner `KadreResult.Failure(KadreFailure.Unsupported(KadreOperation.Redraw?))` — LIRE l'opération réelle utilisée par AppKit l.564–579 et rester au plus proche : si AppKit admet, UIKit phase 1 peut admettre sans effet natif et publier le stimulus « drawn » sous verrou ; choisir la posture AppKit-fidèle et la documenter. `requestOpenedClose` : retirer l'entry et retourner `OpenedWindowCloseOutcome.Closed` (le host garde sa fenêtre).

- [ ] **Step 3: Implémenter UIKitWindowRuntime**

```kotlin
internal class UIKitWindowRuntime internal constructor(
    window: UIWindow,
    view: UIView,
    policy: KadrePolicy,
    failureReporter: RuntimeFailureReporter,
) : AutoCloseable {
    private val commandPort: UIKitWindowCommandPort = UIKitWindowCommandPort(
        resources = policy.resources,
        failureReporter = failureReporter,
        stimulusSink = { stimulus -> manager.acceptSurfaceStimulus(stimulus) },   // filé après création du manager (var lateinit ou provider)
        windowStateSink = { id, state -> manager.acceptWindowGeometryObservation(id, state) },
        readySink = { /* la surface est visible dès l'attach : rien à faire côté natif */ },
    )
    internal val manager: RuntimeWindowManager = RuntimeWindowManager(
        resources = policy.resources,
        commandPort = commandPort,
        surfaceCommandPort = commandPort,
        platform = KadrePlatform.UIKit,
        failureReporter = failureReporter,
        publicWindowCapabilities = false,
        enabledWindowUpdateCapabilities = emptySet(),
        publicSurfaceCapabilities = false,
        enabledSurfaceCapabilities = unsupportedSurfaceCapabilities(),
    )
    // Fenêtre primaire : demandée AU manager au runtime (le reducer reste l'autorité — aucune
    // écriture directe dans l'état). Sur le main thread (l'appelant de attach EST sur le main).
    init {
        val spec = <WindowSpec minimal : contentSize = bounds logiques — lire le constructeur réel de WindowSpec dans foundation>
        val request = manager.requestWindow(spec)
        check(request is KadreResult.Success) { "primary window admission failed: $request" }
        // L'admission déclenche requestOpen sur le port, qui committe la UIWindow fournie.
    }
    internal val surface: RuntimeWindowSurface get() = <la surface du peer primaire, exposée par le port>
    override fun close() { commandPort.beginClose(); manager.close(); commandPort.finishClose() }
}
```

(Vérifier les noms réels : `unsupportedSurfaceCapabilities()` existe dans runtime — cf. constructor AppKit l.141 ; `requestWindow(spec): KadreResult<WindowRequest>` ; la surface du peer est accessible via le registre du port.)

- [ ] **Step 4: Implémenter KadreIos.attach — le ladder exact**

```kotlin
@OptIn(ExperimentalKadreApi::class) // si KadreHost/KadrePolicies l'exigent — vérifier
public object KadreIos {
    public fun attach(
        windowScene: UIWindowScene,
        window: UIWindow,
        surfaceView: UIView,
        applicationFactory: KadreApplicationFactory,
        policy: KadrePolicy = KadrePolicies.Default,
    ): KadreResult<KadreSession> {
        // 1. Validations (roadmap l.106) — fields fermés "window"/"surfaceView".
        val scene: UIWindowScene = window.windowScene
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("window"))
        if (!scene.isEqual(windowScene)) return KadreResult.Failure(KadreFailure.InvalidRequest("window"))
        if (windowScene.session == null || windowScene.activationState == platform.UIKit.UISceneActivationState.Unattached)
            return KadreResult.Failure(KadreFailure.InvalidRequest("window"))
        val viewWindow: UIView? = surfaceView.window
        if (viewWindow == null || !viewWindow.isEqual(window)) return KadreResult.Failure(KadreFailure.InvalidRequest("surfaceView"))
        // 2. Réservation AVANT toute installation (roadmap l.107).
        val registry = UIKitSceneRegistry()
        val reservation = when (val r = registry.reserve(windowScene)) {
            is KadreResult.Success -> r.value
            is KadreResult.Failure -> return KadreResult.Failure(r.failure)
        }
        val ownership = UIKitOwnership(reservation)
        val failureReporter = RuntimeFailureReporter { }
        // 3. Source de lifecycle + observations avec rollback par étape.
        //    Le relais (update/detach) pointe vers le controller, créé plus bas : le holder est
        //    un simple AtomicReference<UIKitSessionRelay?> rempli après withComponents — les
        //    closures de l'observateur le lisent (null avant Running = rien à relayer).
        val source = UIKitLifecycleSource(windowScene)
        var runtime: UIKitWindowRuntime? = null
        val relay = AtomicReference<UIKitSessionRelay?>(null)
        val initial = UIKitLifecycleReducer.initial(windowScene)
            ?: return KadreResult.Failure(KadreFailure.InvalidRequest("window"))
        when (val obs = ownership.observeLifecycle(
            source,
            onSnapshot = { snap -> relay.get()?.update(snap) },
            onDisconnect = { relay.get()?.detach() },
            onLateCallback = { /* compteur de sentinelle : jamais d'effet après terminaison */ },
        )) { is KadreResult.Failure -> { ownership.releaseAfterAttachFailure(); return KadreResult.Failure(obs.failure) } }
        // 4. Controller + factory (le driver est créé DANS la factory — patron AppKit l.307-340).
        val controller = RuntimeHostController.withComponents(
            platform = KadrePlatform.UIKit,
            componentsFactory = { _, _ ->
                val rt = UIKitWindowRuntime(window, surfaceView, policy, failureReporter)
                runtime = rt
                RuntimeSessionComponents(
                    windows = rt.manager,
                    primarySurface = RuntimePrimarySurface(rt.surface) { rt.surface.detach() },
                    closeAction = rt::close,
                )
            },
            initialLifecycleState = <LifecycleState depuis initial — mapping foreground/activation>,
            sessionRevocationHandler = { ownership.releaseObserversOnly() },   // admission révoquée en premier (roadmap l.120)
            sessionObserver = { _, _ -> ownership.release() },                  // terminaison : observers + réservation
        )
        relay.set(UIKitSessionRelay(controller))
        // 5. attach — failure → rollback complet.
        return when (val r = controller.attach(CoroutineScope(SupervisorJob() + Dispatchers.Main), applicationFactory, policy)) {
            is KadreResult.Success -> r
            is KadreResult.Failure -> { ownership.releaseAfterAttachFailure(); KadreResult.Failure(r.failure) }
        }
    }
}
```

**Adaptations obligatoires à la réalité du code (à lire, jamais devinées) :** les noms exacts des params de `RuntimeHostController.withComponents` (l.304 ou l.329 selon le besoin du revocation handler — l'overload interne l.329 porte `sessionRevocationHandler`), les champs de `RuntimeSessionComponents` (ordre : `windows`, `rawInputPort`, `displayPort`, `gamepadPort`, `inputDevicePort`, `capturePort`, `primarySurface`, `closeAction` — lire le constructeur), `LifecycleState`/`LifecycleForeground`/`LifecycleActivation` (foundation Lifecycle.kt), le relais `update/detach` (le relais = `controller.updateLifecycle(...)` / `controller.detach()` — le holder `runtime` doit donc aussi porter le controller ; restructurer le holder en petite classe `UIKitSessionRelay(controller)` construite après le controller, les closures de l'observateur la référencent via un `lateinit`/AtomicReference).
**Vérifications de compile immédiates :** `./gradlew :kadre:platform:uikit:compileKotlinIosSimulatorArm64 :kadre:platform:uikit:compileKotlinTvosSimulatorArm64`.
**KDoc de KadreUikitProbe.kt** : remplacer « remplacé par `KadreIos.attach` en phase 1 » par « complété par `KadreIos.attach` (phase 1) ».

- [ ] **Step 5: Commit**

```bash
git add kadre/platform/uikit
git commit -m "feat(uikit): deliver the public KadreIos.attach with per-scene ownership"
```

---

### Task 5: Driver multi-scène iPadOS + parcours tvOS — la preuve O3

**Files:**
- Modify: `kadre/contracts/driver/uikit/project.yml` (iOS `UIApplicationSupportsMultipleScenes: true`)
- Modify: `kadre/contracts/driver/uikit/apps/Shared/DriverFixture.swift` (fixtures PAR scène)
- Modify: `kadre/contracts/driver/uikit/apps/Shared/DriverSceneDelegate.swift` (install par scène)
- Create: `kadre/contracts/driver/uikit/apps/Shared/DriverApplication.swift` (l'application du host : une classe Swift conforme au protocole Kotlin `KadreApplicationFactory`/`KadreApplication` qui capture les faits de scope pour les tests)
- Modify: `kadre/contracts/driver/uikit/tests/Shared/KadreUikitDriverTests.swift` (nouveaux tests BCK-013/WIN-009, noms figés)
- Modify: `kadre/contracts/driver/uikit/KadreUikitDriver.xcodeproj/` (xcodegen generate — regénéré ET committé)

**Interfaces:**
- Consumes: `KadreIos.attach` (Task 4, exposé au Swift sous `KadreIos.shared.attach(windowScene:window:surfaceView:applicationFactory:policy:)` — objet Kotlin = classe + `.shared`), `KadreSession` (id/state/requestStop/close/awaitTermination), les protocoles exportés `KadreApplicationFactory` (méthode `create(sessionId:rootScope:)`) et `KadreApplication`.
- Produces: les scénarios/noms de test FIGÉS pour Task 6 (cf. Step 3).

- [ ] **Step 1: Fixture par scène + application du host**

`DriverFixture` devient un store par scène (identité `ObjectIdentifier(scene)`), avec `fixture(for:)` créé au `willConnectTo`, et conserve `waitUntilReady()` par fixture. Chaque fixture porte : `window`, `observedView`, `detachedView`, `becameActive`, PLUS les faits capturés par l'application Kadre : `primarySurfaceId: String?`, `primaryWindowSurfaceId: String?`, `primarySurfaceIsPrimaryWindowSurface: Bool` (comparaison `===` Swift entre deux objets Kotlin — objets originaires de Kotlin : identité stable ; si le pont surprend, le REPORTER au contrôleur au lieu de le contourner), et `session: KadreSession?`.
`DriverApplication` (Swift, conforme `KadreApplicationFactory` ET porteur de l'application) :

```swift
final class DriverApplication: KadreApplicationFactory {
    private let fixture: DriverFixture
    init(fixture: DriverFixture) { self.fixture = fixture }
    func create(sessionId: KadreSessionId, rootScope: KadreScope) -> KadreApplication {
        return DriverKadreApplication(fixture: fixture, scope: rootScope)
    }
}

final class DriverKadreApplication: KadreApplication {
    // Lire la forme RÉELLE du protocole KadreApplication dans le header généré
    // (méthode(s) exportée(s) — généralement onCreate(scope:) ou similaire) et s'y conformer exactement.
    private let fixture: DriverFixture
    private let scope: KadreScope
    init(fixture: DriverFixture, scope: KadreScope) { self.fixture = fixture; self.scope = scope }
    func onCreate(scope: KadreScope) {
        let primary = scope.windows.state.value.primary
        fixture.primarySurfaceId = scope.primarySurface.value?.id.toString()
        fixture.primaryWindowSurfaceId = primary?.surface.id.toString()
        fixture.primarySurfaceIsPrimaryWindowSurface = (scope.primarySurface.value === primary?.surface)
    }
}
```

(Les noms exportés (`KadreSessionId`, `.toString()`, la méthode du protocole) se lisent dans `kadre/platform/uikit/build/bin/iosSimulatorArm64/debugFramework/KadreUikit.framework/Headers/KadreUikit.h` après Task 4 — relire le header AVANT d'écrire le Swift final ; adapter les noms, jamais la sémantique.)

- [ ] **Step 2: project.yml multi-scène iOS + regénération**

`UIApplicationSupportsMultipleScenes: true` pour `KadreUikitDriverIos` UNIQUEMENT (tvOS reste `false` — topologie mono-scène). `cd kadre/contracts/driver/uikit && xcodegen generate`, committer le projet regénéré.

- [ ] **Step 3: Les tests (noms FIGÉS pour Task 6)**

Classe `KadreUikitAttachTests` (nouvelle, même fichier tests/Shared) — chaque test suit : attendre le fixture → `KadreIos.shared.attach(...)` → assertions via le fixture. Scénarios et noms EXACTS :

```swift
final class KadreUikitAttachTests: XCTestCase {
    // BCK-013 / uikit-attach-window-mismatch
    func testAttachRejectsWindowFromAnotherScene() throws { /* iOS : fenêtre de la scène 2 + scène 1 → InvalidRequest window */ }
    // BCK-013 / uikit-attach-surfaceview-mismatch
    func testAttachRejectsViewOutsideTheProvidedWindow() throws { /* detachedView → InvalidRequest surfaceView */ }
    // BCK-013 / uikit-attach-scene-busy
    func testSecondAttachOnTheSameSceneIsAlreadyInUse() throws { /* 1er attach OK, 2e attach même scène → AlreadyInUse(Host), AUCUN observer en plus (compteur du fixture) */ }
    // WIN-009 / uikit-primary-is-provided-window + uikit-primary-surface-same-instance
    func testPrimaryWindowAndSurfaceIdentity() throws { /* primarySurfaceId == primaryWindowSurfaceId && primarySurfaceIsPrimaryWindowSurface && les metrics initial snapshot = bounds Swift */ }
    // BCK-013 / uikit-two-scenes-independent (iOS uniquement — multi-scène)
    func testTwoScenesRunIndependentSessions() throws { /* scène 1 + scène 2 attachées ; requestStop sur session1 ; session2 reste Running (state.value) + fixture2 intact */ }
    // BCK-013 / uikit-disconnect-terminates (iOS : destruction RÉELLE de la scène 2)
    func testSceneDisconnectionTerminatesOnlyItsSession() throws { /* requestSceneSessionDestruction(scene2.session) ; session2 → Terminated ; session1 intacte ; window/view de la scène 2 NON détruites par Kadre (référence Swift encore utilisable) */ }
    // BCK-013 / uikit-stop-then-reattach
    func testStopThenReattachOnTheSameScene() throws { /* requestStop immédiat après attach (arrêt pendant Starting), awaitTermination ; nouvel attach sur la MÊME scène réussit (réservation libérée) */ }
    // BCK-013 / uikit-disconnect-during-stop (les DEUX familles)
    func testDisconnectDuringStopTerminatesExactlyOnce() throws { /* requestStop() puis IMMÉDIATEMENT poster DidDisconnect (même passe du run loop) : awaitTermination livre UN terminal unique (expectation comptée 1), pas de crash, pas de double Stopping ; nouvel attach possible ensuite */ }
    // BCK-013 / uikit-lifecycle-transitions (tvOS + iOS — notifications réelles sur la scène vivante)
    func testLifecycleTransitionsFollowTheScene() throws { /* post DidEnterBackground → scope fixture lifecycle Background+Inactive ; WillEnterForeground+DidActivate → Foreground+Active ; WillDeactivate → Inactive */ }
    // BCK-013 / uikit-late-callback-after-termination
    func testLateNativeCallbackAfterTerminationIsIgnored() throws { /* terminer puis poster DidActivate ×3 : aucune crash, session reste Terminated, compteur de callbacks du fixture == 0 */ }
}
```

Attentes bornées (`waitUntil` helpers sur les états — jamais de sommeil nu). Les transitions se testent via `NotificationCenter.default.post(name: UISceneDidEnterBackgroundNotification, object: sceneVivante)` (décision : frontière SDK réelle de l'adapter). `awaitTermination()` est async Swift — utiliser une expectation XCTest.

- [ ] **Step 4: Exécuter sur les deux familles**

```bash
cd /Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f
./gradlew :kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64 :kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64
cd kadre/contracts/driver/uikit
xcodebuild -project KadreUikitDriver.xcodeproj -scheme KadreUikitDriverIos -destination 'platform=iOS Simulator,name=iPhone 17,OS=27.0' -resultBundlePath build/xcresult/ios.xcresult CODE_SIGNING_ALLOWED=NO test
xcodebuild -project KadreUikitDriver.xcodeproj -scheme KadreUikitDriverTvOs -destination 'platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0' -resultBundlePath build/xcresult/tvos.xcresult CODE_SIGNING_ALLOWED=NO test
```

Expected: `** TEST SUCCEEDED **` ×2. Sur iOS : le test two-scenes demande une DEUXIÈME scène — si `requestSceneSessionActivation` ne connecte pas la scène dans le environnement de test unitaire (l'app est foreground, la scène 1 active), capturer le comportement RÉEL et l'adapter (activationOptions, userActivity factice, ou déclenchement depuis `DriverAppDelegate` via un hook du fixture) — reporter la mécanique retenue. Une impossibilité réelle (jamais contournée par un faux test) remonte au contrôleur avec l'erreur exacte.

- [ ] **Step 5: Commit**

```bash
git add kadre/contracts/driver/uikit
git commit -m "test(uikit): prove scene attach, independence and lifecycle on both families"
```

---

### Task 6: Contrats BCK-013 + WIN-009 — registre, evidence, validateur, sentinelles

**Files:**
- Modify: `kadre/contracts/registry/contracts.tsv` (2 lignes `active`)
- Modify: `kadre/contracts/driver/uikit/contracts/evidence.tsv` (rows par target)
- Modify: `kadre/contracts/validator/build.gradle.kts` (line 21 : `uikitContractIds = listOf("BCK-012", "BCK-013", "WIN-009")` — les tâches sont générées mécaniquement par le flatMap existant)
- Modify: `scripts/test-uikit-simulator.sh` (asserts d'artifacts pour les 3 ids + runtime native tests, cf. Task 7 pour cette partie)
- Modify: `kadre/capabilities/uikit.md` (2 nouvelles lignes 7 colonnes + mise à jour de l'« état absent »)
- Test: les mutations de sentinelles (preuves citées, reverts avant commit)

**Interfaces:**
- Consumes: Task 5 (méthodes XCTest exactes) ; pipeline BCK-012 (gabarit intégral).
- Produces: `generateUikitBCK013{Ios,Tvos}SimulatorArm64ContractEvidence`, `generateUikitWIN009{...}`, `validate{Ios,Tvos}SimulatorArm64UikitContractEvidence` (déjà génériques via la liste).

- [ ] **Step 1: Registry rows** (vérifier le vocabulaire des colonnes sur BCK-012/APK-002 avant ; cellules TAB-séparées, listes à virgules SANS espace)

```
BCK-013	active	DESIGN.md#15.2	UIKit scene host attach and lifecycle	cross-scene leak, revoked-admission resurrection, bridged identity confusion	O3	uikit-attach-window-mismatch,uikit-attach-surfaceview-mismatch,uikit-attach-scene-busy,uikit-two-scenes-independent,uikit-disconnect-terminates,uikit-stop-then-reattach,uikit-disconnect-during-stop,uikit-lifecycle-transitions,uikit-late-callback-after-termination	iosSimulatorArm64,tvosSimulatorArm64	-	uikit-scene-single-owner,uikit-late-callback-revoked	-
WIN-009	active	DESIGN.md#15.2	UIKit primary window and surface identity	fabricated primary, duplicated surface identity or wrong initial metrics	O3	uikit-primary-is-provided-window,uikit-primary-surface-same-instance	iosSimulatorArm64,tvosSimulatorArm64	-	-	-
```

- [ ] **Step 2: evidence.tsv** — mapping exact lu depuis le XML produit

Exécuter d'abord `./gradlew :kadre:contracts:driver:uikit:simulatorTests`, LIRE `build/contract-evidence/<target>/test-results/<target>/TEST-*.xml`, puis écrire les rows (testClass `KadreUikitAttachTests`, testName exact avec parenthèses). Deux scénarios tvOS-only nuancés : `uikit-two-scenes-independent` et `uikit-disconnect-terminates` (destruction réelle) sont iOS-only → **requiredTargets de BCK-013 couvre les deux familles via les AUTRES scénarios ; les 2 scénarios iOS-only portent des rows `iosSimulatorArm64` uniquement** (le validateur exige chaque scenario du registre sur au moins une target prouvée — relire `ContractEvidenceMapping` pour la règle exacte de couverture avant d'écrire ; si la règle exige TOUT scenario sur TOUTES les requiredTargets, alors le registre scinde : BCK-013 (les 7 scénarios communs, 2 targets) + les scénarios iOS-only deviennent des scenarios de BCK-013 avec rows iOS-only SEULEMENT si le mapping l'accepte, sinon créer BCK-014 « UIKit iPadOS multi-scene » (iOS-only, 2 scenarios) — DÉCIDER EN LISENT LE VALIDATEUR et reporter).

- [ ] **Step 3: Validator + gate asserts**

`uikitContractIds = listOf("BCK-012", "BCK-013", "WIN-009")` ; le script `all` boucle les asserts sur `BCK-012.json, BCK-013.json, WIN-009.json` par target.

- [ ] **Step 4: Exécuter le gate + preuves can-fail OBLIGATOIRES (sorties citées)**

1. `./gradlew :kadre:contracts:validator:generateUikitContractEvidence` + les 2 validate → vert.
2. Sentinelle `uikit-scene-single-owner` : mutation = retirer l'appel `registry.reserve` (attach accepte toujours) → `testSecondAttachOnTheSameSceneIsAlreadyInUse()` échoue SEUL sur les deux familles (première session non touchée). Revert.
3. Sentinelle `uikit-late-callback-revoked` : mutation = `release()` ne retire PAS les observers → `testLateNativeCallbackAfterTerminationIsIgnored()` échoue SEUL (compteur > 0). Revert.
4. Mapping : retirer une row d'evidence.tsv → rejet spécifique cité. Rétablir.

- [ ] **Step 5: capabilities/uikit.md + commit d'activation**

Deux nouvelles entrées 7 colonnes ; l'entrée phase 0 garde son « état absent » mis à jour (attach n'y est plus listé comme absent). UN SEUL commit : code+registre+evidence+sentinelles+capabilities.

```bash
git add kadre/contracts kadre/capabilities/uikit.md scripts/test-uikit-simulator.sh
git commit -m "feat(uikit): activate BCK-013 and WIN-009 with both-families evidence"
```

---

### Task 7: Gate hardening (native tests) + docs closure

**Files:**
- Modify: `scripts/test-uikit-simulator.sh` (mode `all` : les 2 tâches de tests natifs runtime avant les asserts)
- Modify: `kadre/DESIGN.md` §15.2 (note « livré en phase 1 » sur l'attach), `kadre/BACKEND-CAPABILITIES.md` §6.2 (attach livré : la phrase passe au présent pour l'attach/lifecycle, updates/requests restent phase 2/4), `kadre/PUBLIC-API-CATALOG.md` (entrée KadreIos : `KadreIos.attach` réelle désormais dans le package)
- Test: script `all` vert de bout en bout.

**Interfaces:**
- Consumes: Tasks 1–6.
- Produces: le gate phase 1 complet ; les docs alignés sur le livré.

- [ ] **Step 1: Native tests dans le gate `all`**

Dans le bloc `all)` du script, AVANT les asserts d'artifacts :

```bash
    # Tests natifs du runtime sur les deux simulateurs (mineur N2 de phase 0, promu phase 1) :
    # les rapports XML sont régénérés à chaque gate.
    run_gradle ":kadre:runtime:iosSimulatorArm64Test"
    run_gradle ":kadre:runtime:tvosSimulatorArm64Test"
```

- [ ] **Step 2: Docs — trois éditions chirurgicales**

1. `DESIGN.md` §15.2 après les bullets tvOS : « L'attach, le lifecycle par scène et les propriétaires sont livrés (phase 1) ; surface dynamique (phase 2), façade Swift (phase 3), demandes de scènes (phase 4). »
2. `BACKEND-CAPABILITIES.md` §6.2 : après la signature, préciser « Livré en phase 1 : l'attach, les validations, la réservation par scène, le relais de lifecycle par notifications et la terminaison sur disconnect. Les mises à jour de surface et `requestWindow` restent couvertes par les phases 2 et 4. »
3. `PUBLIC-API-CATALOG.md` ligne 30 : la ligne du package gagne `KadreIos.attach` explicite.

- [ ] **Step 3: Gate complet + commit**

```bash
./scripts/test-uikit-simulator.sh all
git add scripts/test-uikit-simulator.sh kadre/DESIGN.md kadre/BACKEND-CAPABILITIES.md kadre/PUBLIC-API-CATALOG.md
git commit -m "test(uikit): run the runtime native tests in the gate and close the phase 1 docs"
```

---

### Task 8: Sweep du critère de sortie + rapport

**Files:**
- Aucun fichier produit obligatoire ; retouches mineures possibles.
- Test: la checklist d'exit, à froid.

**Interfaces:**
- Consumes: toutes les tasks.
- Produces: le verdict d'exit + le rapport (les éléments de design durables restent dans les commits).

- [ ] **Step 1: Sweep à froid** — dans l'ordre, chaque commande < 9 min :

```bash
git status --short                     # propre
./gradlew :kadre:runtime:jvmTest       # zéro régression JVM
./gradlew :kadre:runtime:iosSimulatorArm64Test :kadre:runtime:tvosSimulatorArm64Test
./gradlew :kadre:backend:appkit:appKitNativeTests   # tests AppKit préservés
./scripts/test-uikit-simulator.sh all  # les deux familles + 3 contrats + device links + native tests
```

- [ ] **Step 2: Checklist d'exit (roadmap Phase 1)** — chaque item avec sa preuve citée :
1. **Deux scènes iPadOS exécutent deux applications distinctes** — `testTwoScenesRunIndependentSessions` vert ; déconnecter l'une ne modifie pas l'autre (`testSceneDisconnectionTerminatesOnlyItsSession`).
2. **Le host mono-scène tvOS passe les transitions et le teardown sans résurrection** — `testLifecycleTransitionsFollowTheScene` + `testStopThenReattachOnTheSameScene` verts sur tvOS.
3. **Aucun objet host-owned détruit** — `requestOpenedClose`/peer owner ne touchent pas UIKit (revue du code) + le test disconnect garde les références Swift utilisables.
4. **Les garanties structurelles initiales + contrats activés avec preuves** — BCK-013 + WIN-009 `active`, JSON/JUnit corrélés par target, sentinelles mutées.
5. **Cinq managers avant Running** — lecture de la factory (revue) + `UnsupportedDisplayManager` etc. avec snapshots initiaux.

- [ ] **Step 3: Rapport de sweep** — commandes + sorties + verdicts, dans le rapport de tâche SDD.

- [ ] **Step 4: Push + PR** (contrôleur).

---

## Séquencement et estimation

Tasks 1→2→3→4→5→6→7→8, séquentielles (chaque task consomme les interfaces de la précédente). Tasks 1–2 = la portabilisation (risque : la restructuration du wait, Review Focus 1) ; Tasks 3–4 = l'adaptateur (risque : les noms exportés et le ladder d'attach) ; Task 5 = la preuve (risque : la mécanique multi-scène en test unitaire) ; Tasks 6–7 = preuves et closure. Budget attendu : comparable à la web phase 4 (~150M tokens de sous-agents).
