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
  simulateur de chaque famille (les preuves `BCK-012` à `WIN-009`), jamais une permission
  hôte.
- **état absent** — ce qui n'est pas livré à ce stade, dit intégralement : depuis la
  phase 1, l'attach (`KadreIos.attach`), le cycle de vie par scène, le multi-scène iPadOS
  et l'identité de la fenêtre primaire sont livrés ; restent absents sur ce target
  input, IME, drop, displays, devices, capture — et toute surface au-delà de la fenêtre
  primaire fournie par le host. Aucun module applicatif Kadre ne référence
  `:kadre:platform:uikit` en dehors de la preuve.
- **tests** — la classe Xcode qui porte la ligne (`KadreUikitDriverTests` phase 0,
  `KadreUikitAttachTests` phase 1), exécutée par
  `./gradlew :kadre:contracts:driver:uikit:simulatorTests` et consommée par le validateur
  via l'export JUnit (`tools/xctest_to_junit.py`).

## 2. Registre

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| observation de conduite (sonde driver) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-012 (2 simulateurs) | tout le reste : non livré (input, IME, drop, displays, devices, capture) | `KadreUikitDriverTests` |
| `KadreIos.attach` — session par scène et cycle de vie | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-013 (2 simulateurs) | input, IME, drop, displays, devices, capture : non livré | `KadreUikitAttachTests` |
| multi-scène iPadOS (une session par `UIWindowScene` supplémentaire) | iosSimulatorArm64 | iOS 16.0 (iPad) | `:kadre:platform:uikit:compileKotlin*` | BCK-014 (iPad Pro 11-inch (M5)) | tvOS mono-scène par design (DESIGN.md §15.2) ; input, IME, drop, displays, devices, capture : non livré | `KadreUikitAttachTests` (extension `tests/Ios`) |
| fenêtre primaire et identité de surface | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | WIN-009 (2 simulateurs) | toute autre fenêtre/surface : non livré (le host impose la primaire) ; input, IME, drop, displays, devices, capture : non livré | `KadreUikitAttachTests` |

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
