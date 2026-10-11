# Task 8 — Sweep du critère de sortie (Phase 2 iOS/tvOS surface, redraw, accès natif)

Date : 2026-10-11 · HEAD : `87c4f6b5` (« docs: close the phase 2 UIKit surface perimeter ») ·
Worktree : `/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f` (branche `zcode/sess-56645e3f`)

**Verdict global : le critère de sortie Phase 2 (roadmap l.214) est TENU — les cinq points sont prouvés,
toutes les pattes de test passent à froid sur `87c4f6b5`. Aucun défaut trouvé, aucun commit (sweep
verification-only).**

---

## 1. Sweep à froid — commandes et sorties décisives

### 1.0 `git status`
```
On branch zcode/sess-56645e3f
nothing to commit, working tree clean
```
HEAD vérifié : `87c4f6b5`. (La branche a divergé d'`origin/zcode/sess-56645e3f` — 12 locaux / 20
distants ; le push est l'affaire du contrôleur, brief Step 3 « push (contrôleur) ».)

### 1.1 `:kadre:runtime:jvmTest`
```
> Task :kadre:runtime:jvmTest
BUILD SUCCESSFUL in 39s
17 actionable tasks: 7 executed, 10 up-to-date
```
La tâche `jvmTest` a réellement exécuté (pas UP-TO-DATE). **PASS.**

### 1.2 `:kadre:runtime:iosSimulatorArm64Test` + `:kadre:runtime:tvosSimulatorArm64Test`
Première invocation : les deux tâches sont revenues **UP-TO-DATE** (aucune exécution) — inacceptable
pour un sweep à froid. Ré-invoqué avec `--rerun` :
```
> Task :kadre:runtime:iosSimulatorArm64Test
> Task :kadre:runtime:tvosSimulatorArm64Test
BUILD SUCCESSFUL in 6s
```
Preuve d'exécution réelle — XML régénérés à l'instant (timestamp `2026-10-11T19:58Z`), totalisé sur
`kadre/runtime/build/test-results/*/` :
```
iosSimulatorArm64Test : suites=13 tests=83 skipped=0 failures=0 errors=0
tvosSimulatorArm64Test: suites=13 tests=83 skipped=0 failures=0 errors=0
```
**PASS (83 + 83 tests, 0 échecs).**

### 1.3 `:kadre:backend:appkit:appKitNativeTests` (`--rerun`)
```
> Task :kadre:backend:appkit:appKitNativeTests
BUILD SUCCESSFUL in 47s
```
`appKitNativeTests` est une tâche agrégée (`dependsOn(jvmTest, appKitStandaloneLoopTest)`,
`kadre/backend/appkit/build.gradle.kts` l.83-87). XML frais du jour (`2026-10-11T19:58-19:59Z`) :
```
appkit/jvmTest               : suites=25 tests=371 skipped=0 failures=0 errors=0
appkit/appKitStandaloneLoop  : suites=2  tests=2   skipped=0 failures=0 errors=0
```
**PASS (373 tests, 0 échecs, boucle standalone process-owning comprise).**

### 1.4 `./scripts/test-uikit-simulator.sh all`
```
OK: kadre/contracts/driver/uikit/build/contract-evidence/iosSimulatorArm64/contract-evidence/BCK-012.json
OK: .../iosSimulatorArm64/contract-evidence/BCK-013.json
OK: .../iosSimulatorArm64/contract-evidence/BCK-014.json
OK: .../iosSimulatorArm64/contract-evidence/BCK-015.json
OK: .../iosSimulatorArm64/contract-evidence/BCK-016.json
OK: .../iosSimulatorArm64/contract-evidence/WIN-009.json
OK: .../tvosSimulatorArm64/contract-evidence/BCK-012.json
OK: .../tvosSimulatorArm64/contract-evidence/BCK-013.json
OK: .../tvosSimulatorArm64/contract-evidence/BCK-015.json
OK: .../tvosSimulatorArm64/contract-evidence/WIN-009.json
uikit gate (all): SUCCESS
```
Vérification d'exécution réelle (la ligne SUCCESS seule ne prouve rien) :
- les tâches `generateUikit…ContractEvidence` sont des Exec sans sorties déclarées (commentaire du
  script l.62-64) → re-jouées à chaque invocation ; les JUnit Swift exportés sont frais
  (`Oct 11 22:00`) ;
- les JSON de preuve portent `"commit": "87c4f6b54beed45bf41adbd2dbb5a88cebd801f9"` = HEAD exact ;
- suites XCTest par famille (`kadre/contracts/driver/uikit/build/contract-evidence/*/test-results/`) :
  ```
  iosSimulatorArm64 : KadreUikitSurfaceTests tests=9  · KadreUikitAttachTests tests=10 · KadreUikitDriverTests tests=2   → 21 tests, 0 failures
  tvosSimulatorArm64: KadreUikitSurfaceTests tests=8  · KadreUikitAttachTests tests=7  · KadreUikitDriverTests tests=2   → 17 tests, 0 failures
  ```
- `BCK-015.json` (les deux familles) : 8 scénarios `Passed` (lease-boundaries, apply-fields-refused,
  metrics-match-view, redraw-after-close, redraw-coalesced, reprise-rereads, resize-propagates,
  theme-propagates), 3 sentinelles `Killed` (uikit-lease-closed-after-terminal,
  uikit-metrics-stale-generation, uikit-redraw-single-per-tick) ; `BCK-016.json` (iOS seul) :
  `uikit-surface-rotation-propagates` Passed + sentinelle Killed ;
- à l'intérieur du gate, les tâches runtime `ios/tvosSimulatorArm64Test` étaient UP-TO-DATE — normal,
  elles venaient d'être forcées en 1.2.

**PASS (gate complet, deux familles, preuves liées à `87c4f6b5`).**

### 1.5 Complément : `:kadre:platform:uikit:{ios,tvos}SimulatorArm64Test --rerun`
Les tests K/N du module uikit (redraw scheduler, apply ladder, lease registry, observation, lifecycle)
ne sont PAS dans le gate `all` ; leurs XML dataient de 16:34 (ère Task 5/6) — périmés par rapport à
HEAD. Forcés :
```
> Task :kadre:platform:uikit:iosSimulatorArm64Test
> Task :kadre:platform:uikit:tvosSimulatorArm64Test
BUILD SUCCESSFUL in 21s
iosSimulatorArm64Test : suites=5 tests=21 skipped=0 failures=0 errors=0   (Oct 11 22:02:49)
tvosSimulatorArm64Test: suites=5 tests=21 skipped=0 failures=0 errors=0   (Oct 11 22:03:01)
```
**PASS (21 + 21 tests sur HEAD, 0 échecs).**

### Bilan chiffré du sweep
| Patte | Tests | Échecs |
|---|---|---|
| runtime jvmTest | (BUILD SUCCESSFUL, exécuté) | 0 |
| runtime iOS simulateur | 83 | 0 |
| runtime tvOS simulateur | 83 | 0 |
| appkit natif (jvmTest + standalone loop) | 373 | 0 |
| XCTest Swift iOS (xcodebuild, iPad Pro 11" OS 27.0) | 21 | 0 |
| XCTest Swift tvOS (xcodebuild, Apple TV 4K OS 27.0) | 17 | 0 |
| module uikit iOS + tvOS | 21 + 21 | 0 |

---

## 2. Checklist d'exit (roadmap IOS-IMPLEMENTATION-ROADMAP.md, Phase 2 « Critère de sortie », l.214)

> « la vue retournée est celle fournie à l'attach ; state avant event pour chaque changement ;
> coordonnées d'input prêtes à utiliser les mêmes métriques ; aucune requête redraw ni invocation
> native après révocation. Les deux opt-ins sont vérifiés par consumers positifs et négatifs. »

### (1) La vue retournée est celle fournie à l'attach — **TENU**
- **Preuve** : `uikit-lease-boundaries` — `kadre/contracts/driver/uikit/tests/Shared/KadreUikitSurfaceTests.swift`
  l.600-621, exécuté sur les deux familles (Passed dans BCK-015.json iOS et tvOS, commit `87c4f6b5`) :
  ```swift
  XCTAssertTrue(probe.grantedViewMatches, "the leased view must isEqual the host surfaceView")
  ```
- La comparaison `isEqual` (égalité Objective-C, pas identité Kotlin) est faite DANS la callback de
  bail : `KadreUikitSurfaceDriver.leaseProbe` —
  `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/KadreUikitSurfaceDriver.kt`
  l.167 (`viewMatches = view.isEqual(expectedView)`).
- La vue remise est relue hors de la section critique d'admission depuis l'entry vivante du registre
  du port (`UIKitWindowCommandPort.lease`, `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitWindowCommandPort.kt`
  l.479-486 : relecture `!closed && bySurface.isNotEmpty()` avant toute invocation du block) — la
  UIView du host elle-même, jamais une copie ni une vue d'une surface morte.

### (2) State avant event pour chaque changement — **TENU** (publication runtime-owned)
- **Preuve** : le drain de publication du kernel runtime — `RuntimeWindowSurface.drainPublications`,
  `kadre/runtime/src/commonMain/kotlin/org/graphiks/kadre/internal/runtime/MinimalWindowSurface.kt`
  l.724-740 :
  ```kotlin
  publication.capabilities?.let { mutableCapabilities.value = it }   // l.732
  publication.state?.let(::publishStateAtLeast)                       // l.733 — le STATE d'abord
  publication.event?.let(::publishEvent)                              // l.734 — l'ÉVÉNEMENT ensuite
  ```
- Chaque fabrication de publication mute d'abord `currentState` (révision incrémentée) puis embarque
  le snapshot : `metricsPublicationLocked` l.630-648, `focusPublicationLocked` l.650-657,
  `visibilityPublicationLocked` l.659-673, `appearancePublicationLocked` l.675-682,
  `redrawPublicationLocked` l.684-691. Le chemin immédiat (`finishPublicationAdmission` l.825-833)
  respecte le même ordre (capabilities → state → drain).
- `publishStateAtLeast` l.858-864 : publication monotone en révision (jamais de retour arrière).
- Côté consumer, le contrat est reconnu et testé : le commentaire du test resize
  (`KadreUikitSurfaceTests.swift` l.336-339) — « Le STATE est publié sur le main (StateFlow direct) ;
  l'ÉVÉNEMENT voyage par la file des lanes … peut atterrir quelques battements APRÈS l'état » — et le
  test asserte `metricsCount >= 1` sur le canal d'événements (l.340-343). Passé sur les deux familles
  (`uikit-surface-resize-propagates`, theme, reprise : Passed dans BCK-015).

### (3) Coordonnées d'input prêtes à utiliser les mêmes métriques — **TENU**
- **Preuve** : `uikit-surface-metrics-match-view` — `KadreUikitSurfaceTests.swift` l.293-313, Passed
  sur les deux familles (BCK-015.json) :
  ```swift
  XCTAssertEqual(state.logicalSize.width,  Double(view.bounds.width),  accuracy: 0.001)
  XCTAssertEqual(state.logicalSize.height, Double(view.bounds.height), accuracy: 0.001)
  XCTAssertEqual(state.scaleFactor, Double(view.traitCollection.displayScale), accuracy: 0.001)
  XCTAssertEqual(state.safeAreaInsets.top/left/bottom/right, Double(view.safeAreaInsets…), accuracy: 0.001)
  assertPhysicalDerivesFromLogical(state)   // physical == logical × scale (ceil)
  ```
  Doc du test l.290-292 : « logical == bounds, physical == logical × scale (ceil), scale ==
  displayScale, safeArea == insets Swift » — le consommateur d'input lit la même métrique que la vue.
- Le suivi vivant est prouvé par `uikit-surface-resize-propagates` (l.320-348 : resize RÉEL
  `setFrame + layoutIfNeeded`, state == nouvelles bounds, MetricsChanged publié par différence) et
  `uikit-surface-rotation-propagates` (BCK-016, iOS) ; sentinelles `uikit-metrics-stale-generation`
  et `uikit-rotation-metrics-faithful` : Killed.

### (4) Aucune requête redraw ni invocation native après révocation — **TENU**
Trois jambes indépendantes, toutes prouvées à HEAD :
- **Redraw après close** — `uikit-surface-redraw-after-close` (`KadreUikitSurfaceTests.swift` l.500-530,
  Passed deux familles) : après `stopAndAwaitTermination`, `state.attachment === .detached`
  (l.514-516), `surface.requestRedraw()` échoue `KadreFailure.Closed(KadreResourceKind.surface)`
  (l.518-525), et fenêtre de quiescence : « no redraw event may follow the close » (l.527-528).
- **Bail natif après terminaison** — sentinelle `uikit-lease-closed-after-terminal` **Killed** (deux
  familles, BCK-015.json) : le verdict steady post-close est `Unsupported(PlatformSurfaceAccess)`
  (test l.617-620 ; sonde `closedLeaseProbe`, `KadreUikitSurfaceDriver.kt` l.202-210), la route du
  registre étant retirée au close (`UIKitWindowRuntime.close`, `UIKitWindowRuntime.kt` l.147-156 :
  `UIKitViewLeaseRegistry.shared.unregister(leaseSurfaceId)`) et la façade répondant sans port
  (`UIKitSurfaceAccess.kt` l.44-45) sans jamais invoquer le block. `Closed(Surface)` reste l'issue de
  course en vol uniquement (relecture de vue après teardown : `UIKitWindowCommandPort.lease` l.483-486,
  block non invoqué) — documenté verbatim dans le KDoc `UIKitSurfaceAccess.kt` l.22-23/32-34.
- **Drain annulé au beginClose** — `UIKitWindowCommandPort.beginClose`
  (`UIKitWindowCommandPort.kt` l.571-575) :
  ```kotlin
  fun beginClose() {
      redrawScheduler.cancel()            // AVANT le verrou — aucun drain après fermeture
      teardownSurfaceObservation()        // Task 3 : zéro observer résiduel
      lock.withLock { closed = true }     // refuse toute nouvelle admission
  }
  ```
  appelé en tête du close (`UIKitWindowRuntime.kt` l.148). Preuve unitaire du cancel :
  `UIKitRedrawSchedulerTest.cancelStopsEverythingForever`
  (`kadre/platform/uikit/src/uikitTest/kotlin/org/graphiks/kadre/internal/uikit/UIKitRedrawSchedulerTest.kt`
  l.55-60) — re-passé sur les deux familles en 1.5. Côté kernel runtime, `terminaliseLocked`
  (`MinimalWindowSurface.kt` l.791-814) annule `redrawTicket`, publie l'état détaché terminal avec
  capabilities unsupported, et `requestRedraw`/`apply` refusent `Detached` avec `Closed(Surface)`
  (l.217-234, l.262-266) ; `uikit-surface-redraw-coalesced` (burst de 5 → 1 tick, sentinelle
  `uikit-redraw-single-per-tick` Killed) prouve que le drain vivant n'émet qu'un tick par génération.

### (5) Les deux opt-ins vérifiés par consumers positifs et négatifs — **TENU**
L'opt-in natif est le double `@KadrePlatformApi @DelicateKadreApi` sur `withUIKitView`
(`UIKitSurfaceAccess.kt` l.41-43 ; KDoc l.36-39 : « Le double opt-in est le contrat de la frontière »).
- **Positif** : la capability est déclarée `Supported` — `uikitSurfaceCapabilities()`
  (`UIKitWindowCommandPort.kt` l.667-670 : `platformAccess = Capability.Supported(Unit,
  FeatureAvailability.Available)`), et le consumer nominal est prouvé : premier bail ADMIS avec la
  vue `isEqual` (`probe.granted` + `grantedViewMatches`, test l.606-607) sur les deux familles.
- **Négatifs** (tous trois prouvés, même test l.608-620 + port) :
  - `TemporarilyUnavailable(retryable = true)` — second bail pendant le vol du premier, tenté DANS la
    callback, block jamais invoqué (test l.608-612 ; garde `leaseHeld` au port l.472-474) ;
  - `Unsupported(PlatformSurfaceAccess)` — bail post-close steady, route du registre retirée
    (test l.617-620 ; façade l.44-45 ; unregister au close `UIKitWindowRuntime.kt` l.149) ;
  - `Closed(Surface)` — course en vol d'un bail admis dont la relecture suit le teardown
    (`UIKitWindowCommandPort.lease` l.469-470 et l.483-486, block non invoqué).
- Consumers Kotlin supplémentaires du registre : `UIKitViewLeaseRegistryTest`
  (`registeredPortIsResolvableAndUnregisterRemovesIt`, `separateRegistryInstancesAreIsolated`) —
  re-passés à HEAD sur les deux familles (1.5).
- Activation registre : BCK-015 `active` (`kadre/contracts/registry/contracts.tsv` l.73) avec les
  preuves both-families générées à 22:00 liées à `87c4f6b5` ; BCK-016 `active` (l.74, iOS).

---

## 3. Auto-revue du sweep

1. **Piège UP-TO-DATE (corrigé)** : la première passe Gradle des tests natifs runtime est revenue
   UP-TO-DATE (aucune exécution) et les tests K/N du module uikit dataient de 16:34 (avant les 3
   derniers commits). Un sweep à froid exigeait `--rerun` partout ; chaque patte a ensuite été
   validée par horodatage des XML (19:58-22:03 du jour) et, pour la gate, par le champ `commit`
   des JSON de preuve (`87c4f6b5…`) — pas par la seule ligne « SUCCESS ».
2. **Couverture du gate** : `./scripts/test-uikit-simulator.sh all` n'inclut PAS les tests K/N du
   module `:kadre:platform:uikit` (il couvre xcodebuild validator + tests runtime + liens des slices
   appareil). Ils ont été ajoutés au sweep (1.5) et passent ; à considérer pour un futur gate CI
   (observation, pas un défaut de la phase 2).
3. **Divergence branche** : 12 commits locaux vs 20 distants sur `origin/zcode/sess-56645e3f` — le
   push/appartenance est la décision du contrôleur (brief Step 3), non traitée ici.
4. **Aucun défaut trouvé** : aucun fix nécessaire, donc **aucun commit** (sweep verification-only,
   conforme à la consigne). Arbre propre en fin de sweep (`git status --short` vide).
5. Les trois reports mineurs hérités de Task 5 (progress.md l.55) restent inchangés : rotation — 2e
   stimulus sans second MetricsChanged drainé (state==live garde le kill power) ; thème tvOS ambiant
   Dark (transition →Light seule publication-prouvée ce run) ; workaround IntArray dans la sonde
   opt-in. Aucun ne touche un point du critère de sortie.

## 4. Statut
- Sweep : **DONE** — critère de sortie Phase 2 tenu sur `87c4f6b5`, toutes pattes vertes à froid.
- Commits : **aucun** (aucun défaut).

---

## 5. Fixes de la revue finale (post-sweep)

Deux corrections de documentation issues de la revue finale, commit
`3ff90b69` (« docs(uikit): correct the kotlin-only claim and qualify the observation model ») —
aucun changement de code.

1. **F1 — fausse affirmation dans le KDoc de `withUIKitView`**
   (`kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/platform/uikit/UIKitSurfaceAccess.kt`
   l.38-39 sur `87c4f6b5`). Le KDoc affirmait « Kotlin-only (INTEROP §7 : aucun pointer n'apparaît
   dans les exports Swift) » — or l'en-tête du framework livré exporte bien `withUIKitView` (et la
   sonde `leaseProbe` du driver) porteurs de `UIView*`. Reformulé : la règle Kotlin-only d'INTEROP
   §7 est le contrat de la façade phase 3 (`KadreHost`), pas celui de l'en-tête framework livré ;
   l'export `UIView*` actuel est une divergence connue et enregistrée, que l'audit façade phase 3
   résout (supprimer l'export Obj-C ou amender INTEROP §7 d'une exception en-tête framework).
2. **F2 — surstatement dans `kadre/capabilities/uikit.md` l.155-156.** « les métriques publiées
   valent en permanence la lecture VIVANTE de la vue » surestimate le modèle par déclencheurs
   (BACKEND-CAPABILITIES.md l.239 : un changement entre deux déclencheurs est observé au
   déclencheur suivant). Qualifié en : « à chaque déclencheur SDK, les métriques publiées valent
   la lecture VIVANTE de la vue ; un changement entre deux déclencheurs est observé au
   déclencheur suivant ».

Vérification : `git diff 87c4f6b5..3ff90b69` ne montre que ces deux fichiers, lignes de doc
uniquement (le changement `.kt` est un commentaire KDoc, zéro code exécutable).
