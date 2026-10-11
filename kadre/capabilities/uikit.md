# Registre d'implémentation — adapter UIKit (`org.graphiks.kadre.platform.uikit`, phase 1)

**Mandat.** `BACKEND-CAPABILITIES.md` §8 impose à chaque adapter de produire, avec son
implémentation, un fichier public `capabilities/<adapter>.md` à sept colonnes : `feature`,
`target`, `minimum déclaré`, `compile gate`, `runtime gate`, `état absent` et `tests`. Le
format est celui de `kadre/capabilities/web.md` ; la première édition (phase 0) ne portait
qu'une ligne — la preuve O3 qu'un hôte UIKit réel peut transmettre des objets
`UIWindow`/`UIView` à du code Kotlin/Native et que celui-ci en publie une lecture immédiate,
copiée et honnête. La présente édition (phase 1) ajoute la livraison d'`KadreIos.attach` :
une session par `UIWindowScene`, le cycle de vie conduit par les notifications de scène
réelles, le multi-scène iPadOS et l'identité de la fenêtre primaire — chacune portée par sa
ligne, avec ses sentinelles.

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
  simulateur de chaque famille (les preuves `BCK-012` à `WIN-009` puis `BCK-015`/`BCK-016`),
  jamais une permission hôte.
- **état absent** — ce qui n'est pas livré à ce stade, dit intégralement : depuis la
  phase 1, l'attach (`KadreIos.attach`), le cycle de vie par scène, le multi-scène iPadOS
  et l'identité de la fenêtre primaire sont livrés ; depuis la phase 2, les observations
  dynamiques de surface (métriques, thème, visibilité — re-lectures sur déclencheurs SDK
  réels), le redraw coalescé, l'`apply` fermé, le bail d'accès natif `withUIKitView` et la
  rotation iPadOS (stimulus dégradé, cf. note ci-dessus) sont livrés ; restent absents sur
  ce target IME, drop, displays, devices, capture — et toute surface au-delà de la fenêtre
  primaire fournie par le host. Le CHANGEMENT D'ÉCHELLE d'écran (`displayScale`) et le
  mode-change tvOS (`UIScreenModeDidChangeNotification` — observé mais sans stimulus
  programmatique sur simulateur) n'ont pas de preuve sur simulateur : charter manuel
  (matériel de la phase 10). Aucun module applicatif Kadre ne référence
  `:kadre:platform:uikit` en dehors de la preuve.
- **tests** — la classe Xcode qui porte la ligne (`KadreUikitDriverTests` phase 0,
  `KadreUikitAttachTests` phase 1, `KadreUikitSurfaceTests` phase 2 — extension iOS-only
  `tests/Ios/KadreUikitSurfaceIosTests.swift` compilée DANS la même classe XCTest pour la
  rotation), exécutée par
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

La ligne BCK-015 « observations dynamiques, redraw et apply » se lit : les métriques
publiées valent en permanence la lecture VIVANTE de la vue (resize réel 480×200 propagé,
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
