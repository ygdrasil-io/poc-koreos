# Task 5 Report — Driver multi-scène iPadOS + parcours tvOS : la preuve O3

**Status: BLOCKED** — le livrable driver/test est complet et committé, mais la vérification ne peut pas aller au vert : **`KadreIos.attach` échoue systématiquement** (`PlatformFailure(runtime-session-components, create-failed)`) à cause d'un **défaut d'ordre dans la composition Kotlin de la Task 4** (jamais exercé par un attach réussi, cf. son propre rapport), et la **demande de seconde scène est impossible dans l'environnement simulateur** (refus propre sur iPhone, erreur XPC + kill du host sur iPad). Aucune assertion n'a été affaiblie ; les erreurs exactes sont capturées ci-dessous avec le design de remédiation.

**Commit:** `88451b9a` — `test(uikit): prove scene attach, independence and lifecycle on both families` (parent `b7987602`, branche `zcode/sess-56645e3f`)

---

## 1. Formes RÉELLES du header utilisées (citations)

Source : `kadre/platform/uikit/build/bin/iosSimulatorArm64/debugFramework/KadreUikit.framework/Headers/KadreUikit.h` (identiques tvOS ; header sous `NS_ASSUME_NONNULL_BEGIN`, l.13). Le sketch du brief (`create(sessionId:rootScope:)`, `onCreate(scope:)`) **ne correspond pas** aux déclarations exportées ; les adaptations suivantes ont été faites d'après le header, jamais la sémantique :

**KadreApplicationFactory (l.244-248)** — le scope n'arrive PAS au `create`, il arrive au `run` :
```objc
@protocol KadreUikitKadreApplicationFactory
@required
- (id<KadreUikitKadreApplication>)createContext:(KadreUikitKadreLaunchContext *)context __attribute__((swift_name("create(context:)")));
@end
```
Swift : `func create(context: KadreLaunchContext) -> any KadreApplication`.

**KadreApplication (l.233-242)** — méthode suspend exportée, completionHandler à appeler exactement une fois :
```objc
@protocol KadreUikitKadreApplication
@required
- (void)run:(id<KadreUikitKadreScope>)receiver completionHandler:(void (^)(NSError * _Nullable))completionHandler __attribute__((swift_name("run(_:completionHandler:)")));
@end
```
Swift : `func run(_ receiver: any KadreScope, completionHandler: @escaping ((any Error)?) -> Void)`.

**KadreSession (l.330-342)** :
```objc
@protocol KadreUikitKadreSession <KadreUikitKotlinAutoCloseable>
@required
- (void)awaitTerminationWithCompletionHandler:(void (^)(id<KadreUikitSessionOutcome> _Nullable, NSError * _Nullable))completionHandler __attribute__((swift_name("awaitTermination(completionHandler:)")));
- (void)requestStop __attribute__((swift_name("requestStop()")));
@property (readonly) KadreUikitSessionId *id __attribute__((swift_name("id")));
@property (readonly) id<KadreUikitKotlinx_coroutines_coreStateFlow> state __attribute__((swift_name("state")));
@end
```

**KadreScope (l.304-317)** — lecture des faits WIN-009 : `primarySurface` est un StateFlow, `windows` un manager dont `state` est un StateFlow :
```objc
@property (readonly) id<KadreUikitKotlinx_coroutines_coreStateFlow> primarySurface __attribute__((swift_name("primarySurface")));
@property (readonly) id<KadreUikitWindowManager> windows __attribute__((swift_name("windows")));
@property (readonly) id<KadreUikitKadreLifecycle> lifecycle __attribute__((swift_name("lifecycle")));
@property (readonly) KadreUikitSessionId *sessionId __attribute__((swift_name("sessionId")));
```
StateFlow exporte un `.value` lisible en Swift (l.6962-6965) : `@property (readonly) id _Nullable value __attribute__((swift_name("value")));`
Chemin réel des faits : `scope.primarySurface.value as? (any HostSurface)` → `.id` ; `scope.windows.state.value as? WindowManagerState` → `.primary?.surface` / `.primary?.state.value as? WindowState` → `.contentSize.width/.height` ; `primarySurface?.state.value as? SurfaceState` → `.logicalSize.width/.height`.

**Résultats et échecs** : `KadreResult` (l.2042, protocole vide), `KadreResultSuccess<T>` avec `.value: T?` (l.2059-2067), `KadreResultFailure` avec `.reason` (l.2047-2056), `KadreFailureInvalidRequest` avec `field: String?` (l.1787-1795), `KadreFailureAlreadyInUse` avec `resource: KadreResourceKind` (l.1742-1750), `KadreResourceKind.host` (l.2016).

**Policy** : `KadrePolicies.shared.Default` (l.5307-5317) — l'export Obj-C n'a pas de valeur par défaut, le Swift passe la policy explicitement.

**Noms Swift réels** (pièges rencontrés) : `SessionId` (PAS `KadreSessionId` — première compilation a échoué dessus), `WindowManagerState`, `WindowState`, `HostSurface`, `SessionStateRunning/Starting/Stopping/Terminated`, `SessionOutcomeStopped`, `SessionStopReason.hostrequested`, `VisibilityState.foreground/.background`, `ActivationState.active/.inactive`.

**Identité WIN-009 vérifiée dans les sources Kotlin** (pas seulement le header) : `RuntimeSessionComponents.primarySurface` **dés enveloppe** le wrapper — `public val primarySurface: HostSurface? = primarySurface?.surface` (RuntimeSessionComponents.kt l.76) — donc `scope.primarySurface.value` EST la même instance `RuntimeWindowSurface` que `manager.state.value.primary?.surface` ; le `===` Swift est donc garantí par construction (Task 4 l'avait sanctionné).

## 2. Design du fixture par scène

- `SceneFixture` (un par `ObjectIdentifier(scene)`) : `scene`, `window`, `observedView` (320×240, DANS la fenêtre), `detachedView` (jamais ajoutée), `becameActive` ; PLUS les faits capturés par l'application Kadre : `applicationRuns` (compteur), `launchSessionIdDescription`, `scope`, `primarySurfaceId`, `primaryWindowSurfaceId`, `primarySurfaceIsPrimaryWindowSurface` (comparaison `===` Swift entre deux objets Kotlin), `primaryWindowContentWidth/Height`, `primarySurfaceLogicalWidth/Height` ; PLUS `session: (any KadreSession)?` (enregistrée par le test après attach), `frozenLifecycleDescription` + `lateCallbackProxyCount` (proxy Swift du compteur de callbacks tardifs : la sentinelle `onLateCallback` de l'attach est interne à Kotlin — le proxy honnête est le gel du `scope.lifecycle.state.value` à la terminaison et le comptage des dérives après coup).
- `DriverFixture` devient un store thread-main : `install(windowScene:)` (au `willConnectTo`, crée fenêtre+vues), `fixture(for:)`, `connected` (ordre de connexion), `primary` (première scène — entrée des tests phase 0, dont les noms `uikit-driver-*` sont inchangés), `fixture(connectedAfter:)` (la seconde scène des tests multi-scènes).
- Attentes bornées sur SceneFixture (`waitUntilReady`, `waitUntilApplicationFacts`, `waitUntilSessionState`) : boucle `RunLoop.current.run(until: +0.02s)` + deadline, échec levé avec l'état courant (`stateSummary()`) — aucun sommeil nu, aucun passage par timeout.
- `DriverSceneDelegate` : installe/active le fixture DE sa scène (multi-scènes sans état partagé).

## 3. `DriverApplication` (l'application du host) et la complétion coopérative

- `DriverApplication: NSObject, KadreApplicationFactory` — `create(context:)` retourne un `DriverKadreApplication(fixture:launchSessionId:)`.
- `DriverKadreApplication.run(_:completionHandler:)` : appelé UNE fois, sur le dispatcher main, après le passage à Running (SessionRuntime.start : `create` puis `mutableState.value = SessionState.Running` puis `runner.start()`). Il capture les faits de scope dans le fixture, stocke le completionHandler, **revient sans compléter** — la session reste Running.
- **Complétion coopérative** (adaptation documentée) : le pont K/N d'une fonction suspend IMPLÉMENTÉE en Swift n'a pas de chemin d'annulation (rien ne peut appeler le handler détenu par Swift) ; sans coopération, chaque `requestStop` sur une session Running attendrait `shutdownTimeout` (5 s, `ExecutionPolicy(Balanced, 5.seconds)`) puis publierait `Failed(ShutdownTimedOut)`. Un Timer RunLoop (20 ms, mode common) lit `session.state.value` et complète `nil` dès que l'état passe à Stopping/Terminated — l'outcome reste `Stopped(HostRequested)` (le premier sélectionné gagne, `selectOutcome`). C'est exactement le comportement d'une application bien élevée qui termine sa boucle quand le host l'arrête.
- `requestStop` pendant Starting (test 7) : `startupJob?.cancel()` → `applicationJob == null` → terminaison immédiate, sans passer par la coopération.

## 4. Mécanique multi-scène retenue (et pourquoi)

- `project.yml` : `UIApplicationSupportsMultipleScenes: true` pour `KadreUikitDriverIos` UNIQUEMENT (tvOS reste `false`, mono-scène) ; xcodegen regénéré et committé ; l'Info.plist installé sur le simulateur a bien été vérifié (`UIApplicationSupportsMultipleScenes => true`).
- Mécanique de demande : `UIApplication.shared.requestSceneSessionActivation(nil, userActivity: NSUserActivity(activityType: "org.graphiks.kadre.driver.scene"), options: nil) { error in ... }` — mécanique UIKit RÉELLE, aucune scène synthétique ; le fixture de la seconde scène naît du vrai `willConnectTo` du delegate.
- Destruction : `requestSceneSessionDestruction(session, options: nil)` avec errorHandler inversé (échoue le test si refusée) — UIKit poste alors le VRAI `DidDisconnect`.
- **Comportement réel capturé (impossibilité d'environnement)** :
  - iPhone 17 (iOS 27 sim) : refus propre, immédiat — `Error Domain=UISceneErrorDomain Code=0 "The current device does not support multiple scenes."` (l'idiome téléphone ne supporte pas le multi-scène — la destination iPhone 17 prescrite ne peut pas exécuter les 3 tests multi-scènes) ;
  - iPad Pro 11-inch (M5) (iOS 27 sim) : `Error Domain=BSServiceConnectionErrorDomain Code=3 "XPC error received on message reply handler"` suivi de `Restarting after unexpected exit, crash, or test timeout` — le host de test est tué (xcresult : « Test crashed with signal kill. ») ; identique avec `userActivity: nil` et avec un `NSUserActivity` réel, test unitaire comme destination partagée. La demande de scène session via XPC FrontBoard n'est donc pas viable depuis un host XCTest sur CoreSimulator.
- Les tests multi-scènes restent ÉCRITURE NORMATIVE (pas de XCTSkip, pas de contournement factice) : ils échouent avec l'erreur exacte capturée dans le message (`scene session activation refused: …`), prêts à passer dès que l'environnement le permet (destination iPad + activation fonctionnelle).

## 5. Matrice test → famille

Mécanisme de répartition : fichiers sources PAR famille dans project.yml — `tests/Shared` compilé dans les deux cibles de tests ; `tests/Ios/KadreUikitAttachIosTests.swift` (extension de la MÊME classe `KadreUikitAttachTests` pour que les noms figés restent dans une seule classe XCTest) compilé UNIQUEMENT dans `KadreUikitDriverIosTests`. Aucun XCTSkip ; la suite tvOS ne demande jamais de seconde scène.

| Test | Famille | Statut constaté |
|---|---|---|
| testAttachRejectsViewOutsideTheProvidedWindow | iOS + tvOS | **PASSE ×2** |
| testAttachRejectsWindowFromAnotherScene | iOS only | échec : refus d'activation (erreur exacte §4) |
| testSecondAttachOnTheSameSceneIsAlreadyInUse | iOS + tvOS | échec : create-failed (défaut Task 4) |
| testPrimaryWindowAndSurfaceIdentity | iOS + tvOS | échec : create-failed |
| testStopThenReattachOnTheSameScene | iOS + tvOS | échec : create-failed |
| testDisconnectDuringStopTerminatesExactlyOnce | iOS + tvOS | échec : create-failed |
| testLifecycleTransitionsFollowTheScene | iOS + tvOS | échec : create-failed |
| testLateNativeCallbackAfterTerminationIsIgnored | iOS + tvOS | échec : create-failed |
| testTwoScenesRunIndependentSessions | iOS only | échec : create-failed |
| testSceneDisconnectionTerminatesOnlyItsSession | iOS only | échec : create-failed |

(+ les 2 tests phase 0 `KadreUikitDriverTests` inchangés et verts sur les deux familles.)

## 6. Commandes et sorties XCTest (lignes « Executed N tests » citées)

1. `./gradlew :kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64` → EXIT 0
2. `./gradlew :kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64` → EXIT 0
3. `xcodebuild -project KadreUikitDriver.xcodeproj -scheme KadreUikitDriverIos -destination 'platform=iOS Simulator,name=iPhone 17,OS=27.0' -resultBundlePath build/xcresult/ios.xcresult CODE_SIGNING_ALLOWED=NO test` → `** TEST FAILED **`
   - `Executed 10 tests, with 18 failures (1 unexpected) in 0.361 (0.367) seconds` (KadreUikitAttachTests)
   - `Executed 12 tests, with 18 failures (1 unexpected) in 0.362 (0.371) seconds` (total, + 2 tests phase 0 verts)
4. `xcodebuild … -scheme KadreUikitDriverTvOs -destination 'platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0' -resultBundlePath build/xcresult/tvos.xcresult CODE_SIGNING_ALLOWED=NO test` → `** TEST FAILED **`
   - `Executed 7 tests, with 12 failures (0 unexpected) in 0.103 (0.105) seconds` (KadreUikitAttachTests)
   - `Executed 9 tests, with 12 failures (0 unexpected) in 0.104 (0.108) seconds` (total)
5. Bonus diagnostic — même suite sur `platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0` :
   - `Executed 10 tests, with 17 failures (1 unexpected) in 1.470 (1.474) seconds` / `Executed 12 tests, with 17 failures (1 unexpected) in 1.471 (1.477) seconds` (la seconde scène se connecte parfois — 1,4 s — mais l'activation échoue en XPC ; cf. §4).

## 7. Défaut bloquant (preuve capturée) — défaut de COMPOSITION Task 4

**Symptôme** : tout attach nominal retourne `KadreResult.Failure(PlatformFailure(platform=UIKit, domain=runtime-session-components, code=create-failed))` sur LES DEUX familles. Instrumentation temporaire du `failureReporter` (revertée depuis, Task 4 pristine) :

```
KADRE-DIAG failure: kotlin.IllegalStateException: window event stamp source must be installed before admission
```

**Chaîne causale exacte** :
1. `SessionRuntime` (kadre/runtime/.../SessionRuntime.kt l.95-119) appelle `componentsFactory.create(id, rootScope)` PUIS `components.installSessionConfiguration(...)`.
2. La factory de `KadreIos.attach` (KadreIos.kt l.152-159) construit `UIKitWindowRuntime` DANS `create` ; l'init de `UIKitWindowRuntime` (UIKitWindowRuntime.kt l.88-100) fait `runBlocking { manager.requestWindow(spec) }` + `check(manager.state.value.primary != null)` — l'admission de la fenêtre primaire a donc lieu AVANT l'installation de la configuration.
3. `RuntimeWindowManager.installSessionConfiguration` (RuntimeWindowManager.kt l.298-299) exige l'inverse : `check(pending.isEmpty() && committed.isEmpty()) { "window event stamp source must be installed before admission" }` → IllegalStateException → attrapée par SessionRuntime → `create-failed`.
4. Le patron AppKit (le miroir documenté) n'a PAS ce problème : `AppKitWindowRuntimeDriver` ne demande AUCUNE fenêtre dans sa factory — la première fenêtre est demandée par la couche application APRÈS la configuration. La branche UIKit est unique (fenêtre fournie par le host, admission synchrone exigée pendant l'attach).

**Remédiation proposée (décision du contrôleur)** — dans les fichiers Task 4 UNIQUEMENT (kadre/platform/uikit), sans toucher kadre/runtime : un petit délégateur `RuntimeSessionWindowManager` retourné comme `windows` des components, qui (a) transmet `installSessionConfiguration` au manager (le check passe : rien n'est encore admis) puis (b) exécute l'admission différée `requestWindow(spec)` — toujours en `runBlocking` sur le thread appelant (main, garantie de l'attach), donc à l'intérieur de `SessionRuntime` init, donc AVANT que `attach` ne rende : le contrat « commit de la fenêtre primaire synchrone à l'attach » est préservé, la rétrogradation `runtimeComponents.primarySurface` lu à SessionRuntime l.165 arrive après l'installation. Variante rejetée : admission paresseuse au premier accès au scope — elle repousserait le commit après le retour d'`attach` et romprait le contrat documenté (KadreIos.kt l.79-83).

**Pourquoi je n'ai pas corrigé moi-même** : mandat explicite — « a genuine runtime defect (hang, wrong state) is a BLOCKED report for the controller with the captured evidence » ; les fichiers concernés sont le livrable Task 4 (commit revu `b7987602`), hors liste de fichiers Task 5 ; corriger à l'intérieur d'un commit `test(uikit)` brouillerait la frontière de revue.

## 8. Problèmes de frontière rencontrés et résolutions

| # | Problème | Résolution |
|---|---|---|
| 1 | Le brief esquissait `create(sessionId:rootScope:)` + `onCreate(scope:)` | Header réel : `create(context: KadreLaunchContext)` ; le scope arrive au `run(_:completionHandler:)` — conformé EXACTEMENT à l'export |
| 2 | `KadreSessionId` n'existe pas en Swift | Le type exporté est `SessionId` (erreur de première compilation) |
| 3 | Fonction suspend implémentée en Swift : pas de chemin d'annulation du pont | Complétion coopérative via Timer lisant `session.state.value` (§3) — évite `ShutdownTimedOut` et garde l'outcome `Stopped(HostRequested)` |
| 4 | `XCTFail` n'existe pas dans la cible APP (pas de XCTest lié) | Les attentes du fixture lèvent `NSError` avec l'état courant (patron phase 0) ; le XCTFail reste dans la couche test |
| 5 | `requestSceneSessionActivation(nil, nil, nil)` — labels Swift | Signature réelle `(_ sceneSession:, userActivity:, options:, errorHandler:)` |
| 6 | Expectation inversée « must not error » échouait avant capture de l'erreur | Helper réécrit sans expectation : erreur capturée dans une closure protégée par NSLock, échec avec le message exact |
| 7 | iPhone refuse le multi-scène (§4) ; iPad échoue en XPC + kill | Mécanique réelle conservée, erreurs exactes remontées dans les échecs de test et dans ce rapport — aucune scène synthétique |
| 8 | `wait(for:timeout:file:line:)` n'existe pas | Signatures XCTest réelles utilisées |

## 9. Auto-revue

- **Ce qui est solide** : formes d'export conformées au header (jamais au sketch) ; fixture par scène sans état partagé ; mécanique multi-scène 100 % UIKit réel ; attentes bornées partout avec diagnostics d'état ; tests 2/3/4/7/8/9/10 prêts à servir de preuve Task 6 dès que la plateforme attache ; le test de refus `surfaceView` (validation AVANT réservation) passe déjà sur les deux familles — première preuve O3 du chemin de validation d'`attach`.
- **Ce qui n'a PAS pu être prouvé** (bloqué par §7) : toute la partie positive — identité WIN-009 (le `===` primaire/surface est garanti par les sources mais jamais observé à l'exécution), sessions indépendantes, terminaisons exactement-une-fois, transitions de lifecycle, callbacks tardifs, complétion coopérative (jamais exercée).
- **Risques résiduels sur ma livraison** : (a) la complétion coopérative repose sur un Timer RunLoop — si Task 6 exécute des attentes sans pompage RunLoop (aucune attente XCTest ne pompe ? impossible : `wait(for:)` pompe), le fallback serait le `ShutdownTimedOut` à 5 s, qui échouerait proprement les assertions d'outcome (pas de masquage) ; (b) `waitUntilSessionState` traite `session == nil` comme « continue d'attendre » (échec borné, pas de faux vert) ; (c) les tests multi-scènes, une fois l'environnement débloqué, devront tourner sur destination iPad — la destination iPhone 17 prescrite ne pourra jamais les exécuter (limite d'idiome, pas de code).
- **Propriété intellectuelle de la preuve** : aucune assertion affaiblie ; aucun XCTSkip ; tout contournement factice refusé conformément au mandat.

---

# Fix Round 1 — rapport (contrôleur : remédiation autorisée)

**Commits:** `1384f1fe` — `fix(uikit): defer primary window admission until the session configuration is installed` (remédiation Kotlin, re-review demandée) ; `a809c978` — `test(uikit): drive driver scenes from the app and compare surface ids by identity` (mécanique app-side + durcissement des preuves + script). Parent : `88451b9a`.

## 1. Blocker 1 — remédiation du défaut d'ordre (AUTORISÉE, appliquée)

**Résultat : l'attach fonctionne.** Tous les tests positifs passent désormais sur les deux familles (cf. §3).

Diff (3 fichiers, +114/−21) :
- `kadre/runtime/.../RuntimeSessionComponents.kt` :
  - `RuntimeSessionWindowManager` passe `internal` → `public` (mêmes members + clause de doc calquée sur `RuntimePrimarySurfaceConfiguration` : public uniquement pour que les backends l'implémentent) — le delegate du contrôleur doit satisfaire le cast `(windows as? RuntimeSessionWindowManager)` fait par `RuntimeSessionComponents.installSessionConfiguration`, impossible depuis uikit tant que l'interface est module-interne. `ExclusiveDisplayTargetResolver` et tous les types de paramètres étaient déjà publics.
  - Nouveau `public class RuntimeDeferredAdmissionWindowManager(target, admission) : WindowManager by target, RuntimeSessionWindowManager` — transmet `installSessionConfiguration` au vrai manager PUIS exécute `admission()` ; `installExclusiveDisplayTargetResolver` est retransmis.
  - Le constructeur primaire gagne `primarySurfaceProvider: (() -> RuntimePrimarySurface?)?` (privé) + un constructeur public secondaire `(windows, primarySurfaceProvider, closeAction)` ; `primarySurface` / `closePrimarySurface` deviennent `by lazy` (évalués au PREMIER ACCÈS = `SessionRuntime` l.165, APRÈS l'installation). Les deux constructeurs publics existants gardent leurs signatures/noms exacts — **zéro appelant existant modifié** (Web, AppKit, `RuntimeHostController.withPrimarySurface`, tests jvm : intouchés, `:kadre:runtime:jvmTest` BUILD SUCCESSFUL).
- `kadre/platform/uikit/.../UIKitWindowRuntime.kt` : le bloc d'admission de l'`init` est DÉPLACÉ (verbatim, mêmes checks) dans `internal fun admitPrimaryWindow()` (exactement-une-fois) ; `view` devient `private val` ; le `RuntimePrimarySurface` est construit DANS l'admission (mémoïsé dans `attachedPrimarySurface`) — la surface n'existe qu'après le commit et `RuntimePrimarySurface` lie sa surface à la construction, d'où le provider paresseux côté runtime.
- `kadre/platform/uikit/.../KadreIos.kt` : la factory passe `windows = RuntimeDeferredAdmissionWindowManager(runtime.manager) { runtime.admitPrimaryWindow() }` et `primarySurfaceProvider = { runtime.attachedPrimarySurface }`.

**Contraintes du contrôleur vérifiées :** admission 100 % via `manager.requestWindow` (bloc déplacé verbatim) ; `primarySurface === primary.surface` même instance (le `RuntimePrimarySurface(surface)` enveloppe exactement la surface commitée par le manager) ; `WindowPeerOwner.close()` neutre (port intouché) ; aucun `Dispatchers.Main` dans le chemin d'admission (`runBlocking` sur le thread appelant = main, l'installation est synchrone dans la construction de session) ; ordre de teardown inchangé (`closePrimarySurface` paresseux → même instance → `::close`, puis `closeAction`).

**Écart documenté vs la lettre du contrôleur :** le delegate seul ne suffisait pas — `RuntimeSessionComponents` dérivait `primarySurface` (et le close) à la CONSTRUCTION, alors que `RuntimePrimarySurface` lie sa surface à sa construction et que la surface n'existe qu'après admission. D'où le provider paresseux + la classe delegate côté runtime (visibilité). Justifié dans les doc comments.

## 2. Blocker 2 — itération multi-scène (règle en deux temps)

- **(a) appel déplacé dans l'APP** : `DriverSceneRequest` (apps/Shared, inerte sans la variable) lit `ProcessInfo.processInfo.environment["KADRE_DRIVER_REQUEST_SECOND_SCENE"]` (nombre de scènes, défaut 1) au `didFinishLaunchingWithOptions` des deux `MainApp` ; **pass-through vérifié** : `TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=2 xcodebuild … test` atteint bien l'app hôte (les 2 scènes secondaires se connectent). **(b) différé jusqu'à la première scène ForegroundActive** : la première demande part sur `UIScene.didActivateNotification`, puis en série (la suivante à chaque connexion). Les erreurs d'activation sont enregistrées dans le store (`DriverFixture.sceneActivationErrors`) et remontées telles quelles dans les échecs.
- **Réécriture des tests multi-scènes** : `secondaryFixture(at:)` attend (borné) la n-ième scène demandée par l'app — le bundle de test ne demande JAMAIS d'activation. Test 1 utilise la scène #1 ; test 6 (SceneDisconnection) détruit la scène #1 ; test 10 (TwoScenes, dernier par ordre alphabétique) utilise la scène #2 (la session de la scène #1 a été détruite par le test 6).
- **Lecture readiness des scènes secondaires** : une scène demandée programmatiquement reste ForegroundInactive (`sceneDidBecomeActive` n'y fire jamais) — `waitUntilReady(requireActivation: false)` pour ces fixtures ; l'attach accepte ForegroundInactive par design (reducer + validation `!= Unattached`).
- **Destruction (test 6) — TOUS LES ATTEMPTS, comportement exact** : `requestSceneSessionDestruction(session, options: nil)` depuis le bundle de test ET depuis le code de l'APP (`DriverSceneRequest.destroySceneSession`, même contexte d'appel que l'activation qui fonctionne) : dans les DEUX cas **no-op silencieux** — errorHandler jamais appelé (expectation inversée 15 s), `scene.session` jamais invalidé (30 s), aucun `DidDisconnect`, session Kadre reste Running. Troisième variation honnête testée : réserver `makeKeyAndVisible` à la seule scène primaire (politique de key plus juste) — même no-op. La destruction de scène est donc inopérante dans cet environnement (simulateur iPad + XCTest), alors que l'activation au lancement est honorée. Conformément à la règle 2 : pas de skip, pas de faux, pas d'affaiblissement — le test 6 reste ÉCRITURE NORMATIVE et échoue avec l'erreur exacte.
- **Robustesse d'exécution** : `primaryFixture` attend borné (25 s) l'existence de la scène primaire (lancements à froid) ; `tearDown` arrête et attend (borné) toute session encore Starting/Running pour qu'un test fautif ne contamine pas les suivants (`AlreadyInUse` en cascade éliminé) ; les faits applicatifs attendus par `attach()` sont SENSIBLES À LA FRAÎCHEUR (`minimumRuns`) — le process hôte vit pendant toute la suite et les faits s'accumulent par fixture.
- **Découverte de preuve** : `SurfaceId.description` est rédigé par Kotlin (`SurfaceId(<redacted>)`) — l'égalité de chaînes d'ids entre sessions était VIDE DE SENS. Les fixtures capturent désormais `primarySurfaceIdMatchesWindowSurfaceId` (`isEqual`) et `primarySurfaceRef: AnyObject?` (`===`/`!==`) ; test 4 et test 10 assertent l'identité, jamais les chaînes.
- **Script** : `scripts/test-uikit-simulator.sh` — destination IOS par défaut `iPad Pro 11-inch (M5)` (l'idiome téléphone refuse le multi-scène : erreur exacte au §4 du rapport initial), `require_destination` inchangé, commentaire iPhone 17 via `KADRE_UIKIT_IOS_DESTINATION`.

## 3. Vérification finale (chacun sa propre commande)

1. `./gradlew :kadre:runtime:jvmTest` → BUILD SUCCESSFUL (zéro appelant modifié).
2. `./gradlew :kadre:platform:uikit:linkDebugFrameworkIosSimulatorArm64 :kadre:platform:uikit:linkDebugFrameworkTvosSimulatorArm64` → BUILD SUCCESSFUL.
3. iOS (iPad, scène app-side) : `TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=2 xcodebuild -project KadreUikitDriver.xcodeproj -scheme KadreUikitDriverIos -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0' -resultBundlePath build/xcresult/ipad.xcresult CODE_SIGNING_ALLOWED=NO test` → `** TEST FAILED **` sur UN SEUL test :
   - 9/10 verts : View-Outside, Window-From-Another-Scene, DisconnectDuringStop, LateNativeCallback, LifecycleTransitions, PrimaryWindowIdentity, SecondAttach-AlreadyInUse, StopThenReattach, TwoScenesIndependent — TOUS les scénarios positifs BCK-013/WIN-009 passent ;
   - `Executed 10 tests, with 2 failures (1 unexpected) in 61.063 (61.068) seconds` (AttachTests) ; `Executed 2 tests, with 0 failures (0 unexpected) in 0.002 (0.004) seconds` (phase 0) ; total `Executed 12 tests, with 2 failures (1 unexpected) in 61.064 (61.071) seconds`.
   - Le seul échec = `testSceneDisconnectionTerminatesOnlyItsSession` : « UIKit never invalidated the destroyed scene session in 30s » puis « session state not reached in 15.0s » (§2 — destruction inopérante dans l'environnement, preuve des trois attempts documentée).
4. tvOS : `xcodebuild … -scheme KadreUikitDriverTvOs -destination 'platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0' … test` → `** TEST SUCCEEDED **` — `Executed 7 tests, with 0 failures (0 unexpected) in 0.611 (0.616) seconds` + `Executed 2 tests, with 0 failures (0 unexpected) in 0.002 (0.004) seconds` ; total `Executed 9 tests, with 0 failures (0 unexpected) in 0.613 (0.622) seconds`.

## 4. Auto-revue du fix round

- La remédiation respecte la lettre ET l'esprit des contraintes ; le seul dépassement (interface + provider côté runtime) est rendu NÉCESSAIRE par l'économie du SPI (dérivation à la construction / liaison à la construction / surface inexistante avant admission) et est documenté dans les doc comments des trois symboles touchés.
- Preuves renforcées, jamais affaiblies : `isEqual` + `===` remplacent des comparaisons de chaînes rédigées (donc vides) ; le test 6 reste normatif.
- Reste ouvert (pour le contrôleur) : le test 6 exige soit un environnement où FrontBoard honore `requestSceneSessionDestruction` (appareil ? hors-XCTest ?), soit une décision explicite d'accepter le fallback « post du VRAI nom de notification » (mécanique déjà sanctionnée pour le test 8) comme forme spécifique simulateur — je ne l'ai PAS appliqué sans ruling.
- Constat de robustesse : la réutilisation du process hôte entre runs xcodebuild accumule l'état du fixture ; la fraîcheur par attach + le tearDown la rendent inoffensive (runs répétés stables : 9/10 identiques sur trois exécutions consécutives).

## 5. Ruling appliqué — testSceneDisconnectionTerminatesOnlyItsSession (commit `25b21be2`)

Le contrôleur a ACCEPTÉ le fallback « post du vrai nom de notification » pour ce test dans cet environnement. Application :
- Le test GARDE son nom et toutes ses assertions : session 2 → Terminated ; session 1 → toujours Running ; fenêtre/vue de la scène 2 encore utilisables depuis Swift ; **+ la nouvelle assertion exigée : re-attach sur la MÊME scène 2 réussit ensuite** (réservation libérée par la terminaison).
- Mécanique : tentative de destruction réelle d'abord (`destroySceneSession` app-side, errorHandler inversé borné à 5 s) ; si UIKit n'invalide pas la session sous 5 s, post de `UIScene.didDisconnectNotification` avec `object = second.scene` (identité pleinement exercée). Si un environnement honore un jour la destruction réelle, le post manuel tombe après coup sur des observers fermés (callback tardif, sans effet).
- Documentation du fallback : (a) commentaire du test citant le ruling (no-op silencieux prouvé depuis les DEUX contextes d'appel, 2026-10-11) ; (b) le présent rapport ; (c) note environnement une ligne dans `kadre/capabilities/uikit.md` (section matrice toolchain) — preuve de destruction réelle = charter manuel sur matériel, matériel phase 10.
- Rationale citée : précédent ratifié phase 0 — la NSNotification EST la frontière SDK que l'adaptateur consomme ; la garantie d'Apple qu'une vraie destruction poste cette notification est le contrat Apple, vérifiée sur matériel.

**Vérification finale (les deux familles, après ruling) :**
- iOS iPad : `TEST_RUNNER_KADRE_DRIVER_REQUEST_SECOND_SCENE=2 xcodebuild … -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0' … test` → **`** TEST SUCCEEDED **`**
  - `Executed 10 tests, with 0 failures (0 unexpected) in 10.801 (10.805) seconds` (KadreUikitAttachTests — les 10 scénarios figés)
  - `Executed 2 tests, with 0 failures (0 unexpected) in 0.001 (0.002) seconds` (phase 0) — total `Executed 12 tests, with 0 failures (0 unexpected) in 10.803 (10.808) seconds`
- tvOS : `xcodebuild … -destination 'platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0' … test` → **`** TEST SUCCEEDED **`**
  - `Executed 7 tests, with 0 failures (0 unexpected) in 0.573 (0.577) seconds` (KadreUikitAttachTests — les 7 scénarios partagés)
  - `Executed 2 tests, with 0 failures (0 unexpected) in 0.002 (0.004) seconds` (phase 0) — total `Executed 9 tests, with 0 failures (0 unexpected) in 0.575 (0.584) seconds`

**Statut final du fix round 1 : DONE.** Les noms figés des 10 scénarios (iOS) / 7 (tvOS) sont des preuves de contrat vertes, prêtes pour Task 6.
