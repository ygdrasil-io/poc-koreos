# Task 8 — Sweep du critère de sortie (jalon A)

**Statut : DONE** · Commit : **none** — sweep verification-only, aucun défaut détecté, aucun commit.
**Branche :** `zcode/sess-56645e3f` · HEAD `b0c55c77` (état des reviews T1–T7 exactement celui annoncé) · arbre de travail PROPRE au départ et à l'arrivée.
**Worktree :** `/Users/chaos/.zcode/worktrees/poc-koreos/sess-56645e3f`
**Logs bruts :** `.superpowers/sdd/2026-10-11-ios-tvos-phase-3-swift-facade/sweep-logs/` (`job-a-runtime.log`, `job-b-uikit-gate.log`, `job-c-consumers.log`, `job-d-gated-swiftui.log`).

---

## 1. Le sweep à froid (Step 1)

git status initial : `nothing to commit, working tree clean`. Divergence origin constatée (9 commits locaux, 13 distants — merge Android phase 3 côté origin) : le push est l'étape du CONTRÔLEUR (plan l.233), ce sweep n'a rien poussé.

### Commande 1 — `./gradlew :kadre:runtime:jvmTest --rerun-tasks` (background, 04:36)

```
BUILD SUCCESSFUL in 16s
```

Fraîcheur vérifiée (anti-piège UP-TO-DATE) : `--rerun-tasks` force la ré-exécution ; XML re-écrits à `04:36:23` (heure du sweep). Comptage JUnit XML (`kadre/runtime/build/test-results/jvmTest/`) : **408 tests, 0 failures, 0 skipped**.

### Commande 2 — `./gradlew :kadre:runtime:iosSimulatorArm64Test :kadre:runtime:tvosSimulatorArm64Test` (background, 04:36)

```
BUILD SUCCESSFUL in 28s
> Task :kadre:runtime:iosSimulatorArm64Test      (exécutée — aucun marqueur UP-TO-DATE)
> Task :kadre:runtime:tvosSimulatorArm64Test     (exécutée — aucun marqueur UP-TO-DATE)
```

XML : `iosSimulatorArm64Test` **83 tests, 0 failures** (mtime 04:36:36–37) ; `tvosSimulatorArm64Test` **83 tests, 0 failures** (mtime 04:36:52).

### Commande 3 — `./gradlew :kadre:backend:appkit:appKitNativeTests` (background, 04:37)

```
BUILD SUCCESSFUL in 34s
> Task :kadre:backend:appkit:appKitStandaloneLoopTest   (exécutée)
> Task :kadre:backend:appkit:jvmTest                    (exécutée)
> Task :kadre:backend:appkit:appKitNativeTests          (exécutée)
```

XML : appkit `jvmTest` **371 tests, 0 failures** (04:37:26) + `appKitStandaloneLoopTest` **2 tests, 0 failures** (04:37:02).

### Commande 4 — `./scripts/test-uikit-simulator.sh all` (background, 04:38–04:40)

```
OK: destination platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0
OK: destination platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0
	 Executed 21 tests, with 0 failures (0 unexpected) in 19.176 (19.190) seconds   [iOS : 2 Driver + 10 Attach + 9 Surface]
	 Executed 17 tests, with 0 failures (0 unexpected) in 4.642 (4.656) seconds     [tvOS : 2 Driver + 7 Attach + 8 Surface]
** TEST SUCCEEDED **          (×2 — une par famille)
uikit gate (all): SUCCESS
EXIT=0
```

Preuves de contrats : **10/10 fichiers `contract-evidence/*.json` validés OK** (iOS : BCK-012…016, WIN-009 ; tvOS : BCK-012/013/015, WIN-009).

### Commande 5 — publication + agrégateur consumer (background, 04:42)

```
### ./gradlew publishContractArtifacts :kadre:platform:uikit:publishAllPublicationsToContractTestRepository
BUILD SUCCESSFUL in 3s

### ./gradlew -p kadre/consumers/swift -PkadreRepository=$PWD/build/kadre-contract-repository -PkadreVersion=1.0.0 check
BUILD SUCCESSFUL in 15s
```

Le `check` de l'agrégateur couvre : repository check ✓, assemblage xcframework ✓, link des deux frameworks statiques consumer ✓, xcodegen ✓, `iosSimulatorTests` ✓, `tvosSimulatorTests` ✓ + gardes `doLast` (TEST SUCCEEDED + zéro duplication).

Logs xcodebuild (`kadre/consumers/swift/build/logs/`, mtime 04:42:52/04:42:59 — runs réels de CE sweep) :

```
ios  : Executed 46 tests, with 1 test skipped and 0 failures   (skip = test SwiftUI gated, cf. commande 6)
tvos : Executed 46 tests, with 1 test skipped and 0 failures
ios  'is implemented in both': 0
tvos 'is implemented in both': 0
```

Détail par suite (identique ×2 familles) : KadreHostMappingTests **29** · KadreHostViewControllerTests **7** · KadreSessionHandleTests **6** · KadreSwiftUIConsumerTests **3 dont 1 skip** · ConsumerAttachTests **1** = 46.

Fraîcheur xcframework : la slice binaire portée à `04:21:32` (gate Task 7) est RESTÉE UP-TO-DATE dans ce run — arbre propre sur `b0c55c77`, inputs inchangés : fraîcheur correcte, pas de stalence ; l'app a lié exactement le binaire de HEAD.

### Commande 6 — run gated SwiftUI (les 2 destinations, 04:43)

Le test de lancement SwiftUI `testLaunchGatedSwiftUISceneHostsTheWrapperAndAttachesOnce` est skippé hors gate (garde d'environnement documentée, jamais une assertion affaiblie) — re-run dédié pour que le sweep porte AUSSI ce parcours :

```
TEST_RUNNER_KADRE_CONSUMER_SWIFTUI=1 TEST_RUNNER_KADRE_CONSUMER_SWIFTUI_FACTORY=held \
  xcodebuild … -only-testing:…/KadreSwiftUIConsumerTests/testLaunchGatedSwiftUISceneHostsTheWrapperAndAttachesOnce test
iOS  : Executed 1 test, with 0 failures (0 unexpected)   ** TEST SUCCEEDED **
tvOS : Executed 1 test, with 0 failures (0 unexpected)   ** TEST SUCCEEDED **
```

### Totaux du sweep

| Suite | Résultat |
|---|---|
| runtime jvmTest (`--rerun-tasks`) | 408 / 0 / 0 |
| runtime natives (iOS + tvOS sims) | 83 + 83 / 0 / 0 |
| appkit (jvmTest + standaloneLoop) | 371 + 2 / 0 / 0 |
| gate uikit `all` (driver ×2 familles) | 21 + 17 / 0 · 10/10 evidence · 2× TEST SUCCEEDED |
| consumer XCTest (×2 familles) | 46 + 46 exécutés (1 skip gated/famille) · 0 failures · 0 duplication |
| consumer gated SwiftUI (×2 familles) | 1 + 1 / 0 · 2× TEST SUCCEEDED |

**Zéro rouge, zéro régression, zéro duplication détectée.**

---

## 2. Checklist jalon A (Step 2) — les cinq verdicts

### (1) Des hosts UIKit et SwiftUI externes, iOS et tvOS, attachent/observent/arrêtent/attendent depuis LEURS propres artifacts — **PASS**

**Les artifacts du consumer** (liés une seule fois, dans l'app, jamais dans le bundle de test) :
- `kadre/consumers/swift/consumer-app/project.yml` l.21 / l.69 : les sources `KadreHost` du dépôt compilées DANS chaque app (iOS + tvOS) ;
- l.43–52 / l.72–82 : le framework statique du module applicatif Kotlin du consumer (`consumer-kmp/…/KadreUikit.framework`, `embed: false`) + l'archive de distribution `KadreUikit.xcframework` passée au lieur par `OTHER_LDFLAGS` ;
- l.100–113 : les bundles de test portent AUCUN framework Kotlin (`BUNDLE_LOADER` — les symboles viennent de l'app hôte).

**Les quatre parcours, par famille (iOS ET tvOS — `tests/Shared` tournent dans les deux schemes ; exécution prouvée par « Executed 46 tests » ×2 familles) :**
- **Attach** : `ConsumerAttachTests.testConsumerFactoryAttachesRunsAndStopsItself` (attach de lancement via la factory Kotlin du consumer) ; `KadreSessionHandleTests.attachHandle` → `KadreIosHost.attach` ; controller-driven : `KadreHostViewControllerTests.testAttachesExactlyOnceWhenTheViewJoinsAWindowWithAScene` ; wrapper SwiftUI : `KadreSwiftUIConsumerTests.testSwiftUIConsumerAttachesExactlyOnceAcrossBodyReevaluations` + le parcours de LANCEMENT gated (`job-d`, les 2 familles).
- **Observe** : `testAttachEmitsCurrentSnapshotImmediatelyThenTerminatedHostRequested` (émission immédiate du courant), `testObserveStateDeliversDistinctSnapshotsInOrderOnMainThread` (ordre + main thread + zéro doublon), `testObservationCloseTwiceIsIdempotentAndStopsDelivery`, `KadreSwiftUIConsumerTests.testObserveStateInscribedAfterTerminationDeliversTerminatedOnceThenSilence`.
- **Stop** : `requestStop` → `.terminated(.stopped(.hostRequested))` (KadreSessionHandleTests 1, 3, 6 ; `testExplicitCloseTerminatesAndReAddDoesNotReattach` ; requestStop VIA LE CONTROLLER dans les 2 tests SwiftUI) ; arrêt par l'APPLICATION : `ConsumerAttachTests` (self-stop ~500 ms → `ApplicationRequested`).
- **awaitTermination** : retour direct sur session terminée (test 1), sous cancellation (test 2), en course (test 6) ; `ConsumerAttachTests` : `terminationErrorDescription == nil`, outcome `Stopped`, raison `ApplicationRequested`.

Le host UIKit est l'app elle-même (`ConsumerSceneDelegate`/`ConsumerSceneStore`, attach sur la scène primaire) ; le host SwiftUI est le wrapper `UIViewControllerRepresentable` de l'app (`ConsumerSwiftUIScene`, gate `KADRE_CONSUMER_SWIFTUI`, prouvé en run dédié ×2 familles). Aucun des deux ne partage un artifact avec le dépôt au-delà de ce que le plan promet (xcframework + sources façade + leur factory).

### (2) La course cancellation/terminaison ne reprend JAMAIS deux fois — **PASS**

- **Mécanisme** : `kadre/platform/uikit/swift/Sources/KadreHost/KadreSessionHandle.swift` l.187–224 — `KadreTerminationWaiter.settle` : le PREMIER arrivé prélève le slot sous `NSLock`, les suivants no-op ; `arm` (l.195–205) couvre la cancellation survenue AVANT l'armement.
- **Preuve** : `KadreSessionHandleTests.testTerminationAndCancellationResumeExactlyOnceFiftyIterationRaceLoop` (consumer, l.328–369) — boucle de **50 itérations**, graine `srand48(iteration + 1)` (déterministe), `requestStop` + `waiter.cancel()` concurrents ; l'assertion `outcomeWins + cancellationWins == 50` (l.363) ; une double reprise de `CheckedContinuation` TRAPPERAIT le process, une reprise manquée pendrait — 50 verts = l'invariant exactement-une-fois tient.
- **Exécution de CE sweep** : passed dans les DEUX familles (iOS 0.637 s, tvOS 0.675 s) ; distribution imprimée : `uikit-facade race loop: outcome-won=0 cancellation-won=50` (×2). Le 0/50 est dans le contrat DU TEST (l.365–366 : « on n'asserte PAS que les deux branches se réalisent ») ; le chemin outcome est prouvé hors course par le test 1 (retour direct) et par le post-boucle `terminationOutcomeWithin == .hostRequested` exigé à CHAQUE itération (l.358–360), y compris dans les 50 itérations où la cancellation a gagné — la session a survécu à la cancellation dans les 50 cas.

### (3) Annuler une Task n'arrête PAS la session — **PASS**

- **Mécanisme** : `KadreSessionHandle.awaitTermination` (l.141–168) — `withTaskCancellationHandler` : le `onCancel` ne règle QUE le waiter (`CancellationError`) ; l'enregistrement Kotlin n'est pas démonté et retombera, à la terminaison, sur un slot déjà réglé (no-op).
- **Preuve** : `KadreSessionHandleTests.testAwaitTerminationCancellationKeepsSessionAliveToApplicationRequested` (l.188–212) — `waiter.cancel()` → `CancellationError` PUIS l'outcome lu est `.stopped(.applicationRequested)` (l.206) et `handle.state == .terminated(.stopped(.applicationRequested))` (l.210) : c'est l'APPLICATION consumer qui a arrêté la session (aucun `requestStop` dans le test ; un arrêt provoqué par la cancellation aurait dit `HostRequested`). Couru dans les 46 tests de CHACUNE des deux familles.

### (4) Aucun cycle de rétention controller/session/observer après fermeture — **PASS**

- **deinit + weak tracking** : `KadreHostViewControllerTests.testDeinitClosesTheSession` (l.257–277) — `weak var weakController`, la dernière référence forte meurt au sortir du scope, `waitUntil(5s) { weakController == nil }` prouve la DÉSALLOCATION du controller, et le handle capturé AVANT atteint `terminated` : le deinit a fermé la session. Couru ×2 familles.
- **Chemins de fermeture** tous prouvés : close explicite idempotent (`testExplicitCloseTerminatesAndReAddDoesNotReattach`, double `close()` → `.hostRequested`, re-add muet), `sceneDidDisconnect` (`testSceneDidDisconnectNotificationClosesTheSession`), deinit (ci-dessus), filet `settle`/`tearDown`.
- **Aucun côté ne retient** : la view ne retient JAMAIS son controller (`onMovedToWindow` capture `[weak self]`, `KadreHostViewController.swift` l.105) ; les blocs NotificationCenter capturent `[weak self]` (l.166, l.205) pour ne pas repousser le deinit ; `KadreSessionHandle.close()` (l.115–129) résilie TOUS les watchers vivants ; l'observation est idempotente et muette après close (tests 4 et 5 de KadreSessionHandleTests — « a removed observer must never be called again », « pre-close observations must be torn down »).
- Instruments non disponible dans cet environnement — le brief le prévoit : « sinon weak/strong assertions », ce sont elles qui font foi (weak tracking de désallocation + terminaison après close, ×2 familles).

### (5) Absence de duplication + identité de types par la factory cross-modules — **PASS**

- **Garde zéro-duplication** : `kadre/consumers/swift/build.gradle.kts` (`consumerTestTask.doLast`) — `check(log.contains("TEST SUCCEEDED"))` ET `check(!log.contains("is implemented in both"))` PAR famille ; l'app s'est LANCÉE avec les deux archives statiques liées (ld à sémantique d'archive paresseuse ne refuse pas un doublon — c'est le runtime qui le logge/crash). Re-vérifié au sweep : grep = **0** dans les deux logs.
- **Identité de types** : `kadre/consumers/swift/consumer-kmp/build.gradle.kts` — `baseName = "KadreUikit"` (préfixe Obj-C IDENTIQUE à la distribution, dérivé du nom de framework — KGP 2.4.20 sans override) + `export(uikit)` + `export(foundation)` pour que les classes partagées portent les MÊMES noms plats dans les deux archives ; `ConsumerApplicationFactory.kt` (module applicatif KOTLIN du consumer) implémente `KadreApplicationFactory` ; Swift l'instancie et le passe à `KadreIosHost.attach(applicationFactory: any KadreUikit.KadreApplicationFactory)` (`KadreIosHost.swift` l.35–39) — le compilateur n'accepte le passage que si les deux fragments exposent le même type Obj-C. La preuve d'exécution : `ConsumerAttachTests` asserte `store.factoryClassName?.hasSuffix("ConsumerApplicationFactory") == true` et la session portée par CETTE factory traverse Running → self-stop → Terminated, ×2 familles.

---

## 3. Auto-revue du sweep

1. **Distribution de la course 0/50** (`outcome-won=0 cancellation-won=50`, ×2 familles) : conforme au contrat du test (les deux branches ne sont PAS exigées), mais signalé pour l'historique : dans cet environnement le délai pré-cancellation (0–20 ms) perd systématiquement contre la latence de dispatch Kotlin. L'invariant exactement-une-fois tient sur les 50 ; le chemin outcome reste prouvé hors course (test 1 + post-boucle par itération). Aucune action.
2. **xcframework UP-TO-DATE (mtime 04:21)** : le binaire lié par le consumer n'a pas été ré-assemblé dans le run C — inputs inchangés depuis HEAD `b0c55c77` (arbre propre), la fraîcheur est correcte ; noté pour ne pas le lire comme un test sur un artefact ancien.
3. **Warn Gradle** « Missing 'dependsOn' in Source Sets » sur `uikitTest` (préexistant, lecture seule de configuration) : bruit connu, aucune incidence sur les résultats — inchangé depuis les tasks précédentes.
4. **Divergence origin** (9 locaux / 13 distants, origin à `eb4d9c30`) : le sweep n'a rien poussé ni mergé — push = étape du contrôleur (plan l.233).
5. **Concern hérité Task 3** (`sourceLost` non pilotable de bout en bout depuis Swift — `CaptureSourceId` sans initialisateur Kotlin ; dérivation prouvée par la couture `kadreOpaqueSourceId`) : inchangé, reporté à la phase capture (phase 9), précédent web identique. Rien à retirer ici.
6. **Portée du sweep** : les tests facade Swift n'existent que dans le consumer (le dépôt ne contient pas de target de test Swift pour KadreHost — `kadre/platform/uikit/swift/` n'a que `Sources/`) ; c'est donc le consumer qui porte TOUTE la preuve façade, par conception du plan (T2). Le gate uikit (driver Kotlin) prouve le contrat côté Kotlin — les deux moitiés sont vertes.

---

## 4. Verdict du sweep

**Jalon A est ATTEINT.** Les cinq critères de sortie sont verts avec preuves exécutées à froid dans CE sweep (pas de réutilisations des runs des tasks précédentes), sur les deux familles iOS et tvOS, sans aucun échec, skip non documenté, ni duplication. Aucun commit produit (sweep verification-only — aucun défaut détecté).

---

## 5. Section de fix — revue finale (F-1, F-3, F-2)

Round de fix sur les trois findings de la revue finale, sur HEAD `b0c55c77`
(branche `zcode/sess-56645e3f`), commits **38c74ab8** (F-1), **de132989** (F-3),
**7f5c9b4f** (F-2). Portée du diff : exactement `kadre/contracts/registry/contracts.tsv`,
`kadre/platform/uikit/swift/Sources/KadreHost/KadreHostViewController.swift`,
`kadre/consumers/swift/consumer-app/tests/Shared/KadreHostViewControllerTests.swift`,
`kadre/capabilities/uikit.md`.

### F-1 — BCK-017/BCK-018 enregistrés `planned` (commit 38c74ab8)

Deux lignes à 11 colonnes en statut `planned` (légal sans preuve — miroir WIN-008/APK-016) :
BCK-017 (`INTEROP-EXPORTS.md#5`, façade Swift KadreHost — attach, observation, termination ;
scénarios `uikit-facade-attach-observes-to-terminated`,
`uikit-facade-await-termination-cancellation-keeps-session`,
`uikit-facade-race-never-resumes-twice` — KadreSessionHandleTests) et BCK-018
(`INTEROP-EXPORTS.md#9`, consumers UIKit/SwiftUI depuis leurs propres artefacts ;
scénario `uikit-swiftui-consumer-attaches-once` — KadreSwiftUIConsumerTests).
Le pipeline de preuve (export JUnit + corrélation validateur pour les targets consumer)
reste le follow-up qui les passera `active`.

**Écart assumé vs brief** : l'oracle de BCK-018 vaut **`O3`** et non `-`. Le validateur
parse la colonne `oracle` comme l'enum `ContractOracle` (`O1..O4`,
`ContractRegistry.kt:55` — `No enum constant org.graphiks.kadre.contracts.ContractOracle.-`
au premier run) : `-` n'est légal que pour les colonnes de liste. `O3` est la valeur
cohérente (preuve frontière externe — l'app consumer réelle lance les deux archives
statiques et traverse jusqu'à l'attach — vocabulaire BCK-013/015 demandé par le brief
lui-même). Validation :

```
### ./gradlew :kadre:contracts:validator:validateContractRegistry
BUILD SUCCESSFUL in 868ms          (planned sans preuve accepté ; avec '-' oracle : FAILED)
```

### F-3 — close() avant premier attach consomme le budget (commit de132989, TDD)

Bug : `close()` sans session laissait `attachState` à `waiting` — un join de fenêtre
ULTÉRIEUR (fenêtre + scène Active) posait alors une session fraîche malgré la fermeture
explicite. Fix : une ligne dans `close()` — un état non réglé (`waiting`/`retryArmed`)
devient `settled` ; la saveur `settled` existante suffit (pas de « closedBeforeAttach ») :
`attemptAttachAtValidMoment` garde sur `.waiting`, `fireActivationRetry` garde sur
`.retryArmed` (un réessai armé qui arrive ensuite est inerte, son token part au deinit).
KDoc étendue (close(), machine d'état, en-tête de classe).

Preuve TDD — nouveau scénario consumer
`uikit-facade-controller-close-before-attach-consumes-the-budget`
(testCloseBeforeAttachConsumesTheAttachBudget) : controller créé, `close()` AVANT tout
join, PUIS join (fenêtre de la scène Active) → `kadreSession` reste nil,
`attachAttempts` reste 0, deinit net (désallocation prouvée par weak tracking).

ROUGE (xcodebuild `-only-testing`, iOS, avant fix) :

```
KadreHostViewControllerTests.swift:387: error: -[KadreConsumerIosTests.KadreHostViewControllerTests
  testCloseBeforeAttachConsumesTheAttachBudget] : XCTAssertNil failed: "KadreConsumerIos.KadreSessionHandle"
  - a close before attach must consume the budget — no session after a later window join
KadreHostViewControllerTests.swift:390: error: ... XCTAssertEqual failed: ("1") is not equal to ("0")
  - a close before attach must consume the budget — zero real attach attempts
** TEST FAILED **
```

VERT (même commande après fix) :

```
Test Case '-[KadreConsumerIosTests.KadreHostViewControllerTests
  testCloseBeforeAttachConsumesTheAttachBudget]' passed (2.526 seconds).
** TEST SUCCEEDED **
```

### F-2 — report du câblage CI du gate consumer (commit 7f5c9b4f, doc only)

Note ajoutée sous le registre de `kadre/capabilities/uikit.md` (§2, lignes phase 3) :
le gate consumer (`consumers/swift`, 2 familles) tourne localement via l'agrégateur
`kadre/consumers/swift` ; le câblage CI (job xcode-27 dédié ou job macOS consolidé) est
un follow-up enregistré (revue finale F-2).

### Vérification d'ensemble — gate consumer, deux familles (05:27)

```
### ./gradlew -p kadre/consumers/swift -PkadreRepository=$PWD/build/kadre-contract-repository -PkadreVersion=1.0.0 check
BUILD SUCCESSFUL in 25s

ios  : Executed 47 tests, with 1 test skipped and 0 failures   (46 passed + skip SwiftUI gated préexistant ; 46 → 47 = +1 nouveau)
tvos : Executed 47 tests, with 1 test skipped and 0 failures
testCloseBeforeAttachConsumesTheAttachBudget : passed sur les DEUX familles (2.017s / 2.025s)
ios/tvos 'is implemented in both': 0                          (garde anti-duplication verte)
```

Le validator contract registry reste vert après commit (planned sans preuve). Aucun
autre test du dépôt n'est touché par le diff (Swift consumer-side + TSV + doc).
