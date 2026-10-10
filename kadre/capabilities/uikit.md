# Registre d'implémentation — adapter UIKit (`org.graphiks.kadre.platform.uikit`, phase 0)

**Mandat.** `BACKEND-CAPABILITIES.md` §8 impose à chaque adapter de produire, avec son
implémentation, un fichier public `capabilities/<adapter>.md` à sept colonnes : `feature`,
`target`, `minimum déclaré`, `compile gate`, `runtime gate`, `état absent` et `tests`. Le
format est celui de `kadre/capabilities/web.md` ; la présente édition est la première du
target UIKit et elle ne porte qu'une ligne, parce que la phase 0 ne livre qu'une chose :
la preuve O3 qu'un hôte UIKit réel peut transmettre des objets `UIWindow`/`UIView` à du
code Kotlin/Native et que celui-ci en publie une lecture immédiate, copiée et honnête.

**Portée de lecture.** Deux targets Kotlin partagent la sonde (`KadreUikitProbe`,
`uikitMain`) : `iosSimulatorArm64` et `tvosSimulatorArm64`. La preuve O3 court dans des
applications hôte Xcode réelles (`kadre/contracts/driver/uikit/KadreUikitDriver.xcodeproj`,
schemes `KadreUikitDriverIos` et `KadreUikitDriverTvOs`) qui lient le framework
`KadreUikit` produit par `:kadre:platform:uikit` et exécutent `KadreUikitDriverTests`
sur les simulateurs. La matrice toolchain **relevée** de cette première édition — ce sont
les versions qui ont produit les documents de preuve `BCK-012`, pas des minima au-delà
d'eux-mêmes :

- Kotlin **2.4.20** (Kotlin/Native), Gradle **9.8.0** ;
- Xcode **27.0 (27A266a)**, `xcodebuild` + `xcresulttool` du même Xcode ;
- simulateurs réellement exercés : **iPhone 17 / iOS 27.0 (build 24A434)** et
  **Apple TV 4K (3rd generation) / tvOS 27.0 (build 24J360)** (relevés
  `devices` des bundles `build/xcresult/{ios,tvos}.xcresult`) ;
- planchers **déclarés** de l'adapter : **iOS 16.0 / tvOS 16.0** — déclarés, pas exercés :
  aucune passe de preuve ne court sous 27.0 à ce stade (voir la colonne `minimum déclaré`).

**Règle d'identité du pont Obj-C (relevé Task 5, 2026-10-10).** `===` ne traverse PAS le
pont Kotlin/Obj-C : pour la même instance `UIWindow` reçue du hôte, le pont produit deux
wrappers Kotlin distincts (`===` faux) alors que `hashCode` coïncide. Toute comparaison
d'identité Objective-C côté hôte passe donc par `NSObject.isEqual` — c'est la lecture
qu'implémente `KadreUikitProbe.observe` (`view.window.isEqual(window)`), et c'est la
règle que la sentinelle de `BCK-012` protège (`uikit-driver-window-membership-sentinel`).

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
  simulateur de chaque famille (la preuve `BCK-012`), jamais une permission hôte.
- **état absent** — ce qui n'est pas livré à ce stade, dit intégralement : tout le reste
  de la surface d'un adapter (attach, surface, input, IME, drop, displays, devices,
  capture) n'existe pas encore sur ce target — aucun module applicatif Kadre ne référence
  `:kadre:platform:uikit` en dehors de la preuve.
- **tests** — la classe Xcode qui porte la ligne (`KadreUikitDriverTests`), exécutée par
  `./gradlew :kadre:contracts:driver:uikit:simulatorTests` et consommée par le validateur
  via l'export JUnit (`tools/xctest_to_junit.py`).

## 2. Registre

| feature | target | minimum déclaré | compile gate | runtime gate | état absent | tests |
|---|---|---|---|---|---|---|
| observation de conduite (sonde driver) | iosSimulatorArm64, tvosSimulatorArm64 | iOS 16.0 / tvOS 16.0 | `:kadre:platform:uikit:compileKotlin*` | BCK-012 (2 simulateurs) | tout le reste : non livré (attach, surface, input, IME, drop, displays, devices, capture) | `KadreUikitDriverTests` |

La ligne se lit : `KadreUikitProbe.observe(window:view:)` publie une `KadreUikitObservation`
copiée (appartenance à la fenêtre via `isEqual`, connexion de la scène, dimensions
`bounds` et `displayScale`) et ne retient aucune référence native — le scénario
`uikit-driver-observes-real-window` prouve que le Kotlin lit les mêmes valeurs que le
test Swift sur les mêmes objets, le scénario `uikit-driver-reports-detached-view` prouve
qu'une vue hors fenêtre est rapportée `Detached`, et la sentinelle
`uikit-driver-window-membership-sentinel` (le même test détaché, muté) prouve que la
lecture d'appartenance est réellement exercée : supprimer le test d'appartenance tue
exactement ce scénario sur les deux familles sans toucher le préambule
(`testKotlinProbeObservesRealWindow` reste vert).
