# Consumer Swift — le sample minimal Kadre UIKit/tvOS

**Ce module EST le sample minimal de la phase 10** : une app UIKit/SwiftUI iOS + tvOS qui
consomme les artefacts PUBLIÉS de la distribution Kadre — exactement ce que ferait un
consumer externe, et la preuve d'intégration runnable que les guides décrivent
([`installation.md`](../../guides/installation.md)). La feuille de route
(`IOS-IMPLEMENTATION-ROADMAP.md` §4) nomme `kadre/consumers/swift/` la maison des
consumers : un module `samples/` distinct dupliquerait l'app ET la discipline de lien
deux-frameworks statiques documentée dans
[`consumer-app/project.yml`](consumer-app/project.yml) — la décision est prise, pas
à reprendre. Le tour riche multi-fenêtres est l'affaire d'`android-kadre-tour` côté
Android ; côté Swift, le consumer minimal tient lieu de sample.

## Le chemin minimal de bout en bout (zéro `project(...)`)

```bash
# 1. Publier les artefacts (racine du dépôt) — publications communes + uikit + foundation.
./gradlew publishContractArtifacts \
          :kadre:platform:uikit:publishAllPublicationsToContractTestRepository \
          :kadre:foundation:publishAllPublicationsToContractTestRepository

# 2. Consommer : le point d'entrée unique publie AU PRÉALABLE puis pilote le check du
#    consumer avec -PkadreRepository/-PkadreVersion (les deux propriétés sont REQUISES).
./gradlew :kadre:validateSwiftConsumer
```

Le `check` de l'agrégateur (ce répertoire) enchaîne : la vérification du repository
contractTest (`org/graphiks/kadre/uikit/<version>/uikit-<version>.module`), les liens des
frameworks statiques `consumer-kmp` par cible, l'assemblage du
`KadreUikit.xcframework` de distribution, `xcodegen generate` (le `.xcodeproj` généré est
commité), puis les QUATRE runs `xcodebuild test` (standard + lancement gated SwiftUI,
iOS et tvOS) et leurs exports JUnit. La garde anti-duplication Obj-C lit le log de
lancement (« Class … is implemented in both » = échec, même si les tests passent).

Un consumer HORS de ce dépôt suit exactement les mêmes `-P` contre le même repository
publié — c'est la forme d'intégration prévue
([`installation.md`](../../guides/installation.md) §« La consommation »). La tâche est
macOS-only (xcodebuild, simulateurs, xcodegen), volontairement hors `:kadre:check`.

## L'app consumer (`consumer-app/`)

- `apps/Shared` — le délégué de scène et les stores de faits (le bundle de test ne lie
  AUCUN framework Kotlin : les archives statiques sont liées une fois, dans l'app) ;
- `apps/Ios` / `apps/Tvos` — les points d'entrée par famille (`MainApp.swift`) ;
- `apps/Shared/ConsumerSwiftUIScene.swift` + `ConsumerRepresentable.swift` — le chemin
  SwiftUI gated (Task 6) : le wrapper `UIViewControllerRepresentable` app-owned ;
- `apps/Tvos/ConsumerTvOsCoexistenceScene.swift` — la scène de coexistence tvOS (ci-
 dessous) ;
- `tests/Shared` — les XCTest qui prouvent l'attach, l'identité de types, l'attache-une-
  fois SwiftUI et la coexistence tvOS ;
- `consumer-kmp/` — le module applicatif Kotlin du consumer (framework statique
  `baseName = "KadreUikit"`, exports uikit + foundation — voir l'en-tête de son
  `build.gradle.kts` pour la règle du préfixe Obj-C).

## La scène de coexistence tvOS (Task 7) — le focus est SYSTÈME

`KADRE_CONSUMER_TVOS_COEXIST` (env ou argument `-KADRE_CONSUMER_TVOS_COEXIST`) bascule la
fenêtre tvOS sur la scène de coexistence : trois `UIButton` natifs **focusables** autour
de la surface Kadre (`KadreHostViewController` en enfant — sa view EST la surfaceView),
navigués par le **moteur de focus système**. L'honnêteté d'abord :

- **Kadre n'a AUCUN binding télécommande** (ni `GCMicroGamepad` — non livré, suivi
  enregistré ; ni observation `UIFocusEnvironment` — hors périmètre v1 ; registre
  [`uikit.md`](../../capabilities/uikit.md)). La surface Kadre n'est PAS un participant
  au focus (`canBecomeFocused == false`, asserté par
  `ConsumerTvOsCoexistenceTests`) — le sample prouve la **COEXISTENCE** (la forme exacte
  de la preuve phase 5) : le focus système navigue les contrôles natifs, la surface vit
  à côté dans la même hiérarchie.
- **La saisie est le chemin système livré (BCK-024)** : le bouton « Saisie » appelle
  `SurfaceInput.openTextInput` via `ConsumerTextInputBridge` (module applicatif Kotlin —
  les artefacts publiés, jamais `project(...)`). Le receiver natif Kadre reçoit la saisie
  du système ; les événements `Replace` s'affichent au label. Le clavier réel reste un
  charter manuel sur Apple TV physique ([`tvos.md`](../../guides/tvos.md)).
- **La manette est conditionnelle par matériel** (BCK-027 côté app) : le sample inventorie
  les `GCController` et classe `extendedGamepad` (manette complète) / `microGamepad`
  (télécommande micro-only) — la règle de Kadre, la réponse d'affichage app-owned.
- **Le BOUT réel de la Siri Remote reste un charter manuel** (`XCUIRemote` est de
  l'UI-testing, jamais synthétisable in-process). Sur le SIMULATEUR, la navigation par
  télécommande s'exerce avec les touches mappées par le Simulator (flèches = directions,
  retour chariot = sélection) — l'émulation du Simulator, pas un bout réel.

Lancer le sample sur le simulateur tvOS (après un `validateSwiftConsumer` qui a installé
l'app) :

```bash
SIMCTL_CHILD_KADRE_CONSUMER_TVOS_COEXIST=1 \
  xcrun simctl launch "Apple TV 4K (3rd generation)" org.graphiks.kadre.consumer.tvos
```

Les tests (`ConsumerTvOsCoexistenceTests`, famille tvOS du run standard) prouvent la
structure de focus (les contrôles natifs focusables, la surface exclue), l'attach
exactement une fois de la scène et l'ouverture/fermeture de la session texte par le
consumer. Le récit tvOS complet : [`tvos.md`](../../guides/tvos.md).
