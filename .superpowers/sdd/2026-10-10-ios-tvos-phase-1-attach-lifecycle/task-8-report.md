# Task 8 Report — Sweep à froid du critère de sortie Phase 1 iOS/tvOS

**Status:** DONE (verification-only sweep — **no commit**, working tree clean before and after)
**Branch:** `zcode/sess-56645e3f`, HEAD `2f5e1be8` (`test(uikit): run the runtime native tests in the gate and close the phase 1 docs`), les 13 commits de la branche présents (plan `ca4e5fed` → docs `2f5e1be8`).
**Sweep verdict:** **EXIT — les 5 items de la checklist d'exit Phase 1 sont PASS avec preuves fraîches régénérées à HEAD.**

---

## 1. Sweep à froid — commandes et sorties décisives

Chaque invocation Gradle a été exécutée comme commande Bash isolée (< 9 min). Les tâches marquées
UP-TO-DATE au premier passage ont été **forcées à ré-exécution** (`--rerun`) pour que le sweep soit
réellement à froid ; les horodatages des XML cités ci-dessous prouvent l'exécution de la session.

### 1.1 `git status --short`
```
(no output — working tree clean)
On branch zcode/sess-56645e3f ... nothing to commit, working tree clean
```

### 1.2 `./gradlew :kadre:runtime:jvmTest --rerun`
```
> Task :kadre:runtime:jvmTest
BUILD SUCCESSFUL in 5s
```
Résultats (`kadre/runtime/build/test-results/jvmTest/*.xml`) : **24 suites, 408 tests, 0 failure,
0 error, 0 skipped**. Suites notables : `RuntimeWindowManagerTest` 158, `RuntimeWindowSurfaceTest` 69,
`RuntimeHostControllerTest` 18, `RuntimeSessionComponentsTest` 13, `UnsupportedManagersTest` 2.

### 1.3 `./gradlew :kadre:runtime:iosSimulatorArm64Test :kadre:runtime:tvosSimulatorArm64Test --rerun`
(puis chaque tâche re-forcée individuellement pour horodater sans ambiguïté)
```
> Task :kadre:runtime:iosSimulatorArm64Test
BUILD SUCCESSFUL in 3s
> Task :kadre:runtime:tvosSimulatorArm64Test
BUILD SUCCESSFUL in 3s
```
- iOS (`.../test-results/iosSimulatorArm64Test/`, XML écrits 05:11:34) : 13 suites, **83 tests, 0 failed**.
- tvOS (`.../test-results/tvosSimulatorArm64Test/`, XML écrits 05:11:44) : 13 suites, **83 tests, 0 failed**.
- Suites natives propres au ciblage natif présentes et vertes des deux côtés :
  `NativePrimitivesConcurrencyTest` (7) et `RuntimeWindowSurfaceConcurrencyTest` (2).

### 1.4 `./gradlew :kadre:backend:appkit:appkitNativeTests` (tâche agrégée `appKitNativeTests`)
```
> Task :kadre:backend:appkit:appKitNativeTests
BUILD SUCCESSFUL in 35s
```
- `appKit jvmTest` : 30 suites, **371 tests, 0 failed** (dont `AppKitWindowRuntimeDriverTest` 127,
  `KffiAppKitWindowPortMacOsTest` 45, `AppKitBackendProviderTest` 65).
- `appKitStandaloneLoopTest` (process-owning, forkEvery=1, XML écrits 05:12:07) : **2 tests, 0 failed**
  (`realKffiStandaloneLoopStartsAndStopsOnMacOs`, `KffiAppKitStandaloneActivationMacOsTest`).
- Total agrégé : **373 tests, 0 failed** — la portabilisation n'a rien cassé côté AppKit.

### 1.5 `./scripts/test-uikit-simulator.sh all`
```
OK: destination platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0
xcodebuild -project KadreUikitDriver.xcodeproj -scheme KadreUikitDriverIos
  -destination "platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0"
  -resultBundlePath .../kadre/contracts/driver/uikit/build/xcresult/ios.xcresult CODE_SIGNING_ALLOWED=NO test
2026-10-11 05:14:12 ... IDETestOperationsObserverDebug: 41.453 elapsed
** TEST SUCCEEDED **
BUILD SUCCESSFUL in 48s                      # xcresult → JUnit export + génération + validation preuves iOS

OK: destination platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0
xcodebuild ... -scheme KadreUikitDriverTvOs -destination "platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0"
  -resultBundlePath .../build/xcresult/tvos.xcresult CODE_SIGNING_ALLOWED=NO test
2026-10-11 05:14:43 ... IDETestOperationsObserverDebug: 22.721 elapsed
** TEST SUCCEEDED **
BUILD SUCCESSFUL in 30s                      # JUnit export + generateUikitBCK012/013/WIN009 + validateTvos...

> Task :kadre:platform:uikit:linkDebugFrameworkIosArm64      # slices appareil (compilent et se lient)
> Task :kadre:platform:uikit:linkDebugFrameworkTvosArm64
BUILD SUCCESSFUL in 1s

> Task :kadre:runtime:iosSimulatorArm64Test UP-TO-DATE       # fraîchement ré-exécutés en 1.3
> Task :kadre:runtime:tvosSimulatorArm64Test UP-TO-DATE

OK: kadre/contracts/driver/uikit/build/contract-evidence/iosSimulatorArm64/contract-evidence/BCK-012.json
OK: kadre/contracts/driver/uikit/build/contract-evidence/iosSimulatorArm64/contract-evidence/BCK-013.json
OK: kadre/contracts/driver/uikit/build/contract-evidence/iosSimulatorArm64/contract-evidence/BCK-014.json
OK: kadre/contracts/driver/uikit/build/contract-evidence/iosSimulatorArm64/contract-evidence/WIN-009.json
OK: kadre/contracts/driver/uikit/build/contract-evidence/tvosSimulatorArm64/contract-evidence/BCK-012.json
OK: kadre/contracts/driver/uikit/build/contract-evidence/tvosSimulatorArm64/contract-evidence/BCK-013.json
OK: kadre/contracts/driver/uikit/build/contract-evidence/tvosSimulatorArm64/contract-evidence/WIN-009.json
uikit gate (all): SUCCESS
```
Bundles xcresult fraîches : `ios.xcresult` (05:14:16), `tvos.xcresult` (05:14:46).
Exports JUnit corrélés : `build/contract-evidence/{ios,tvos}SimulatorArm64/test-results/*/TEST-*.xml`
(écrits 05:14:16 et 05:14:46).

---

## 2. Checklist d'exit (roadmap IOS-IMPLEMENTATION-ROADMAP.md) — verdicts et preuves

### Verdict 1 — Deux scènes iPadOS exécutent deux applications distinctes : **PASS**
Preuve : `kadre/contracts/driver/uikit/build/contract-evidence/iosSimulatorArm64/test-results/iosSimulatorArm64/TEST-KadreUikitAttachTests.xml`
(suite `KadreUikitAttachTests`, **tests=10 failures=0**, 05:14:16, depuis `ios.xcresult`) :
- `KadreUikitAttachTests.testTwoScenesRunIndependentSessions() PASS` — deux fixtures de scènes
  distinctes (`first.scene.isEqual(second.scene)` faux), ids de session distincts par identité
  (`session1.id.isEqual(session2.id)` faux), surfaces primaires distinctes (`firstSurface ===
  secondSurface` faux) ; après `session1.requestStop()`, la session 2 reste `Running`, la surface
  primaire du fixture 2 reste la surface de sa fenêtre primaire (`KadreUikitAttachIosTests.swift` l.82-119).
- `KadreUikitAttachTests.testSceneDisconnectionTerminatesOnlyItsSession() PASS` — le disconnect de la
  scène 2 termine SA session (`SessionStateTerminated`) pendant que la session 1 reste `Running`
  (`XCTAssertNotNil(first.primarySurfaceId)`) (l.125-182).
- Scénario corrélé `BCK-014/uikit-two-scenes-independent` → `Passed` dans
  `contract-evidence/iosSimulatorArm64/contract-evidence/BCK-014.json` (sentinelle
  `uikit-window-scene-owner` → `Killed`) ; BCK-014 est `active` et mono-cible
  `iosSimulatorArm64` dans `kadre/contracts/registry/contracts.tsv` l.69.

### Verdict 2 — Host mono-scène tvOS : transitions + teardown sans résurrection : **PASS**
Preuve : `kadre/contracts/driver/uikit/build/contract-evidence/tvosSimulatorArm64/test-results/tvosSimulatorArm64/TEST-KadreUikitAttachTests.xml`
(suite `KadreUikitAttachTests`, **tests=7 failures=0**, 05:14:46, depuis `tvos.xcresult` sur Apple TV 4K 3rd generation) :
- `testLifecycleTransitionsFollowTheScene() PASS` — notifications de scène RÉELLES postées sur la
  scène : DidEnterBackground → (Background, Inactive) ; WillEnterForeground+DidActivate →
  (Foreground, Active) ; WillDeactivate → (Foreground, Inactive)
  (`kadre/contracts/driver/uikit/tests/Shared/KadreUikitAttachTests.swift` l.290-306).
- `testStopThenReattachOnTheSameScene() PASS` — `requestStop` pendant `Starting`, arrêt normal
  (`SessionOutcomeStopped`), puis re-attach sur la MÊME scène avec un id de session NOUVEAU
  (`XCTAssertFalse(second.id.isEqual(first.id))`, l.228-252) — pas de résurrection de l'ancienne session.
- Soutien « sans résurrection » sur tvOS (même XML, PASS) :
  `testDisconnectDuringStopTerminatesExactlyOnce()` (exactement UN terminal `Stopped(HostRequested)`,
  callback tardif ignoré, l.258-285) et `testLateNativeCallbackAfterTerminationIsIgnored()`
  (0 dérive post-termination, session reste `Terminated`, l.311-328).
- Code : le réducteur assigne ABSOLUMENT chaque transition (`UIKitLifecycleReducer.reduce`,
  `kadre/platform/uikit/src/uikitMain/.../UIKitLifecycleReducer.kt` l.47-62, jamais `copy`) et route
  `DidDisconnect` hors états ; `UIKitLifecycleSource` ferme les observers exactement une fois et route
  toute livraison en file au moment du close vers `onLateCallback` (l.37-44, 57-69).

### Verdict 3 — Aucun objet host-owned détruit : **PASS**
Revue du code — `kadre/platform/uikit/src/uikitMain/kotlin/org/graphiks/kadre/internal/uikit/UIKitWindowCommandPort.kt` :
- l.96-106 : `UIKitWindowPeerOwner.close()` — **`override fun close() = Unit`**, avec commentaire :
  « Phase 1 : aucun objet natif possédé par Kadre (la fenêtre et la vue restent au host)… Toute future
  ressource Kadre-owned sera retirée ICI, jamais sur la fenêtre elle-même. »
- l.165-175 : `requestOpenedClose` — retire uniquement l'entry des trois registres sous verrou
  (`byRequest`/`byWindow`/`bySurface`) et rend `Accepted` ; aucun appel UIKit ; contrat documenté l.66-68
  (« la fermeture ne détruit JAMAIS la UIWindow/UIView du host »).
- l.185-192 : `requestRedraw` n'injecte qu'un stimulus (`RedrawConsumed`) — jamais d'appel natif.
Test — `testSceneDisconnectionTerminatesOnlyItsSession()` (iOS, PASS) : après le disconnect de la
scène 2, les références Swift du host restent utilisables (`KadreUikitAttachIosTests.swift` l.164-171) :
`view2.bounds` == 320×240 (`XCTAssertEqual` precision 0.001), `XCTAssertFalse(window2.isHidden)`,
`window2.layoutIfNeeded()` exécuté — Kadre n'a rien révoqué dans la hiérarchie du host.

### Verdict 4 — Garanties structurelles + contrats activés avec preuves : **PASS**
- Activation : `kadre/contracts/registry/contracts.tsv` l.67-70 — `BCK-012 active`, `BCK-013 active`,
  `BCK-014 active`, `WIN-009 active`, colonnes cibles `iosSimulatorArm64,tvosSimulatorArm64`
  (BCK-014 : `iosSimulatorArm64` seul, par design iPadOS). Corrélation scenario/sentinelle → test
  JUnit concret dans `kadre/contracts/driver/uikit/contracts/evidence.tsv`.
- Les **7 JSONs régénérés à HEAD pendant ce sweep** (tous `"commit": "2f5e1be833cb…"`, `"execution":
  {"kind":"junit"}`, `adapter: uikit-driver`), validés par les 7 lignes `OK:` du gate :
  - iOS (junit corrélé tests=12 failures=0) : BCK-012 (2 scénarios Passed, sentinelle Killed),
    BCK-013 (6 scénarios Passed, sentinelles `uikit-scene-single-owner` + `uikit-lifecycle-scene-faithful` Killed),
    BCK-014 (3 scénarios Passed, sentinelle Killed), WIN-009 (2 scénarios Passed, sentinelle
    `uikit-primary-metrics-faithful` Killed).
  - tvOS (junit corrélé tests=9 failures=0) : BCK-012, BCK-013, WIN-009 — mêmes profils Passed/Killed.
  - Les 12 tests iOS = 10 `KadreUikitAttachTests` + 2 `KadreUikitDriverTests` ; les 9 tvOS = 7 + 2 —
    JSON et XML JUnit sont numériquement corrélés par target.

### Verdict 5 — Cinq managers avant `Running` : **PASS**
Roadmap (`kadre/IOS-IMPLEMENTATION-ROADMAP.md` l.110) : « Les cinq managers `windows`, `displays`,
`devices`, `capture` et `diagnostics`, avec leurs snapshots initiaux, existent avant `Running`. »
- Chemin de la factory/revue : `SessionRuntime` (`kadre/runtime/src/commonMain/.../SessionRuntime.kt`)
  construit TOUS les managers comme propriétés d'initialisation de l'objet session — donc avant toute
  transition `Starting → Running` (le `start()` n'est appelé qu'ensuite, l.176) :
  - `runtimeDiagnostics` (l.91-94) ; `runtimeDisplays` = port display ou **`UnsupportedDisplayManager`** (l.128-131) ;
  - `runtimeWindows` via `runtimeComponents.windows` (l.132), dont la factory par défaut est
    `RuntimeSessionComponents` → **`UnsupportedWindowManager`** (`RuntimeSessionComponents.kt` l.260-261,
    fallback host `RuntimeHostController.kt` l.397) ;
  - `runtimeDevices` = gamepad/input ou **`UnsupportedDeviceManager`** (l.135-154) ;
  - `runtimeCapture` = port capture ou **`UnsupportedCaptureManager`** (l.164).
- Surface publique avant `Running` : `KadreScope` (`kadre/foundation/src/commonMain/.../Application.kt`
  l.36-48) expose `windows`, `displays`, `devices`, `capture`, `diagnostics` ;
  `SessionRuntime.ApplicationScope` les branche (l.490-494).
- Snapshots initiaux des fallbacks : `kadre/runtime/src/commonMain/.../UnsupportedManagers.kt` —
  `WindowManagerState` (l.53-60), `DisplayManagerState` avec `DisplayInventory.Unavailable` (l.89-95),
  `DeviceManagerState` avec `DeviceInventory.Unsupported` (l.111-116), `CaptureManagerState` avec
  permissions `Unavailable` + capacités `Unsupported` + `CaptureSources.Unavailable` (l.131-147).
- Tests : `UnsupportedManagersTest` (2 tests verts, JVM) ; suites session vertes sur les trois cibles
  (`RuntimeSessionComponentsTest` 13 JVM, `RuntimeLifecycleTest` 6 JVM).

---

## 3. Auto-revue (findings)

1. **Artefact périmé non validé `BCK-010.json`** dans `kadre/contracts/driver/uikit/build/contract-evidence/*/contract-evidence/`
   (commit embarqué `fea394e`, horodaté 19:23 la veille — antérieur à la branche ; BCK-010 du registre
   est le contrat capture web js/wasmJs). Il n'appartient PAS aux 7 JSON validés par le gate et vit
   uniquement sous `build/` (gitignore). Gravité : cosmétique. Suggestion future : purger les
   répertoires d'evidence en tête du gate pour éviter toute confusion lors d'un sweep manuel.
   **Ne bloque pas l'exit.**
2. **Fallback du disconnect scène documenté et ratifié** : dans le simulateur, `requestSceneSessionDestruction`
   est un no-op silencieux ; le test tente d'abord la vraie destruction puis poste la VRAIE notification
   `UIScene.didDisconnectNotification` routée par identité (`KadreUikitAttachIosTests.swift` l.132-155).
   La garantie Apple « une vraie destruction poste la notification » reste à confirmer sur matériel
   (charter manuel phase 10, noté dans `kadre/capabilities/uikit.md`) — c'est un bornage d'environnement
   assumé, pas un défaut du sweep.
3. **`--rerun` nécessaire pour un vrai cold sweep** : plusieurs tâches Gradle étaient UP-TO-DATE à
   l'arrivée ; chaque tâche citée a été forcée et l'horodatage des XML vérifié (05:11–05:14). Les
   comptes « 12/9 tests » des JSONs recoupent exactement les XML (10+2 / 7+2), ce qui exclut une
   corrélation au Rogers d'une exécution périmée.
4. **Avertissement Gradle préexistant** (« Kotlin Source Set 'uikitTest' … doesn't depend on 'commonTest' »)
   imprimé par la configuration `:kadre:platform:uikit` — présent avant ce sweep, sans effet sur les
   résultats ; à traiter éventuellement dans une phase ultérieure de ménage.
5. **Aucun commit** : sweep purement vérificationnel, arbre propre avant/après (`git status --short` vide) ;
   aucun défaut réel trouvé, donc pas de commit de fix (conforme à la consigne).

## 4. Conclusion

Le critère de sortie Phase 1 iOS/tvOS est **tenu à HEAD `2f5e1be8`** : JVM 408/0, natifs simulateurs
83/0 + 83/0, AppKit 371+2/0, gate UIKit `all` SUCCESS (TEST SUCCEEDED ×2, 7 preuves JSON OK, liens
appareil OK), et les cinq items de checklist PASS avec preuves citées. Aucune régression, aucun objet
host-owned détruit, contrats actifs avec sentinelles tuées, cinq managers disponibles avant `Running`.

## 5. Fix de revue finale — enforcement du contrat main-thread à l'entrée de `KadreIos.attach`

**Commit** : `a4ac38ed` — `fix(uikit): enforce the main-thread contract at KadreIos.attach entry`

**Défaut** (revue finale) : `KadreIos.attach` documentait « L'appelant DOIT être sur le thread
principal » sans l'imposer ; un appel hors main-thread compilait et corrompait silencieusement les
lectures UIKit et le pont d'admission différé.

**Changement** (`KadreIos.kt`, un seul fichier) :

1. Tout premier énoncé du corps d'`attach`, avant toute validation :

   ```kotlin
   check(NSThread.isMainThread()) {
       "KadreIos.attach doit être appelé depuis le thread principal (les lectures UIKit et le pont d'admission l'exigent)"
   }
   ```

   `check` lève une `IllegalStateException` — délibéré : un appel hors main-thread est une erreur de
   programmation de l'appelant, pas un mode d'échec d'attach (les champs `window` / `surfaceView`
   restent réservés à la validation des arguments).

2. KDoc mis à jour : le contrat est désormais « vérifié à l'entrée
   (`NSThread.isMainThread()`, sinon IllegalStateException) ».

**Écart API assumé et motivé** : la consigne proposait `check(Dispatchers.Main.isCurrent())`.
Vérifié dans les sources natives kotlinx-coroutines 1.10.1
(`kotlinx-coroutines-core-iossimulatorarm64-1.10.1-sources.jar`, `commonMain/MainCoroutineDispatcher.kt`
et `nativeDarwinMain/Dispatchers.kt`) : `MainCoroutineDispatcher.isCurrent()` est une API JVM-only —
le premier compilateur `iosSimulatorArm64` a échoué sur
`Unresolved reference 'isCurrent' on receiver of type 'MainCoroutineDispatcher'`. L'équivalent
public qui existe sur natif Darwin est `platform.Foundation.NSThread.isMainThread()` (l'équivalent
interne du coroutines, `isMainThread()` sur `CFRunLoopGetCurrent() == CFRunLoopGetMain()`, est
`internal`). Le check retenu s'aligne sur la convention existante du dépôt
(`KffiAppKitWindowPort.kt` l.555 : `check(NSThread.isMainThread())`). La sémantique est identique au
contractuel `Dispatchers.Main.isCurrent()` JVM : le thread courant est-il le thread principal.

**Vérifications** (worktree `sess-56645e3f`, HEAD de base `2f5e1be8`) :

1. `:kadre:platform:uikit:compileKotlinIosSimulatorArm64 :…compileKotlinTvosSimulatorArm64` —
   BUILD SUCCESSFUL.
2. `:kadre:platform:uikit:compileKotlinIosArm64 :…compileKotlinTvosArm64` — BUILD SUCCESSFUL
   (les 4 cibles compilent).
3. `:kadre:platform:uikit:iosSimulatorArm64Test :…tvosSimulatorArm64Test` — BUILD SUCCESSFUL ;
   les tests natifs (réducteurs compris) restent verts.
4. `git diff` limité audit fichier (import `platform.Foundation.NSThread`, `check` d'entrée, KDoc) ;
   `git status --short` vide après commit.

Aucun nouveau test : le check est un garde-fou d'entrée (erreur de programmation) ; son premier
énoncé fait échouer tout appel off-main avant tout effet de bord, et les tests natifs existants
(appelant sur le main thread du runner XCTest) le traversent sans changement.
