# Registre d'implémentation — adapter UIKit (`org.graphiks.kadre.platform.uikit`, phase 1)

**Mandat.** `BACKEND-CAPABILITIES.md` §8 impose à chaque adapter de produire, avec son
implémentation, un fichier public `capabilities/<adapter>.md` à sept colonnes : `feature`,
`target`, `minimum déclaré`, `compile gate`, `runtime gate`, `état absent` et `tests`. Le
format est celui de `kadre/capabilities/web.md` ; la première édition (phase 0) ne portait
qu'une ligne — la preuve O3 qu'un hôte UIKit réel peut transmettre des objets
`UIWindow`/`UIView` à du code Kotlin/Native et que celui-ci en publie une lecture immédiate,
copiée et honnête. L'édition phase 1 a ajouté la livraison d'`KadreIos.attach` :
une session par `UIWindowScene`, le cycle de vie conduit par les notifications de scène
réelles, le multi-scène iPadOS et l'identité de la fenêtre primaire — chacune portée par sa
ligne, avec ses sentinelles. L'édition phase 3 ajoute la façade Swift `KadreHost`
(contrat §5 d'INTEROP-EXPORTS), le `KadreHostViewController`, la distribution XCFramework
statique et l'audit de l'en-tête framework (§3 ci-dessous) — l'inventaire qui clôt la
divergence F1 de la phase 2 par suppression (`@HiddenFromObjC` sur `withUIKitView`).
L'édition phase 4 ajoute la demande de fenêtre `requestWindow` : le refus sans provider
(`Rejected(Unsupported(RequestWindow))` avant toute réservation — le chemin tvOS et tout
app mono-fenêtre, BCK-020) et la corrélation multi-scènes iPadOS via le provider app-owned
(`OpenedInNewSession` d'une session enfant COMPLÈTE et indépendante, BCK-021) — les
fenêtres enfants ne sont jamais des `Window` Kadre : `Window.apply`/`attention`/`close`
restent hors périmètre. L'édition phase 5 ajoute l'ANNONCE des sources d'entrée : le
recognizer de touch Kadre-owned additif (BCK-022), le fait pointeur par famille (survol
iOS seul), le clavier physique par la route `GCKeyboard.coalescedKeyboard` et l'ensemble
EXACT des gestes par famille (BCK-023) — quatre faits publiés dans UN agrégat
`InputObservationChanged` par attach, révoqué au teardown ; les LIVRAISONS réelles
(touch, touches clavier, gestes, survol) restent des charters manuels — aucune n'est
synthétisable in-process honnêtement (ruling pré-vol), les compteurs du driver le prouvent
(`touchEventCount == 0`, `keyboardEventCount == 0`, `gestureStimulusCount == 0`).

**Portée de lecture.** Deux targets Kotlin partagent la sonde (`KadreUikitProbe`,
`uikitMain`) : `iosSimulatorArm64` et `tvosSimulatorArm64`. La preuve O3 court dans des
applications hôte Xcode réelles (`kadre/contracts/driver/uikit/KadreUikitDriver.xcodeproj`,
schemes `KadreUikitDriverIos` et `KadreUikitDriverTvOs`) qui lient le framework
`KadreUikit` produit par `:kadre:platform:uikit` et exécutent `KadreUikitDriverTests`
(phase 0) et `KadreUikitAttachTests` (phase 1, extension iOS-only
`tests/Ios/KadreUikitAttachIosTests.swift` compilée DANS la même classe XCTest) sur les
simulateurs. La matrice toolchain **relevée** de cette édition — ce sont
les versions qui ont produit les documents de preuve `BCK-012` à `WIN-009`, pas des minima
au-delà d'eux-mêmes :

- Kotlin **2.4.20** (Kotlin/Native), Gradle **9.8.0** ;
- Xcode **27.0 (27A266a)**, `xcodebuild` + `xcresulttool` du même Xcode ;
- simulateurs réellement exercés : **iPhone 17 / iOS 27.0 (build 24A434)** (phase 0),
  **iPad Pro 11-inch (M5) / iOS 27.0** (phase 1 — le multi-scène est refusé par l'idiome
  téléphone) et **Apple TV 4K (3rd generation) / tvOS 27.0 (build 24J360)** (relevés
  `devices` des bundles `build/xcresult/{ios,tvos}.xcresult`) ;
- planchers **déclarés** de l'adapter : **iOS 16.0 / tvOS 16.0** — déclarés, pas exercés :
  aucune passe de preuve ne court sous 27.0 à ce stade (voir la colonne `minimum déclaré`).

**Règle d'identité du pont Obj-C (relevé Task 5, 2026-10-10).** `===` ne traverse PAS le
pont Kotlin/Obj-C : pour la même instance `UIWindow` reçue du hôte, le pont produit deux
wrappers Kotlin distincts (`===` faux) alors que `hashCode` coïncide. Toute comparaison
d'identité Objective-C côté hôte passe donc par `NSObject.isEqual` — c'est la lecture
qu'implémente `KadreUikitProbe.observe` (`view.window.isEqual(window)`), et c'est la
règle que la sentinelle de `BCK-012` protège (`uikit-driver-window-membership-sentinel`).

**Note environnement simulateur (relevé Task 5 fix round, 2026-10-11).** Sous XCTest,
`requestSceneSessionDestruction` est un no-op silencieux sur simulateur (errorHandler jamais
appelé, session jamais invalidée — prouvé depuis le bundle de test ET le code de l'app ;
`requestSceneSessionActivation` n'est honoré par FrontBoard que depuis le lancement de l'app) :
la preuve de disconnect du driver passe par le post du vrai nom de notification
(`uikit-disconnect-terminates`, fallback ruling contrôleur). La preuve de DESTRUCTION réelle
par FrontBoard est un charter manuel sur matériel — matériel de la phase 10.

**Note rotation programmatique (relevé Task 5, 2026-10-11 — portée par BCK-016).**
`UIScene.requestGeometryUpdate(.iOS(interfaceOrientations:))` est REFUSÉ par ce runtime
simulateur quel que soit le levier — TROIS refus capturés, un par configuration :
(1) windowing libre par défaut → `UISceneErrorDomain 101` « The current windowing mode does
not allow for programmatic changes to interface orientation » ; (2) avec la clé TN3192
`UIPreferredDefaultWindowingStyle=UIWindowSceneWindowingStyleFullscreen` dans l'Info.plist
construit (vérifié présent) → même 101 ; (3) avec `UIRequiresFullScreen=true` →
`BSActionErrorDomain « response-not-possible »`. Le stimulus retenu est le resize à
changement d'aspect (240×320) — DÉGRADÉ, tracé `KADRE-DRIVER … DEGRADED` à chaque run
(NSLog + DriverTrace), jamais présenté comme une rotation réelle. La branche de rotation
RÉELLE reste codée dans le test (fidélité exacte : largeur tournée == hauteur portrait,
échange vérifié contre le portrait de référence) et s'exécutera sur un runtime qui accepte
`requestGeometryUpdate` — charter manuel / runtime futur. Le kill de la sentinelle
`uikit-rotation-metrics-faithful` est PORTÉ PAR LA BRANCHE DÉGRADÉE (fidélité géométrique :
publié == vivant après le changement de géométrie), jamais par une rotation réelle.

## 1. Lecture des colonnes

- **feature** — la conduite livrée, nommée comme le fait le registre Web : le membre
  public ou, à ce stade, la sonde de conduite du driver.
- **target** — les deux targets simulateurs du framework (`iosSimulatorArm64`,
  `tvosSimulatorArm64`). Aucun target device n'est déclaré : la phase 0 ne prouve que la
  conduite sur simulateur, un target device exigerait une signature que cette phase ne
  possède pas.
- **minimum déclaré** — le plancher que le code déclare (iOS 16.0 / tvOS 16.0,
  `kadre/platform/uikit/build.gradle.kts`), la toolchain ci-dessus étant ce qui a
  réellement exercé la preuve.
- **compile gate** — le symbole dont la compilation dépend : ici aucun symbole
  conditionnel ; le framework compile pour les quatre targets natifs et la sonde ne lit
  que des membres toujours présents (`UIView.window`, `UIWindow.windowScene`,
  `UIView.bounds`, `UITraitCollection.displayScale`).
- **runtime gate** — ce dont dépend la *publication* : un run xcodebuild sur le
  simulateur de chaque famille (les preuves `BCK-012` à `WIN-009` puis `BCK-015`/`BCK-016`,
  `BCK-020`/`BCK-021` et `BCK-022`/`BCK-023`),
  jamais une permission hôte.
- **état absent** — ce qui n'est pas livré à ce stade, dit intégralement : depuis la
  phase 1, l'attach (`KadreIos.attach`), le cycle de vie par scène, le multi-scène iPadOS
  et l'identité de la fenêtre primaire sont livrés ; depuis la phase 2, les observations
  dynamiques de surface (métriques, thème, visibilité — re-lectures sur déclencheurs SDK
  réels), le redraw coalescé, l'`apply` fermé, le bail d'accès natif `withUIKitView` et la
  rotation iPadOS (stimulus dégradé, cf. note ci-dessus) sont livrés ; depuis la phase 3,
  la façade Swift `KadreHost` (contrat §5), le `KadreHostViewController`, la distribution
  XCFramework statique et l'audit de l'en-tête framework sont livrés — et l'appel Swift du
  bail RESTE ABSENT par décision : `withUIKitView` est Kotlin-only strict, masqué des
  exports Obj-C/Swift (`@HiddenFromObjC`, audit §3) ; depuis la phase 4, la demande de
  fenêtre `requestWindow` est livrée — refus pur sans provider (BCK-020) et corrélation
  multi-scènes via provider app-owned (BCK-021) — mais une fenêtre enfant n'est JAMAIS une
  `Window` Kadre : `Window.apply`/`attention`/`close` et toute opération sur l'enfant
  restent hors périmètre (l'enfant est sa propre session UIKit complète) ; depuis la
  phase 5, les sources d'entrée sont ANNONCÉES — le recognizer de touch additif, le fait
  pointeur par famille (survol iOS seul), le clavier physique (`GCKeyboard.coalescedKeyboard`)
  et l'ensemble exact des gestes par famille, dans un agrégat `InputObservationChanged`
  publié après l'installation et révoqué au teardown (BCK-022/BCK-023) — mais les
  LIVRAISONS réelles restent des charters manuels : un touch `UITouch`, une touche
  `GCKeyCode`, un geste ou un survol ne sont pas synthétisables in-process honnêtement
  (ruling pré-vol ; les compteurs driver prouvent les zéros — `touchEventCount == 0`,
  `keyboardEventCount == 0`, `gestureStimulusCount == 0`) ; restent absents
  sur ce target IME (texte = phase 6), drop, displays, devices, capture.
  Le CHANGEMENT D'ÉCHELLE d'écran (`displayScale`) et le
  mode-change tvOS (`UIScreenModeDidChangeNotification` — observé mais sans stimulus
  programmatique sur simulateur) n'ont pas de preuve sur simulateur : charter manuel
  (matériel de la phase 10). Aucun module applicatif Kadre ne référence
  `:kadre:platform:uikit` en dehors de la preuve.
- **tests** — la classe Xcode qui porte la ligne (`KadreUikitDriverTests` phase 0,
  `KadreUikitAttachTests` phase 1, `KadreUikitSurfaceTests` phase 2 — extension iOS-only
  `tests/Ios/KadreUikitSurfaceIosTests.swift` compilée DANS la même classe XCTest pour la
  rotation, `KadreUikitWindowRequestTests` phase 4 — extension iOS-only
  `tests/Ios/KadreUikitWindowRequestIosTests.swift` compilée DANS la même classe XCTest
  pour les scénarios multi-scènes, `KadreUikitInputTests` phase 5 — classe partagée, la
  vérité par famille est assertée DANS les tests), exécutée par
  `./gradlew :kadre:contracts:driver:uikit:simulatorTests` et consommée par le validateur
  via l'export JUnit (`tools/xctest_to_junit.py`).

## 2. Registre

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| observation de conduite (sonde driver) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-012 (2 simulateurs) | tout le reste : non livré (input, IME, drop, displays, devices, capture) | `KadreUikitDriverTests` |
| `KadreIos.attach` — session par scène et cycle de vie | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-013 (2 simulateurs) | input, IME, drop, displays, devices, capture : non livré | `KadreUikitAttachTests` |
| multi-scène iPadOS (une session par `UIWindowScene` supplémentaire) | iosSimulatorArm64 | iOS 16.0 (iPad) | `:kadre:platform:uikit:compileKotlin*` | BCK-014 (iPad Pro 11-inch (M5)) | tvOS mono-scène par design (DESIGN.md §15.2) ; input, IME, drop, displays, devices, capture : non livré | `KadreUikitAttachTests` (extension `tests/Ios`) |
| fenêtre primaire et identité de surface | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | WIN-009 (2 simulateurs) | toute autre fenêtre/surface : non livré (le host impose la primaire) ; input, IME, drop, displays, devices, capture : non livré | `KadreUikitAttachTests` |
| observations dynamiques de surface, redraw coalescé et `apply` fermé | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-015 (2 simulateurs) | occlusion (restera `Visible`), changement d'échelle d'écran et mode-change tvOS : pas de stimulus programmatique sur simulateur (charter manuel) ; IME, drop, displays, devices, capture : non livré | `KadreUikitSurfaceTests` |
| bail d'accès natif `withUIKitView` (platformAccess) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-015 (2 simulateurs) | la UIView n'est valide qu'au retour du block (jamais retenue) ; après close : `Unsupported(PlatformSurfaceAccess)` déterministe ; IME, drop, displays, devices, capture : non livré | `KadreUikitSurfaceTests` |
| rotation iPadOS de la surface | iosSimulatorArm64 | iOS 16.0 (iPad) | `:kadre:platform:uikit:compileKotlin*` | BCK-016 (iPad Pro 11-inch (M5)) | rotation RÉELLE non exercée : `requestGeometryUpdate` refusé 3 voies sur ce runtime — kill porté par la branche DÉGRADÉE (resize à changement d'aspect, fidélité géométrique publié == vivant) ; rotation réelle = runtime qui accepte `requestGeometryUpdate` (charter manuel / runtime futur) ; tvOS : hors sujet (pas de rotation) ; IME, drop, displays, devices, capture : non livré | `KadreUikitSurfaceTests` (extension `tests/Ios`) |
| façade Swift `KadreHost` (contrat INTEROP §5 : `KadreIosHost.attach`, `KadreSessionHandle`, value model fermé) + inventaire d'audit de l'en-tête (§3) | iosSimulatorArm64, tvosSimulatorArm64 (sources Swift `platform/uikit/swift/Sources/KadreHost`, compilées dans l'app consumer) | iOS 16.0 / tvOS 16.0 | compile du consumer `KadreSwiftConsumer.xcodeproj` (les symboles Kotlin traversent par `swift_name`, xcframework lié sans embed) | consumer gate (`iosSimulatorTests`/`tvosSimulatorTests` — la factory du module Kotlin applicatif traverse jusqu'à l'attach ; pré-absence de `Flow`/`Continuation`/`Throwable` dans la façade épinglée) | `withUIKitView` depuis Swift : NON promis — Kotlin-only strict (`@HiddenFromObjC`, audit phase 3, §3) ; module `integration:swiftui` : non matérialisé (le wrapper `UIViewControllerRepresentable` ~10 lignes est possédé par l'app, INTEROP §5) ; managers/flows/frames : non promis | suites consumer-app (`KadreHostMappingTests`, `KadreSessionHandleTests`, `ConsumerAttachTests`) |
| `KadreHostViewController` (attach exactement une fois quand sa view appartient à une `UIWindow`/`UIWindowScene` ; la view passée EST `self.view`) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | compile du consumer `KadreSwiftConsumer.xcodeproj` | consumer gate (`iosSimulatorTests`/`tvosSimulatorTests`) | zéro layout Kadre (promesse §5) ; re-attach après `close` hors promesse (budget d'attach unique) ; déconnexion observer seulement au `deinit` (close la laisse — bénin, weak self) | suite consumer-app (`KadreHostViewControllerTests`) |
| distribution XCFramework STATIQUE `KadreUikit.xcframework` (4 slices : `ios-arm64`, `ios-arm64-simulator`, `tvos-arm64`, `tvos-arm64-simulator` — `AvailableLibraries` du Info.plist) | iosArm64, iosSimulatorArm64, tvosArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `assembleKadreUikit{Debug,Release}XCFramework` (l'en-tête audité de §3 est celui des slices) | l'app consumer SE LANCE avec les DEUX archives statiques liées sans embed — toute duplication Obj-C au lancement (« Class … is implemented in both ») fait échouer le run | pas de publication binaire signée (artefact local de preuve, `build/` non commité) ; slices device (arm64) non exercées — simulateurs seuls ; l'en-tête ne porte aucun symbole surface-access (§3) | consumer gate (`iosSimulatorTests`/`tvosSimulatorTests`, garde `doLast` anti-duplication) |
| `requestWindow` sans provider — refus `Rejected(Unsupported(RequestWindow))` avant toute réservation, aucun effet natif (chemin tvOS et tout app mono-fenêtre) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-020 (2 simulateurs) | le chemin provider (corrélation de scène, `OpenedInNewSession`) : hors sujet sans provider (BCK-021, iOS seul) ; `Window.apply`/`attention`/`close` : hors périmètre (une fenêtre enfant n'est jamais une `Window` Kadre) ; IME, drop, displays, devices, capture : non livré | `KadreUikitWindowRequestTests` |
| `requestWindow` corrélé multi-scènes — provider app-owned, `OpenedInNewSession` d'une session enfant COMPLÈTE et indépendante (sa propre scène/surface/lifecycle ; le close du requérant détache l'enfant du destin du requérant — l'enfant survit) | iosSimulatorArm64 | iOS 16.0 (iPad) | `:kadre:platform:uikit:compileKotlin*` | BCK-021 (iPad Pro 11-inch (M5)) | tvOS : mono-scène par design (DESIGN.md §15.2) — le refus BCK-020 y porte le contrat ; `Window.apply`/`attention`/`close` sur une fenêtre enfant : hors périmètre (l'enfant n'est jamais une `Window` Kadre) ; IME, drop, displays, devices, capture : non livré | `KadreUikitWindowRequestTests` (extension `tests/Ios`) |
| sources d'entrée touch et pointeur — recognizer de touch Kadre-owned additif (`cancelsTouchesInView == false`), fait pointeur par famille (survol iOS seul), annonce agrégée publiée après l'install et révoquée au teardown | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-022 (2 simulateurs) | la LIVRAISON réelle d'un touch (`UITouch`) : charter manuel (pas synthétisable in-process — `touchEventCount == 0` prouvé) ; le survol est iOS seul (`API_UNAVAILABLE(tvos)`, klib sans la classe — tvOS reste `pointerInstalled == false` sans faux écrit) ; IME (texte = phase 6), drop, displays, devices, capture : non livré | `KadreUikitInputTests` |
| sources d'entrée clavier et gestes — binding `GCKeyboard.coalescedKeyboard` (notifications de connexion + scan d'install, handler `keyChangedHandler`), ensemble EXACT des gestes par famille (iOS tap/pan/pinch/rotation, tvOS tap/pan), annonce agrégée publiée après l'install et révoquée au teardown | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-023 (2 simulateurs) | la LIVRAISON réelle de touches : charter manuel (`keyboardEventCount == 0`, `gestureStimulusCount == 0` prouvés) ; `LogicalKey` toujours `Unidentified(null)` et modifiers toujours vides (la route GC ne porte ni `characters` ni FlagsChanged — le texte est la route UIKeyInput, phase 6) ; le clavier intégré iPad (non-GameController) : couverture non prouvée (limite documentée) ; handler `keyChangedHandler` process-global : limite mono-consumateur documentée (une seconde session attachée prend la livraison des touches, le teardown de la première la coupe pour la suivante — les annonces peuvent alors sur-déclarer `keyboardInstalled` ; arbitrage process-wide = follow-up) ; pinch/rotation : iOS seuls (absents du klib tvOS) ; `Cancelled`/`Failed` de geste : PERDUS par politique explicite (aucun état de geste retenu — DESIGN §15.2 le documente) ; la télécommande (bout réel) : charter manuel (XCUIRemote = UI-testing) ; binding micro-gamepad (Siri Remote — `valueChangedHandler`/`controllerPausedHandler`) : non livré (suivi phase suivante) ; IME, drop, displays, devices, capture : non livré | `KadreUikitInputTests` |

Note de gate des lignes phase 3 (façade Swift, `KadreHostViewController`, distribution
XCFramework) : le gate consumer (`consumers/swift`, 2 familles) tourne localement via
l'agrégateur `kadre/consumers/swift` ; le câblage CI (job xcode-27 dédié ou job macOS
consolidé) est un follow-up enregistré (revue finale F-2).

La ligne phase 0 se lit : `KadreUikitProbe.observe(window:view:)` publie une
`KadreUikitObservation` copiée (appartenance à la fenêtre via `isEqual`, connexion de la
scène, dimensions `bounds` et `displayScale`) et ne retient aucune référence native — le
scénario `uikit-driver-observes-real-window` prouve que le Kotlin lit les mêmes valeurs que
le test Swift sur les mêmes objets, le scénario `uikit-driver-reports-detached-view` prouve
qu'une vue hors fenêtre est rapportée `Detached`, et la sentinelle
`uikit-driver-window-membership-sentinel` (le même test détaché, muté) prouve que la
lecture d'appartenance est réellement exercée : supprimer le test d'appartenance tue
exactement ce scénario sur les deux familles sans toucher le préambule
(`testKotlinProbeObservesRealWindow` reste vert).

La ligne BCK-013 se lit : `KadreIos.attach(windowScene:window:surfaceView:applicationFactory:policy:)`
refuse (`InvalidRequest`) une vue hors de la fenêtre fournie, réserve la scène AVANT toute
installation (`AlreadyInUse(Host)` sur un second attach, première session intacte), termine
exactement une fois sous un arrêt pendant un arrêt, conduit le lifecycle par les
notifications de scène RÉELLES et ignore les callbacks natifs post-terminaison. La
sentinelle `uikit-scene-single-owner` (mutation : ignorer l'échec de
`UIKitSceneRegistry.reserve`) est tuée par `testSecondAttachOnTheSameSceneIsAlreadyInUse`
sur les deux familles ; la sentinelle `uikit-lifecycle-scene-faithful` (mutation : fabricater
l'état réduit d'`DidEnterBackground`) est tuée par
`testLifecycleTransitionsFollowTheScene` sur les deux familles. La table
notification→lifecycle est le réducteur normatif `UIKitLifecycleReducer` ; les callbacks
natifs post-terminaison sont neutralisés en profondeur (tokens retirés, routage tardif
inerte, `updateLifecycle` dédoublonné puis no-op sur session terminée) — voir le rapport de
preuve Task 6 pour les rayons d'explosion exacts.

La ligne BCK-014 se lit : sur iPad, une fenêtre d'une AUTRE scène est refusée
(`InvalidRequest("window")`), deux scènes attachées tournent des sessions indépendantes
(ids, surfaces, lifecycles distincts), et la destruction de la session de scène d'une
scène ne termine que SA session (fallback ruling contrôleur : post du vrai nom de
notification — voir la note environnement ci-dessus). La sentinelle
`uikit-window-scene-owner` (mutation : supprimer la comparaison `windowScene.isEqual`)
est tuée par `testAttachRejectsWindowFromAnotherScene` sur la famille iOS seule.

La ligne WIN-009 se lit : la surface primaire EST la surface de la fenêtre primaire (même
`SurfaceId` via `isEqual`, même instance via `===` à travers le pont) et les métriques
initiales valent la lecture Swift des `bounds` de la vue. La sentinelle
`uikit-primary-metrics-faithful` (mutation : fabricater le `contentSize` du
`WindowSpec` d'admission) est tuée par `testPrimaryWindowAndSurfaceIdentity` sur les deux
familles.

La ligne BCK-015 « observations dynamiques, redraw et apply » se lit : à chaque déclencheur
SDK, les métriques publiées valent la lecture VIVANTE de la vue ; un changement entre deux
déclencheurs est observé au déclencheur suivant (resize réel 480×200 propagé,
thème conduit par les overrides réels fenêtre puis vue — repli documenté, §3.3 du rapport
Task 5 —, reprise background/foreground prouvée par le compteur de re-lectures), le redraw
coalesce une rafale de 5 (puis 3) requêtes en EXACTEMENT UN événement par tick (fenêtre de
quiescence), l'`apply` refuse fermé tout champ posé (`PartiallyApplied` à exactement un
rejeté, `Unsupported(UpdateSurface)`) et accepte tout-`Unchanged` (`Applied`), et un
`requestRedraw` post-close répond `Closed(Surface)` sans aucun événement résiduel. Trois
sentinelles : `uikit-metrics-stale-generation` (mutation : le binding republie la géométrie
gelée à la génération d'attach) est tuée par `testSurfaceResizePropagatesMetrics` — des
métriques figées ne portent jamais 480×200 — preuve citée sur tvOS (échec SEUL, rayon au
rapport Task 6 ; même chemin binaire sur iOS) ;
`uikit-redraw-single-per-tick` (mutation : une requête de rafale — ticket en vol — est
REFUSÉE au lieu d'être absorbée par le coalescing ; le gate scheduler seul est MASQUÉ par
le ticket du runtime, cf. rapport Task 6) est tuée par
`testSurfaceRedrawBurstCoalescesToOnePerTick` (« every redraw of the burst must be
admitted » — échec SEUL, preuve citée sur tvOS) ;
`uikit-lease-closed-after-terminal` est la sentinelle de la ligne suivante.

La ligne BCK-015 « bail d'accès natif » se lit : le premier bail est accordé (la vue louée
`isEqual` la surfaceView), un bail DANS la callback du premier répond
`TemporarilyUnavailable(retryable = true)` sans jamais invoquer son block, et le verdict
STEADY post-close est `Unsupported(PlatformSurfaceAccess)` déterministe — la route du
registre (`UIKitViewLeaseRegistry`) est retirée au close AVANT la publication de l'état
détaché. La sentinelle `uikit-lease-closed-after-terminal` (mutation : retirer
l'`unregister` du close — la route survit à la terminaison) est tuée par
`testLeaseBoundariesHonoursFlightAndClose` : le bail post-terminaison retombe alors sur le
port résiduel et répond `Closed(Surface)` (course en vol), jamais le `Unsupported` ruled —
l'assert du verdict steady échoue SEUL (preuve citée sur tvOS, rayon au rapport Task 6).

La ligne BCK-016 se lit : après un changement de géométrie réel de la vue (pleine fenêtre
puis resize à changement d'aspect 240×320 — stimulus DÉGRADÉ, cf. la note rotation
ci-dessus), les métriques publiées valent la géométrie VIVANTE post-stimulus
(`wantWidth ≠ beforeWidth`, published == live). La sentinelle
`uikit-rotation-metrics-faithful` (mutation : le kernel sert la géométrie du miroir
courant — un cache périmé — au lieu de l'observation vivante) est tuée par
`testSurfaceRotationPropagatesMetrics` sur la famille iOS seule : l'égalité géométrique
post-changement (publié == vivant) échoue — l'état ne quitte jamais les dimensions
d'attach, le test meurt à l'attente bornée de la géométrie post-stimulus (rayon exact au
rapport Task 6 : sous ce mutant, exactement `testSurfaceResizePropagatesMetrics` et
`testSurfaceRotationPropagatesMetrics` échouent, la même couture de fidélité géométrique
portant les deux morts). Le kill est PORTÉ PAR LA FIDÉLITÉ GÉOMÉTRIQUE DU CHEMIN DÉGRADÉ
(published == vivant après un changement réel de géométrie), jamais par une rotation
réelle : la rotation réelle est réservée à un runtime qui accepte
`requestGeometryUpdate` (charter manuel / runtime futur) — la branche réelle du test
(fidélité exacte de l'échange portrait↔paysage) reste codée et s'exécutera alors.

La ligne BCK-020 se lit : sur une session SANS provider (le défaut tvOS — mono-scène par
design, DESIGN.md §15.2 — et tout app mono-fenêtre), la capability `requestWindow` est
annoncée `Unsupported(RequestWindow)` et toute demande rend au requérant une requête DÉJÀ
terminale `Rejected(Unsupported(RequestWindow))` AVANT toute réservation : aucun provider
invoqué (compteur), aucune scène activée, aucune corrélation, état du manager intact, et
une seconde demande est refusée pareil (rien n'a été consommé par la première). La
sentinelle `uikit-window-request-tvos-refusal-faithful` (mutation : la capability
null-provider annonce `Supported({OpenedInNewSession})` — le refus ne dit plus la vérité)
est tuée par `testWindowRequestWithoutProviderIsRejectedUnsupportedWithNoNativeEffect` sur
les DEUX familles (même binaire Kotlin, même couture `sceneWindowCapability`) ; le refus
reste par ailleurs pur : toute réservation ou route provider sur ce chemin échoue aux
asserts de pureté du même test (compteur provider, corrélations, résolutions, primaire).

La ligne BCK-021 se lit : sur iPad, `requestWindow` sur un requérant portant le provider
app-owned reste `Pending` jusqu'à la connexion de la NOUVELLE scène corrélée à la demande
(activation `requestSceneSessionActivation` côté app, corrélation par identité — jamais
par index), puis l'enfant s'attache par le chemin ordinaire et l'outcome est
`OpenedInNewSession(childId)` : la chaîne requestId ↔ scène ↔ session enfant est prouvée
deux à deux distincte sur une paire concurrente, le requérant reste unique-fenêtre
(l'enfant n'entre JAMAIS dans sa liste), l'arrêt et le `close()` du requérant détachent
l'enfant du destin du requérant — l'enfant déjà résolu SURVIT (attaché, app vivante,
surface intacte ; « detach » = séparé du sort du requérant, jamais « enfant terminé ») —,
une seconde demande repart du même requérant (slot libéré), et une UIWindow overlay de la
scène requérante survit intacte (visible, toujours key, jamais une fenêtre Kadre).
`uikit-window-request-cancel-before-connect` prouve le cancel avant connexion :
`CancelledBeforeCommit`, demande terminale `Cancelled` pour toujours, et la résolution
tardive de la scène (si FrontBoard l'honore) est un no-op — AUCUN enfant ; le
renforcement de revue (Task 4) fait échouer le scénario quand la gate relâchée ne produit
NI résolution NI refus d'activation explicite (plus de passe vacue). Deux sentinelles :
`uikit-window-request-correlation-crossed` (mutation : `claim` ignore le registre — il
rend la DERNIÈRE entrée pendante au lieu de l'entrée de l'identité demandée) est tuée par
`testConcurrentWindowRequestsCorrelateEachSceneToItsOwnRequest`, seul scénario à porter
deux demandes pendantes EN MÊME TEMPS (les scénarios mono-demande ne voient jamais la
différence) ; `uikit-window-request-cancel-terminal-faithful` (mutation : le `cancel` ne
publie plus l'outcome terminal — le CAS de terminaison est court-circuité, l'annulation
répond `CancelledBeforeCommit` sans jamais terminer la requête) est tuée par
`testWindowRequestCancelledBeforeTheSceneConnectsStaysTerminalWithNoChild` (l'`await` de
l'outcome ne délivre jamais — seul scénario du driver à annuler). La résurrection d'une
demande annulée par la résolution tardive est défendue à DEUX coutures (retrait de
l'entrée au cancel + garde de terminalité au `claimResolve` — chaque couture prouvée en
uikitTest kernel) ; aucune mutation à couture unique ne peut la traverser, et la borne de
pending (`maxPendingWindowRequests`) est du ressort kernel (uikitTest), sans test de borne
au driver.

La ligne BCK-022 se lit : l'attach pose LE recognizer de touch Kadre (`UIKitTouchRecognizer`,
sous-classe Kotlin de `UIGestureRecognizer` exportée `@ExportObjCClass` + `@ObjCAction`) sur
la vue du host — ADDITIVEMENT (`cancelsTouchesInView == false`, la décision tracée : le host
garde ses touches ; les SEULS recognizers ajoutés sont ceux de Kadre) — publie
`InputObservationChanged(touchInstalled = true)` après l'installation, puis révoque tout au
close (recognizer retiré, annonce all-false, zéro résiduel). Le fait POINTEUR est prouvé PAR
FAMILLE dans le scénario partagé (`uikit-input-pointer-source-per-family`) : le survol
s'installe LÀ OÙ LE KLIB LE PORTE (iOS : `hoverInstallCount` bouge ; tvOS : la classe est
absente du klib — `pointerInstalled` reste faux sans faux écrit) et suit le même retrait. La
LIVRAISON réelle d'un touch reste un charter manuel (ruling pré-vol : pas synthétisable
in-process ; `touchEventCount == 0` et `touchStimulusCount == 0` sont des invariants PROUVÉS
du driver, jamais un silence). Deux sentinelles : `uikit-input-touch-additive-policy`
(mutation : le recognizer Kadre pose `cancelsTouchesInView = true` — le host perd ses
touches) est tuée par `testTouchRecognizerInstalledAdditivelyWithHonestAnnouncement`
(XCTAssertFalse « the traced additive policy: the host view keeps its touches », ligne 170) ;
LE MÊME mutant tue `testGestureAndKeyboardSourcesInstalledAdditivelyPerFamily` (le prédicat
`cancelsTouchesInViewAll` couvre le même recognizer — rayon DEUX tests, couture partagée,
preuve citée sur tvOS, 22 exécutés / 2 échecs, même chemin binaire uikitMain sur iOS) ;
`uikit-input-announcement-revoked-at-close` (mutation : le teardown du touch ne publie plus
la révocation — l'annonce de morte ne part jamais) est tuée par
`testTouchRecognizerRemovedAndAnnouncementRevokedAtClose` SEUL (XCTAssertGreaterThanOrEqual
« ("11") is less than ("12") - the all-false revocation announcement must have been
published », ligne 290 — les compteurs d'annonce sont PAR binding : les révocations
clavier/gestes du même teardown ne masquent pas le trou ; preuve citée sur tvOS, 22
exécutés / 1 échec).

La ligne BCK-023 se lit : le binding clavier pose D'ABORD les observers de connexion
(`GCKeyboardDidConnect/DidDisconnect`), puis scanne `GCKeyboard.coalescedKeyboard` — sur un
simulateur qui appareille un clavier physique (« Connect Hardware Keyboard »), l'attach à ce
VRAI objet est un fait vrai, jamais une fabrication (`keyboardConnectCount` ne décroît
jamais, peut légitimement croître) ; l'ensemble EXACT des recognizers de geste par famille
(iOS tap/pan/pinch/rotation, tvOS tap/pan — le klib tvOS ne porte ni pinch ni rotation, ni
le survol) est sur la vue, chaque recognizer `cancelsTouchesInView == false`, et
`gestureKinds` PORTE cet ensemble exact (jamais un de plus). Les LIVRAISONS réelles restent
des charters manuels : `keyboardEventCount == 0`, `keyboardStimulusCount == 0` et
`gestureStimulusCount == 0` sont des invariants PROUVÉS du driver — et la payload d'une
future livraison est bornée par le kernel : `LogicalKey` toujours `Unidentified(null)` et
modifiers toujours vides sur la route GC (pas de `characters`, pas de FlagsChanged — le
texte attend la route UIKeyInput, phase 6), le mapping code inconnu/hors-domaine →
`Unidentified(null)` est prouvé en uikitTest (`UIKitKeyboardHoverMappingTest`) HORS du
pipeline driver (aucune livraison automatisable — aucune sentinelle driver n'est donc
enregistrée pour ce fait, choix documenté), de même que la perte explicite des phases
`Cancelled`/`Failed` de geste (`UIKitGestureStatePhaseTest` — aucun état de geste retenu au
runtime ; la perte est documentée au DESIGN §15.2, Task 5). Deux sentinelles :
`uikit-input-keyboard-binding-live` (mutation : l'`install()` du clavier retourne immédiatement
— binding mort, ni observers, ni scan, ni annonce) est tuée par
`testGestureAndKeyboardSourcesInstalledAdditivelyPerFamily` SEUL (XCTAssertGreaterThan
« ("0") is not greater than ("0") - the keyboard binding install counter must have moved »,
ligne 223 ; le compteur de teardown, indépendant, bouge encore — le test de révocation reste
vert ; preuve citée sur tvOS, 22 exécutés / 1 échec) ; `uikit-input-gesture-set-exact`
(mutation : l'install pose tous les recognizers SAUF le pan — l'ensemble de la vue dérive de
l'ensemble annoncé) est tuée par `testGestureAndKeyboardSourcesInstalledAdditivelyPerFamily`
(XCTAssertEqual — « UIPanGestureRecognizer » absent de l'ensemble de la vue, ligne 213) ;
le MÊME mutant tue les DEUX tests touch (compte total de la vue : 2 ≠ 3, ligne 173 ; retour
au baseline : 0 ≠ −1, ligne 286) — rayon TROIS tests, physiquement inévitable et honnête :
l'inventaire des recognizers de la vue est UNE couture portée par les trois assertions
(miroir du rayon de la sentinelle rotation, phase 2) ; preuve citée sur tvOS, 22 exécutés /
3 échecs.

## 3. Audit de l'en-tête framework (phase 3 — clôture de la divergence F1)

La phase 2 avait enregistré une divergence (F1) : la règle Kotlin-only d'INTEROP-EXPORTS
§7 était tenue par la façade promise, mais l'en-tête généré du framework
(`KadreUikit.framework/Headers/KadreUikit.h`) exportait physiquement
`UIKitSurfaceAccessKt.withUIKitView(_:block:completionHandler:)` — symbole porteur de
`UIView*`. L'audit façade de la phase 3 la clôt PAR SUPPRESSION : `@HiddenFromObjC`
(`kotlin.native`, opt-in `ExperimentalObjCRefinement` — le mécanisme stable qu'INTEROP §2
attendait pour appliquer l'annotation aux actuals) masque la fonction des exports
Obj-C/Swift. La preuve phase 2 ne passe PAS par cet export (vérifié par grep des sources
Swift driver et consumer avant édition : zéro appel — les suites pilotent la sonde Kotlin
`KadreUikitSurfaceDriver.leaseProbe`/`closedLeaseProbe`), donc aucun gate n'est affaibli.
INTEROP §7 est amendé dans le même changement : la règle Kotlin-only vaut pour la façade
ET pour l'en-tête framework.

Inventaire d'audit de l'en-tête régénéré (grep du 2026-10-11, cible
`iosSimulatorArm64/debugFramework` ; tvOS identique) — 7197 lignes, 483 `@interface`,
101 `@protocol` (contre 7228 / 484 / 101 AVANT l'audit) :

- **Contrat — la surface promise §5, visible et supportée** : `KadreIos.attach`
  (`KadreUikitKadreIos`, `attachWindowScene:window:surfaceView:applicationFactory:policy:completionHandler:`),
  la factory (`@protocol KadreUikitKadreApplicationFactory` — référence passée par le
  module applicatif Kotlin), la session (`@protocol KadreUikitKadreSession`), les types de
  policy (`KadrePolicy` et builders) et le value model session/état/outcome + failures
  (famille `KadreResult`/`KadreFailure`, exportée avec foundation pour que la session
  rendue par `attach` soit utilisable depuis Swift).
- **Scaffolding driver — visible VOLONTAIREMENT** : les sondes de conduite
  (`KadreUikitProbe`, `KadreUikitObservation`, `KadreUikitWindowMembership`,
  `KadreUikitSurfaceDriver` et ses résultats `KadreUikitLeaseProbe` /
  `KadreUikitClosedLeaseProbe` / `KadreUikitSurfaceEventCapture`), la sonde de
  reconnaissance du touch (`touchRecognitionProbe` — résultat portant
  `recognizerAttached`/`recognizerOverridesTouchesBeganImp`/`recognizerObjCClassName`/
  `cancelsTouchesInView`/`recognizerEnabled`/`totalGestureRecognizers`, phase 5) et la
  sonde des recognizers de geste (`gestureRecognitionProbe` — `recognizerClassNames` +
  `cancelsTouchesInViewAll`, phase 5), plus les compteurs d'entrée du driver
  (`KadreUikitSurfaceDriver` : touch `touchInstallCount`/`touchTeardownCount`/
  `touchEventCount`/`touchStimulusCount`/`touchAnnouncementCount` ; clavier
  `keyboardInstallCount`/`keyboardTeardownCount`/`keyboardConnectCount`/
  `keyboardEventCount`/`keyboardStimulusCount`/`keyboardAnnouncementCount` ; gestes et
  survol `gestureInstallCount`/`gestureTeardownCount`/`hoverInstallCount`/
  `hoverTeardownCount`/`gestureStimulusCount`/`gestureAnnouncementCount`) —
  instrumentation des suites XCTest (phases 0-5), hors de la surface promise §5, opt-in
  `@KadrePlatformApi`, documentées comme telles dans leur KDoc. Les masquer casserait les
  gates phase 2/3/5 (les tests Swift les pilotent directement) — elles restent donc
  listées ici, pas cachées.
- **Masqué (`@HiddenFromObjC`)** : `withUIKitView` — grep d'audit `withUIKitView` = **0**
  sur les deux en-têtes simulateur post-édition (la classe porteuse
  `KadreUikitUIKitSurfaceAccessKt` a disparu de l'en-tête) : aucun symbole surface-access
  n'apparaît plus dans l'en-tête, la règle INTEROP §7 y est désormais vraie.
- **Détail généré toléré** : tout le reste — l'export transitif de foundation (les 483
  interfaces/101 protocoles incluent les types window/surface/input/capture non promis) —
  statut INTEROP §1 (« détail généré ») et §2 (« les symboles non promis peuvent
  physiquement apparaître dans un bytecode/header généré tant que le toolchain ne permet
  pas de les cacher ») ; les tests interop les ignorent, aucun sample/document ne les
  utilise.

La distribution XCFramework statique (Task 1) porte le même en-tête audité : ses quatre
slices (`ios-arm64`, `ios-arm64-simulator`, `tvos-arm64`, `tvos-arm64-simulator`, listés
dans `AvailableLibraries` du `Info.plist`) sont rassemblées depuis le même binaire
`KadreUikit` — le grep = 0 vaut pour les slices du xcframework réassemblé.
