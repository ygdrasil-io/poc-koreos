# Guide — Lire les capacités conditionnelles d'UIKit/tvOS

**Vérité par feature :** [`kadre/capabilities/uikit.md`](../capabilities/uikit.md) —
ce guide apprend à le LIRE. Registre : des décisions, pas des promesses.

## La structure : un registre à sept colonnes

`capabilities/uikit.md` §2 est une table : `feature`, `target`, `minimum déclaré`,
`compile gate`, `runtime gate`, `état absent`, `tests`. La §1 définit chaque colonne ;
les points qui comptent pour une intégration :

- **target** — les cibles exercées (`iosSimulatorArm64`, `tvosSimulatorArm64` ; les
  lignes distribution citent aussi les slices device). Une ligne qui porte LES DEUX
  targets affirme que la vérité livrée est la même sur les deux familles — chaque fusion
  gardée se justifie DANS sa ligne ; toute divergence de disponibilité a SA propre ligne.
- **minimum déclaré** — le plancher que le CODE déclare (iOS 16.0 / tvOS 16.0) ; la
  toolchain de preuve est au-dessus (27.0, les deux familles). Déclaré n'est pas exercé :
  le registre §4 dit l'écart intégralement — le nightly ne prouve PAS min-OS.
- **compile gate** — le symbole dont la COMPILATION dépend. La plupart des lignes n'en
  ont aucun ; là où la disponibilité diffère par famille, le gate NOMME la frontière de
  klib.
- **runtime gate** — le run xcodebuild qui porte la publication (BCK-012 à WIN-009,
  BCK-015/016, BCK-020/021, BCK-022/023, BCK-024, BCK-026, BCK-027/028) — jamais une
  permission hôte.
- **état absent** — ce qui n'est PAS livré, dit intégralement, ligne par ligne. Lisez
  cette colonne AVANT de planifier une feature : c'est elle qui sépare le livré de
  l'absent.
- **tests** — la classe XCTest qui porte la ligne, exécutée par
  `./gradlew :kadre:contracts:driver:uikit:simulatorTests` (ou
  `scripts/test-uikit-simulator.sh [ios|tvos|all]`).

## Trois espèces d'absence — ne les confondez jamais

C'est LA lecture qui évite les fausses attentes. Une absence est toujours de l'une des
trois espèces, et la colonne `compile gate` vous dit laquelle :

**1. Absence STRUCTURELLE (frontière de klib) — le type n'existe pas dans le klib de la
famille.** La classe SDK est `API_UNAVAILABLE(tvos)` : elle est INNOMMABLE à la
compilation tvOS, et la compilation de la source partagée — qui ne nomme jamais le type —
est elle-même la preuve. Exemples livrés : `UIDropInteraction*` (drop tvOS),
`UIHoverGestureRecognizer` (survol tvOS), `UIPinchGestureRecognizer`/`UIRotationGestureRecognizer`
(pinch/rotation tvOS — d'où l'ensemble de gestes tvOS exactement {double-tap, pan},
contre {double-tap, pan, pinch, rotation} sur iOS). Rien à « activer » : tant que le SDK
Apple ne porte pas la classe, il n'y a rien à livrer — une frontière de plateforme, pas
un trou de mapping. Un jour de livraison future, ce serait une NOUVELLE ligne de
registre, pas un flag.

**2. Absence par DÉCISION — le type existe dans le klib, la livraison n'existe pas.**
Exemples : le binding micro-gamepad `GCMicroGamepad` (télécommande tvOS — type présent,
binding NON LIVRÉ, « suivi enregistré pour la phase suivante ») et l'observation des
transitions `UIFocusEnvironment` (hors périmètre v1 — zéro référence dans la source
`uikitMain`). Zéro symbole à chercher, zéro gate à nommer : c'est un état de DÉCISION.
`requestWindow` sur un attach sans provider (`Unsupported(RequestWindow)`) est la même
espèce : la couture existe, le périmètre est tranché.

**3. Indisponibilité à l'EXÉCUTION — mesurée vivante, jamais codée en dur.** Le type
existe, la livraison existe, et un état de l'environnement décide. Exemple porteur :
`RPScreenRecorder.isAvailable` pour la capture `HostChoice` — LU VIVANT à la
construction, à chaque `requestPermission` et à chaque `reserve`, jamais « Simulator ⇒
indisponible » écrit en dur (mesuré `true` sur les deux simulateurs de gate). La famille
des réponses honnêtes est celle du modèle de capabilities : `Supported(...)`,
`Unavailable(...)`, `Unsupported(...)` — et le registre dit laquelle s'applique à quelle
cellule.

Règle de lecture associée : un `Unsupported` ne se « répare » pas côté consumer. La
réponse honnête d'une intégration est de lire la capability publiée (snapshot de
capabilities de la session) et de ne pas appeler ce qui est annoncé absent — jamais de
tenter et d'interpréter l'échec comme un état.

## Les charters manuels : le concept

Beaucoup de LIVRAISONS réelles ne sont pas synthétisables in-process honnêtement sur
Simulateur : un touch `UITouch`, une touche `GCKeyCode` réelle, un geste, un survol, le
clavier logiciel réel, l'IME réel, un drag CROSS-APP, les frames ReplayKit, la rotation
réelle, la destruction FrontBoard, l'AirPlay/écran externe, la manette Bluetooth réelle,
le bout réel de la Siri Remote (`XCUIRemote` est de l'UI-testing), le stimulus de
pression mémoire, le changement d'échelle d'écran. Ces preuves s'appellent **charters
manuels** : des passes sur matériel réel, tenues en index par la phase 10
(`kadre/DEFERRED-AUDIT-uikit.md`, nommé par la §4 du registre) et nommées ligne par
ligne dans la colonne `état absent`.

Trois propriétés du système, pour lire sans se méprendre :

- **les compteurs prouvent les zéros.** Là où une livraison réelle reste charter, le
  driver porte des compteurs (`touchEventCount == 0`, `keyboardEventCount == 0`,
  `gestureStimulusCount == 0`) qui sont des invariants PROUVÉS, jamais un silence :
  l'absence d'événement est un fait testé, pas une ignorance.
- **l'ANNONCE est livrée même quand la livraison est charter.** Les sources d'entrée
  sont annoncées par un agrégat (`InputObservationChanged` publié à l'install, révoqué
  au teardown) — l'installation d'un binding est un fait prouvé, distinct de la
  livraison d'un événement réel.
- **la release parcourt l'index AVANT la coupe.** Règle TEST-STRATEGY §11.2 : chaque
  capability promise non exerçable sur simulateur doit porter sa validation physique, ou
  la promesse doit être retirée explicitement — jamais l'inverse (registre §4).

## Posture générale

Le registre est un registre de décisions. « Pas encore » n'y figure pas : une ligne dit
soit livré-et-prouvé, soit absent — avec la raison exacte de l'absence. Replongez-y à
chaque doute de disponibilité ; ces guides ne portent que le chemin d'intégration.
