# Task 5 — `KadreHostViewController` (l'attach exactement-une-fois, les fermetures)

Statut : **DONE** · branche `zcode/sess-56645e3f` · commit `9c55897c` — `feat(uikit): deliver the kadre host view controller with single attach semantics`

---

## 1. Ce qui a été livré

| Fichier | Contenu |
|---|---|
| `kadre/platform/uikit/swift/Sources/KadreHost/KadreHostViewController.swift` | **nouveau** — `public final class KadreHostViewController: UIViewController` : `init(applicationFactory:policy: = .default)`, `kadreSession` public private(set), `close()` idempotent, `deinit` fermant, machine d'attach exactement-une-fois (waiting → retryArmed → settled), routage de notification `UIScene.didDisconnectNotification` filtré par scène, réessai one-shot sur `UIScene.didActivateNotification`, diagnostics internes (`attachAttempts`, `lastAttachFailureDescription`) + la sous-classe privée `KadreHostContentView` (le hook `didMoveToWindow` — voir §2.1) |
| `kadre/consumers/swift/consumer-app/tests/Shared/KadreHostViewControllerTests.swift` | **nouveau** — 7 tests XCTest (async, `@MainActor`) couvrant les points (a)–(g) du brief, avec antichambre de session de lancement, filet `tearDown` (miroir `KadreUikitAttachTests.tearDown`) et sentinelle de fraîcheur `runCount` |
| `kadre/consumers/swift/consumer-kmp/src/commonMain/kotlin/org/graphiks/kadre/consumer/HeldApplicationFactory.kt` | **nouveau** — le registre `ConsumerFixture` (public object, exporté au header : `runCount`, `primarySurface`, `record`) + `HeldApplicationFactory` : l'application TENUE du consumer (capture de `primarySurface.value` au run puis `awaitCancellation()` — la session ne s'arrête que lorsque le host la ferme). Justification : la self-stop de `ConsumerApplication` (~500 ms) arrêterait la session PENDANT les assertions (retrait temporaire, re-add, preuve de surface) |
| `kadre/consumers/swift/consumer-app/KadreSwiftConsumer.xcodeproj/project.pbxproj` | régénéré par xcodegen (+12 lignes : les 2 nouveaux fichiers Swift) — convention « .xcodeproj généré commité » |

Aucune modification de la DISTRIBUTION Kotlin (le framework KadreUikit.xcframework est inchangé) ; le module applicatif consumer (consumer-kmp) gagne ses propres types de fixture.

---

## 2. Les sémantiques d'attach — le moment valide (file:line)

Toutes les citations sont `KadreHostViewController.swift` (le fichier est le livrable ; lignes du commit `9c55897c`).

### 2.1 Le hook : le `didMoveToWindow` de LA VIEW (l.103–113 + l.241–256)

**Décision documentée** : `UIViewController` n'expose AUCUN callback de mouvement en fenêtre (`didMoveToWindow` est une méthode UIView ; les apparences `viewWillAppear` ne sont pas propagées à un VC dont la view est seulement sous-vue d'une UIWindow). Le hook vit donc sur la vue elle-même : `loadView` (l.103) installe `KadreHostContentView` — une sous-classe UIView PRIVÉE dont L'UNIQUE override est `didMoveToWindow` (l.250), qui relaie à `attemptAttachAtValidMoment()` (l.117). Les propriétés §5 tiennent : c'est LA view du controller (même instance — c'est elle que la session reçoit comme surfaceView, preuve (f)), aucun layout, aucun dessin, jamais remplacée. L'écart au mot « plain UIView » du croquis est un écart de MOYEN, pas de contrat (voir §8.5).

### 2.2 Le moment valide (l.117–123)

`attemptAttachAtValidMoment` : guard `case .waiting` (jamais tenté/plus de budget) ; guard `view.window` ET `window.windowScene` non-nil (les DEUX conditions du verbatim §5 « lorsque sa view appartient à une UIWindow et une UIWindowScene »). Fenêtre sans scène ou view hors hiérarchie = PAS le moment : rien n'est consommé, le prochain `didMoveToWindow` reviendra. La validation `surfaceView.window == window` de `KadreIos.attach` (KadreIos.kt l.111–114) est vraie PAR CONSTRUCTION : la fenêtre passée est `view.window` elle-même.

### 2.3 L'attach réel et le retry-one-shot-on-activation (l.128–159, 161–199)

`performAttach(scene:window:isFinalAttempt:)` (l.128) : incrémente `attachAttempts` (compteur d'appels RÉELS à `KadreIosHost.attach`) ; délégation §5 avec `surfaceView: view` (LA view du controller, l.137) ; succès → `kadreSession = handle`, `attachState = .settled`, `connectedScene = scene`, observer de disconnect posé (l.142–147).

Sur `KadreError` :
- `invalidRequest(field: "window")` ET pas encore de réessai consommé (l.151–158) : la scène n'était pas encore valide (`windowScene.session == null` ou `activationState == Unattached` — les validations KadreIos.kt l.108–110 ; l'iPadOS 27 laisse la scène `Unattached` au `willConnectTo`, finding phase 1). **UN** réessai est armé sur `UIScene.didActivateNotification` (l.161–175) : `attachState = .retryArmed(scene:)`, observer one-shot (token retiré À LA PREMIÈRE livraison, l.167–170). À la livraison (`fireActivationRetry`, l.177) : `attachState = .settled` D'ABORD — le réessai est le DERNIER essai quoi qu'il réponde ; double vérification `isEqual` de la scène notifiée (l.186–188, défense en profondeur miroir `UIKitLifecycleSource.observe` — `===` ne pont pas l'identité Obj-C, preuve O3 phase 0) ; puis relecture de `view.window`/`windowScene` au moment du feu (l.190) et `performAttach(..., isFinalAttempt: true)`.
- **Tout autre refus est DÉFINITIF** (l.158–159) : notamment `AlreadyInUse(Host)` (la scène porte déjà une session — le registre de scène, phase 1). Le controller n'insiste jamais : l'attach est EXACTEMENT UNE FOIS à vie, un re-add à une hiérarchie (avant OU après close) ne ré-attache pas (`attemptAttachAtValidMoment` guard `.waiting`, l.118).

**Sémantique documentée** : l'attach a lieu au PREMIER MOMENT VALIDE — jamais avant (guard fenêtre+scène), jamais deux fois (machine à états `waiting → retryArmed → settled`, l.54–62) ; le seul chemin de réessai est l'activation de la scène après un premier essai refusé précisément parce que la scène n'y était pas prête (pattern driver-proven : le premier `sceneDidBecomeActive` est le premier moment qui passe — miroir `ConsumerSceneDelegate.swift` l.10–16).

### 2.4 Ce que les tests NE couvrent PAS du retry (honnêteté)

Le chemin `retryArmed` n'est pas déclenchable en test : la scène de l'app hôte est déjà `ForegroundActive` quand les tests tournent, et créer une scène neuve resterait `ForegroundInactive` (le driver phase 1 l'a prouvé — `SceneFixture.waitUntilReady(requireActivation:)`). Le test (a) prouve le chemin nominal (attach immédiat, compteur 1) ; le chemin de réessai est une sûreté de production documentée, morte dans les deux suites.

---

## 3. Le routage des notifications (file:line)

`installDisconnectObserver(for:)` (l.201–219) — le miroir Swift d'`UIKitLifecycleSource` (phase 1) :

1. **UN observer, filtré PAR TOKEN** : `addObserver(forName: UIScene.didDisconnectNotification, object: scene, queue: .main)` — le centre ne livre que les notifications `object == la scène attachée` (l.203–208) ; `connectedScene` n'est posé qu'après un attach réussi (l.147).
2. **Défense en profondeur** dans le block : `(notification.object as? NSObjectProtocol)?.isEqual(connected)` (l.211–213) — même vérification que `UIKitLifecycleSource.observe` (Kotlin l.48) : le `object` d'une NSNotification est `Any?`, et `===` ne pont pas l'identité Obj-C (preuve O3, phase 0).
3. **L'action** : `self.close()` (l.215) — « sceneDidDisconnect … ferme la session » (§5 l.140). Le close du handle est idempotent : une disconnect pendant un close explicite ne fait qu'un arrêt.
4. **Le retrait temporaire ne passe JAMAIS ici** : retirer une view de sa fenêtre ne poste pas `didDisconnect` (commentaire l.216–217 ; prouvé par le test (g)).
5. **Les tokens sont retirés au deinit PAR TOKEN** (l.229–239) : les observers par block sont détenus par le centre PAR TOKEN — `removeObserver(self)` ne les retirerait pas ; le block capture self FAIBLE (l.209) pour ne jamais repousser le deinit qu'il doit servir. Le token du réessai d'activation est one-shot (retiré à la première livraison, l.167–170) et aussi retiré au deinit s'il restait armé.
6. Livraison sur la file principale (`queue: .main`) — toute vie du controller est principale (callbacks de vue, notifications de scène) ; le `precondition` main-thread de `KadreIosHost.attach` est satisfait par construction (commentaire d'entête l.34–38).

---

## 4. La preuve d'identité de la view (mécanisme)

**La chaîne** (test (f) `testControllerViewIsTheAttachedSurfaceView`) :

1. **La capture côté Kotlin** — le scope Kadre n'arrive qu'au `run` de l'application (KOTLIN) ; la façade Swift ne peut pas le lire. `HeldApplicationFactory.kt` : `HeldApplication.run` capture `ConsumerFixture.record(primarySurface.value)` AU DÉBUT du run (l.74–77) — la surface primaire est présente à ce point (l'admission de la fenêtre primaire précède Running, finding phase 1 `hasApplicationFacts`). `ConsumerFixture` est un `public object` (exporté au header, préfixe `KadreUikit` — vérifié : `KadreUikitConsumerFixture` avec `runCount: int32_t`, `primarySurface: id<KadreUikitHostSurface> _Nullable`, `record(surface:)` dans les headers des DEUX slices) ; single-thread par construction (écriture au dispatcher Main de la session = le thread principal UIKit ; lectures des XCTest sur le même fil — documenté dans le KDoc).
2. **La fraîcheur** — `runCount` s'incrémente À CHAQUE run ; le test note `runsBefore` AVANT de créer le controller et n'accepte qu'un compteur STRICTEMENT supérieur avec surface non-null (le patron `waitUntilApplicationFacts(minimumRuns:)` du driver) : jamais un reliquat d'une session précédente.
3. **La comparaison** — `KadreUikitSurfaceDriver.shared.leaseProbe(surface:expectedView:)` (la sonde phase 2, distribution) : le bail natif `withUIKitView` de LA surface primaire de session remet la vue enregistrée dans le port de lease — c'est-à-dire EXACTEMENT la surfaceView passée à l'attach (`UIKitWindowRuntime` construit port et surface sur la MÊME `view`, l.59–68/167) — et la sonde la compare par `isEqual` à la view du controller (`grantedViewMatches`). `isEqual` et non `===` : `===` ne pont pas l'identité Obj-C à la frontière K/N → Swift (preuve O3 phase 0, citée par `KadreUikitLeaseProbe` l.22–27).
4. **Ce que ça tue** : un controller qui passerait une vue différente, une vue wrapper ou une copie à l'attach — le bail rendrait une AUTRE vue et `grantedViewMatches` serait faux.

NB : l'égalité par `KadreSessionHandle.id` est VIDE DE SENS (le `SessionId.toString()` Kotlin est rédigé constant « SessionId(<redacted>) », foundation Identity.kt l.15) — les preuves d'identité de session des tests passent par `===` sur les handles et par le compteur `attachAttempts`, jamais par les ids (commentaire dans le test (b)).

---

## 5. Les tests et la mutation que chacun tue

Tous dans `KadreHostViewControllerTests.swift` (tests/Shared — tournent sur les DEUX familles), sur sessions RÉELLES (attach controller-driven sur fenêtre neuve de la scène de l'app hôte, factory `HeldApplicationFactory` — la session ne s'arrête jamais seule) :

| # | Test | Ce qu'il prouve | La mutation tuée |
|---|---|---|---|
| a | `testAttachesExactlyOnceWhenTheViewJoinsAWindowWithAScene` | ajout à une fenêtre (scène Active) → `kadreSession` non-nil, `attachAttempts == 1`, la session atteint Running | ne jamais attacher ; attacher deux fois (deux hooks) ; une session morte-née |
| b | `testRemoveAndReAddDoesNotAttachAgain` | removeFromSuperview + re-add → compteur toujours 1, `kadreSession ===` même handle, session vivante | le re-attach à chaque `didMoveToWindow` (compteur 2+ ou handle remplacé) |
| c | `testExplicitCloseTerminatesAndReAddDoesNotReattach` | `close()` deux fois (idempotent) → `.terminated(.stopped(.hostRequested))` (la raison ne peut être QUE hostRequested : l'app tenue ne s'arrête jamais seule) ; re-add APRÈS close → compteur 1, même handle, toujours terminé | un close() no-op ; un re-attach après close ; un close non-idempotent (le double appel) |
| d | `testSceneDidDisconnectNotificationClosesTheSession` | post synthétique `UIScene.didDisconnectNotification` (object = la scène attachée — patron prouvé phase 1 `KadreUikitAttachTests.testDisconnectDuringStop…`) → session terminée, compteur inchangé ; close après coup idempotent | l'observer absent ou mal routé (un disconnect ignoré laisserait la session tenir la réservation de scène — cascade `AlreadyInUse` sur le consumer) |
| e | `testDeinitClosesTheSession` | le controller lâché (le registre du tearDown relâché exprès dans le scope) se désalloue (weak tracking `weakController == nil`) et la session capturée AVANT atteint `terminated` | un deinit qui ne ferme pas (session orpheline Running — fuite de réservation) ; un retain-cycle observer→self qui empêcherait le deinit (le weak tracking ne serait jamais nil) |
| f | `testControllerViewIsTheAttachedSurfaceView` | le bail natif de la surface primaire remet une vue `isEqual` à LA view du controller (§4) | une surfaceView différente/wrapper/copiée passée à l'attach |
| g | `testTemporaryRemovalDoesNotCloseTheSession` | Running → removeFromSuperview → 150 ms pompées → session non-nil, toujours `.running` ; re-add → même handle, compteur 1, toujours Running | une fermeture sur le retrait (roadmap l.227 — « un retrait temporaire ne ferme pas la scène » violé) |

**Filets d'isolement** (aucun affaiblissement d'assertion) :
- antichambre dans `makeAttachedController` : la session de LANCEMENT de l'app (self-stop ~500 ms) peut tenir la réservation quand la suite tourne seule (`-only-testing`) — l'attach controller-driven refusant `AlreadyInUse` de façon DÉFINITIVE, l'attente bornée (20 s, poll MainActor) est la seule place honnête. Capturé en vrai : le premier run `-only-testing` a échoué exactement là (`failure=alreadyInUse(resource: .host)`) — le fix est l'antichambre, pas un retry du controller.
- `settle` + `tearDown` async (miroir `KadreUikitAttachTests.tearDown`) : close + attente bornée de la terminaison — la réservation est libérée AVANT le test suivant (ordre prouvé : `SessionRuntime.finish` publie `Terminated` l.413 AVANT `onTerminated` → `ownership.release()` l.415 ; observer la publication depuis le main garantit la release faite). Le test fautif reste le seul à porter sa défaillance.
- le test (e) retire le controller du registre du tearDown DANS le scope (sinon CE registre — référence forte — empêcherait la désallocation que le test mesure).

---

## 6. Commandes exécutées et issues

| # | Commande (cwd worktree sauf mention) | Issue |
|---|---|---|
| 1 | `./gradlew -p kadre/consumers/swift -PkadreRepository=$PWD/build/kadre-contract-repository -PkadreVersion=1.0.0 linkConsumerSimulatorFrameworks` | OK — `BUILD SUCCESSFUL in 35s` ; les deux frameworks statiques consumer rebuildés avec `ConsumerFixture`/`HeldApplicationFactory` |
| 2 | grep headers générés (`iosSimulatorArm64` + `tvosSimulatorArm64` …/KadreUikit.framework/Headers/KadreUikit.h) | OK — `KadreUikitConsumerFixture` (`runCount`, `primarySurface`, `recordSurface:` swift_name `record(surface:)`) et `KadreUikitHeldApplicationFactory` exportés sur les DEUX slices |
| 3 | `xcodegen generate` (consumer-app) | OK — nouveau test référencé |
| 4 | `xcodebuild … -scheme KadreConsumerIos -destination 'platform=iOS Simulator,name=iPad Pro 11-inch (M5),OS=27.0' CODE_SIGNING_ALLOWED=NO build-for-testing` | **ROUGE attendu (TDD)** — `exit=65` : `KadreHostViewControllerTests.swift:67: error: cannot find type 'KadreHostViewController' in scope` (+ un vrai bug de test capturé au passage : `invalid redeclaration of 'isTerminated'` — l'extension de lisibilité existait DÉJÀ en interne dans `KadreSessionHandleTests.swift` ; doublon supprimé, l'interne réutilisé) |
| 5 | (après implémentation) idem | ÉCHEC — `KadreHostViewController.swift:106: method does not override any method from its superclass` : `didMoveToWindow` est un hook UIView, pas UIViewController → le hook déplacé sur la sous-classe privée de la view (§2.1) |
| 6 | idem | ÉCHEC — `KadreHostViewControllerTests.swift:249: tuples have a different number of elements` (déstructuration 2-vs-3 du tuple du helper) → corrigé |
| 7 | idem | **`exit=0` — BUILD SUCCEEDED** (iOS) |
| 8 | `xcodebuild … -only-testing:KadreConsumerIosTests/KadreHostViewControllerTests test` | **6/7 puis fix antichambre** — le premier run : `testAttachesExactlyOnce…` échoue `failure=alreadyInUse(resource: .host)` (la session de lancement de l'app tenait encore la réservation — course `-only-testing`, §5) ; l'antichambre ajoutée au helper |
| 9 | idem | **`exit=0` — Executed 7 tests, with 0 failures (iOS)**, `** TEST SUCCEEDED **` |
| 10 | `xcodebuild … -scheme KadreConsumerTvOs -destination 'platform=tvOS Simulator,name=Apple TV 4K (3rd generation),OS=27.0' … build-for-testing` puis `-only-testing:KadreConsumerTvOsTests/KadreHostViewControllerTests test` | **`exit=0` ×2 — Executed 7 tests, with 0 failures (tvOS)**, `** TEST SUCCEEDED **` |
| 11 | `./gradlew -p kadre/consumers/swift -PkadreRepository=$PWD/build/kadre-contract-repository -PkadreVersion=1.0.0 iosSimulatorTests tvosSimulatorTests` (racine worktree) | **`BUILD SUCCESSFUL in 21s`** — `** TEST SUCCEEDED **` ×2 ; **iOS : « Executed 43 tests, with 0 failures (0 unexpected) »** (1 ConsumerAttachTests + 29 KadreHostMappingTests + 6 KadreSessionHandleTests + 7 KadreHostViewControllerTests) **; tvOS : « Executed 43 tests, with 0 failures (0 unexpected) »** ; zéro « is implemented in both » (garde anti-duplication des deux lancements) |

Tous les xcodebuild/gradle longs : exécution en tâche de fond + attentes bornées (TaskOutput, timeout ≤ 600 s) — la discipline agrégateur est tenue.

Régressions : les suites Tasks 2–4 (ConsumerAttachTests, KadreHostMappingTests, KadreSessionHandleTests) restent vertes dans l'agrégateur des deux destinations (36 → 43 par famille, +7).

---

## 7. Auto-audit de la surface publique (grille négative §9)

`grep public` sur `KadreHostViewController.swift` — la surface publique complète du fichier :

- `public final class KadreHostViewController: UIViewController` ;
- `public init(applicationFactory: any KadreUikit.KadreApplicationFactory, policy: KadrePolicyProfile = .default)` — la coutume d'injection Kotlin (même posture que `KadreIosHost.attach`, Task 4 revue) + le profil fermé §5 ;
- `public private(set) var kadreSession: KadreSessionHandle?` — le billet §5, non-settable ;
- `public func close()` ;
- `override public func loadView()` (override du SDK, pas une API nouvelle).

**Aucun autre `public`** : `attachAttempts` et `lastAttachFailureDescription` sont `private(set)` internes (lisibles des tests via `@testable` uniquement) ; la machine d'attach, les tokens et `KadreHostContentView` sont `private`/fichier.

Grep des tokens interdits : `CoroutineScope` **0**, `Throwable` **0**, `Continuation` **0**, `Flow` **0**, `Any`/`AnyObject` **0** — aucune fuite du modèle Kotlin dans le public ; la seule référence Kotlin du public est le paramètre factory (comme l'attach Task 4).

---

## 8. Auto-revue (relecture à froid)

1. **Course close × disconnect** : `close()` délègue au handle (idempotent, verrouillé) ; l'observer de disconnect reste posé après close mais ne fait qu'un second close — no-op prouvé (test c appelle close deux fois, test d un close après la fermeture par notification). Pas de double-terminaison possible côté Kotlin (`requestTermination` guard `finished`, SessionRuntime.kt l.360).
2. **Course retrait × attach** : le hook est `didMoveToWindow` (main) et la machine d'états est lue/écrite sans autre fil ; un retrait SYNCHRONE suivi d'un re-add (removeFromSuperview + addSubview dans la même passe, tests b et g) livre deux `didMoveToWindow` — le premier avec window=nil (guard le passe), le second trouve `.settled` : rien.
3. **Le retry armé ne peut pas boucler** : `fireActivationRetry` pose `.settled` AVANT `performAttach(isFinalAttempt: true)` et le paramètre interdit le re-arm — même un second `InvalidRequest("window")` ne ré-arme pas. Le token est one-shot (retiré à la première livraison) et retiré au deinit s'il restait armé.
4. **Le block d'observer ne retient pas self** (faible) — le deinit du test (e) est arrivé à terme sur les deux destinations ; les tokens sont retirés PAR TOKEN (les observers par block ne répondent pas à `removeObserver(self)`).
5. **Écart assumé au croquis** : le brief disait « loadView crée une plain UIView ; hook didMoveToWindow ». UIViewController n'a pas ce hook — il vit sur la view (sous-classe privée à une seule override). Le contrat §5 (cette view = surfaceView, jamais remplacée, aucun layout) est intact et PROUVÉ (test f). Signalé ici comme écart de moyen documenté, pas de sémantique.
6. **`AlreadyInUse` non réessayé par le controller** : choix de contrat — l'attach est exactement une fois et le controller n'insiste pas sur un conflit de ressource (c'est au consommateur de gérer l'exclusivité de scène). La conséquence en test (la course de lancement `-only-testing`) est traitée par l'antichambre du fixture, pas par un affaiblissement du controller.
7. **Ce que je n'ai PAS fait (et pourquoi)** : pas d'extension iOS multi-scènes (le brief la conditionnait à un besoin — le routage par scène suit `object:` + isEqual, et aucune scène 2 n'existe dans l'app consumer) ; pas de capture dans `ConsumerApplication` (la self-stop la rendrait inutilisable pour (b)/(g) — la capture vit dans `HeldApplication`, même module, même registre) ; pas de test du chemin `retryArmed` (indéclenchable en test — §2.4) ; pas de modification de la distribution Kotlin.
8. **Point d'attention honnête** : `ConsumerFixture.primarySurface` est un slot « dernier run » — deux sessions tenues CONCURRENTES se brouilleraient les faits ; impossible dans ce process (une session par scène, chaque test règle la sienne avant la suivante), et la sentinelle `runCount` rend toute lecture périmée détectable plutôt que silencieuse.

---

## 9. Vérification finale

- iOS simulateur (iPad Pro 11-inch (M5), OS 27.0) : **Executed 43 tests, 0 failures** — `** TEST SUCCEEDED **`
- tvOS simulateur (Apple TV 4K (3rd generation), OS=27.0) : **Executed 43 tests, 0 failures** — `** TEST SUCCEEDED **`
- Rouge TDD capturé (type §5 absent), un défaut de test réel tué avant le vert (doublon `isTerminated`), un écart d'API corrigé par le compilateur (`didMoveToWindow` n'est pas un hook UIViewController), une course d'isolement capturée en vrai et fixée par l'antichambre (sans toucher au contrat du controller).
- Grille négative §9 : passée (grep §7). Gardes anti-duplication de l'agrégateur : passées (zéro « is implemented in both »).
