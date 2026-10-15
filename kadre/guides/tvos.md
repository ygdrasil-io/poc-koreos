# Guide — tvOS : mono-scène, télécommande, saisie, Simulator vs matériel

**Vérité par feature :** [`kadre/capabilities/uikit.md`](../capabilities/uikit.md) —
les mêmes lignes que iOS (l'adapter est un seul `uikitMain`, les divergences ont chacune
leur ligne), plus les deux lignes dédiées télécommande/focus tvOS ; narratif
`BACKEND-CAPABILITIES.md` §6.2 ; `DESIGN.md` §15.2. Registre : des décisions, pas des
promesses.

## Les mêmes contrats, une topologie mono-scène

tvOS porte les mêmes contrats d'attach et la même façade Swift (`KadreIos`,
`KadreHost` — mêmes noms, mêmes slices du XCFramework) avec une topologie **mono-scène
par design** (`DESIGN.md` §15.2) : une seule session par application, aucune seconde
`UIWindowScene` demandée. Concrètement :

- n'attendez qu'UN `HostViewport` dans l'inventaire displays (l'unique écran EST l'écran
  de la fenêtre) ;
- ne passez JAMAIS de `windowSceneProvider` : `requestWindow` répond
  `Rejected(Unsupported(RequestWindow))` avant toute réservation, sans aucun effet
  natif — ce refus est le contrat tvOS (BCK-020) ;
- le multi-scène iPadOS ([`multi-scene.md`](multi-scene.md)) n'a PAS de lecture tvOS ;
- tout le reste de l'attach — thread principal, réservation de scène, `AlreadyInUse(Host)`,
  lifecycle par notifications réelles — est identique ([`uikit.md`](uikit.md)).

## Télécommande : l'honnêteté d'abord — le focus est SYSTÈME

Décision fermée à énoncer telle quelle dans toute intégration tvOS : **Kadre n'a AUCUN
binding télécommande.** La Siri Remote pilote le FOCUS SYSTÈME du host — la navigation
UIKit (`UIFocusEnvironment`) reste celle de vos contrôleurs et vues natifs — et coexiste
avec les contrôles du host sans passer par Kadre. Deux lignes dédiées du registre le
portent :

- **binding micro-gamepad (`GCMicroGamepad`) NON LIVRÉ** : aucun `valueChangedHandler`
  ni `controllerPausedHandler` n'est posé sur le profil ; le mapping des pressions
  natives (directions, sélection, retour) vers les types d'input Kadre n'existe pas —
  « suivi enregistré pour la phase suivante » (BACKEND-CAPABILITIES §6.2). AUCUNE
  frontière SDK : le type existe dans le klib — l'absence est une DÉCISION de livraison,
  jamais une absence structurelle ;
- **observation des transitions `UIFocusEnvironment` HORS PÉRIMÈTRE v1** : Kadre
  n'observe pas le moteur de focus du host, n'introduit pas de moteur de navigation de
  widgets, et le focus de la surface reste piloté par les transitions de scène (le
  lifecycle). C'est une décision de périmètre, pas un suivi enregistré.

Le fait ADJACENT livré (BCK-027) : `controller.microGamepad` non-nil classe la Siri
Remote micro-only dans `InputDevicePort` — jamais full gamepad sans `extendedGamepad`.
Et l'ensemble de gestes livré sur la vue du host est exactement **{double-tap, pan}** :
le klib tvOS ne porte NI pinch NI rotation NI le survol (`API_UNAVAILABLE(tvos)` dans le
SDK Apple — frontières de plateforme, pas des trous de mapping). La preuve du BOUT réel
reste au charter manuel (`XCUIRemote` est de l'UI-testing, pas synthétisable in-process).

Conséquence d'intégration : votre app tvOS reste responsable de sa navigation par focus
(la télécommande la pilote via UIKit) ; les entrées qui atteignent la session Kadre sont
celles des sources livrées — touch/recognizers additifs, clavier GameController,
gestes {double-tap, pan} — annoncées dans l'agrégat d'input et révoquées au teardown.

## Saisie système : la session texte est livrée, le clavier réel est un charter

La session texte (BCK-024) est livrée À L'IDENTIQUE sur tvOS — la route UIKeyInput ne
diverge pas par klib. C'est la saisie par le SYSTÈME, dans les limites suivantes, toutes
portées par la ligne du registre :

- `SurfaceInput.openTextInput` pose un receiver natif Kadre (`UITextField` caché — alpha
  0, jamais `isHidden` — posé dans la hiérarchie du host à l'open, retiré au close) ;
- ombre immédiate et révisions, write-back conduit (champ d'abord, ombre ensuite), refus
  `StaleRevision(expected, received)` typé, open exclusif (`AlreadyInUse(TextInputSession)`),
  plages `Replace` nommées par UIKit (frontière de graphème d'UIKit, jamais recomposée),
  mapping de config (`purpose`→`keyboardType`, `action`→`returnKeyType`,
  `capitalization`→`autocapitalizationType`, `autocorrect`→`autocorrectionType`,
  Email/Password vérifiés sur les brutes du champ) ;
- limites honnêtes : le couplage focus→Suspend n'existe pas côté UIKit (les hooks de
  focus du receiver ne font QUE compter — la session reste `Active` à la perte et au
  retour du first responder) ; le clavier logiciel ne redimensionne rien (aucune
  notification keyboard-frame observée — le layout de la surface reste au host) ;
  **le clavier réel et l'IME réel restent des charters manuels** — sur tvOS comme sur
  iOS, la saisie physique réelle (la Siri Remote vers le clavier système, les langues,
  les compositions vivantes) se valide sur matériel.

## Simulator vs Apple TV physique : ce que chaque cible prouve

La preuve automatisée tvOS court sur le simulateur **Apple TV 4K (3rd generation) /
tvOS 27.0** (gate `scripts/test-uikit-simulator.sh tvos`, preuves BCK-013 à BCK-028,
consumer gate `tvosSimulatorTests`). Ce que le Simulator PROUVE : l'attach et le
lifecycle, les observations dynamiques de surface, le redraw coalescé, l'`apply` fermé,
le refus `requestWindow`, l'installation des sources d'entrée et leurs annonces
(gestes exacts {double-tap, pan} compris), la session texte de bout en bout, l'absence
structurelle du drop tvOS, les inventaires displays/devices (miroir SDK), la pression
mémoire, la capture cible `Surface` avec de vraies frames.

Ce qui reste un **charter manuel sur Apple TV physique** (index tenu par
`kadre/DEFERRED-AUDIT-uikit.md`, colonne `état absent` du registre) :

- le BOUT réel de la Siri Remote — `XCUIRemote` est de l'UI-testing ; aucun binding
  n'est livré de toute façon (voir ci-dessus) ;
- la destruction de session par FrontBoard réelle (`requestSceneSessionDestruction` est
  un no-op silencieux sur simulateur — note « environnement simulateur » du registre) ;
- les frames ReplayKit (`HostChoice`) — jamais livrées sur Simulator, fait mesuré ;
- le stimulus de pression mémoire réel, le mode-change d'écran réel, une manette
  Bluetooth/USB réelle (GameController matériel), AirPlay.

Règle de la §4 du registre, valable pour toute release tvOS : chaque capability promise
non exerçable sur simulateur porte sa validation physique, ou la promesse est retirée
explicitement — jamais l'inverse.

## Le sample de coexistence : l'app consumer, scène tvOS gated

La coexistence décrite ci-dessus EST RUNNABLE : la scène tvOS gated du consumer
(`kadre/consumers/swift/consumer-app/apps/Tvos/ConsumerTvOsCoexistenceScene.swift`,
[`README.md`](../consumers/swift/README.md)) pose trois `UIButton` natifs **focusables**
autour de la surface Kadre (`KadreHostViewController` en enfant — sa view EST la
surfaceView) dans LA MÊME hiérarchie, et le focus reste au MOTEUR SYSTÈME :

- le gate `KADRE_CONSUMER_TVOS_COEXIST` (env ou argument, mécanique phase 1
  `DriverSceneRequest`) bascule la fenêtre tvOS sur la scène — l'attach est
  controller-driven, la factory est la factory TENUE (session longue) ;
- **la surface n'est PAS un participant au focus** (`canBecomeFocused == false` — une
  UIView plate, aucune binding Kadre) — l'honnêteté est une ASSERTION
  (`ConsumerTvOsCoexistenceTests`, famille tvOS du run standard consumer) : la navigation
  appartient aux contrôles natifs, pilotée par le moteur de focus UIKit ;
- la saisie passe par le chemin système livré (BCK-024) : le bouton « Saisie » appelle
  `SurfaceInput.openTextInput` via `ConsumerTextInputBridge` (le module applicatif Kotlin
  du consumer — les artefacts publiés, jamais `project(...)`) ; le receiver natif Kadre
  reçoit la saisie du système et les événements `Replace` s'affichent au label ;
- la manette est conditionnelle par matériel (BCK-027 côté app) : les `GCController`
  sont classés `extendedGamepad` (manette complète) / `microGamepad` (télécommande
  micro-only) — la règle de Kadre, la réponse d'affichage app-owned.

Sur le SIMULATEUR, la navigation « télécommande » s'exerce avec les touches mappées par
le Simulator (flèches = directions, retour chariot = sélection) — l'émulation du
Simulator, pas un bout réel ; le BOUT de la Siri Remote reste le charter manuel de la
section ci-dessus. Lancement (après un `validateSwiftConsumer` qui a installé l'app) :

```bash
SIMCTL_CHILD_KADRE_CONSUMER_TVOS_COEXIST=1 \
  xcrun simctl launch "Apple TV 4K (3rd generation)" org.graphiks.kadre.consumer.tvos
```

Ce que la scène PROUVE automatiquement (tests consumer tvOS) : l'attach exactement une
fois, la coexistence hiérarchique, l'exclusion de la surface du focus, l'ouverture/
fermeture de la session texte par le consumer. Ce qu'elle NE prouve PAS (et ne prétend
pas) : aucun binding télécommande Kadre — il n'existe pas.

## La préuve séparée est obligatoire

Une preuve iOS ne couvre pas tvOS. Les tests partagés (`KadreUikitInputTests`,
`KadreUikitTextInputTests`, `KadreUikitDropAbsenceTests`, `KadreUikitCaptureTests`…)
assertent la vérité PAR famille dans le même run ; les scénarios iOS-only sont gardés
`!os(tvOS)`. Toute intégration tvOS se prouve par ses propres runs — le gate de famille
`scripts/test-uikit-simulator.sh tvos` et le consumer gate tvOS sont les points d'entrée
([`installation.md`](installation.md)).
