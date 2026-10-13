# kadre compose showcase (Android)

La démo Compose migrée de kadre-old `samples/compose/android` (roadmap Android, phase 9 —
AND-010) : la liaison Compose publiée de phase 9 (`KadreComposeHost`,
`org.graphiks.kadre.internal:compose-android` depuis le repository contractuel) — UNE
session kadre par host compose, attachée quand la `SurfaceView` rejoint réellement la
fenêtre, fermée à la sortie de composition, recréée après un détachement terminal — plus
l'observation du touch publiée par le runtime (`surface.input.events`).

Le module porte aussi les **deux scénarios instrumentés** du contrat AND-010
(`src/androidTest`, exécutés par la gate Android) :

- `androidComposeShowcaseSingleSession` — recomposition ×3 → UNE session ; sortie de
  composition → terminée par l'un des DEUX arrêts host documentés de la machine
  (`Stopped(HostRequested)` quand le `onDispose` gagne la course,
  `Stopped(HostDetached)` quand la fenêtre retire la vue d'abord — sur une fenêtre
  réelle, le détachement précède) ; re-compose → recréée (une NOUVELLE session sur une
  nouvelle vue) ;
- `androidLegacyTypesAbsent` — le scan du dex de l'APK réellement exécuté (où les classes
  de l'AAR `platform-android` publié sont fusionnées) : ni `AndroidKadreRuntime` ni
  `EventLoop` (les types kadre-old) — ils n'ont jamais été dans le nouveau build.

Le sample n'ajoute aucune politique de relance au-dessus de la liaison : l'attach est
posé une fois par host (attach-once), un refus persistant n'est jamais retenté par le
sample — sémantique d'erreur de programmation assumée d'une démo.

## Mapping kadre-old → nouveau

| kadre-old | nouveau | note |
| --- | --- | --- |
| `samples/compose/android` (`MainActivity`) | `kadre/samples/android-compose-showcase` | l'ancien hôtillage Compose (session manuelle, pompes) devient le composable `KadreComposeHost` de la liaison publiée ; la démo n'écrit plus AUCUNE mécanique de session. |
| `samples/compose/shared` | couvert par `kadre:integration:compose` (le binding) + la fondation | le cœur partagé de l'ancien sample vivait la machine de session à la main ; elle est désormais la liaison testée de phase 9 (les captures de création — policy, factory, vue — survivent à la recréation : elles viennent de la PREMIÈRE composition, pas de la dernière recomposition). |
| `samples/compose/desktop`, `samples/compose/ios`, `samples/compose/web` | hors périmètre Android | les flux desktop/iOS/web restent les leurs. |
| `samples/hello-triangle-android-capture` | **non migré** — reporté | le renderer GPU (wgpu4k) est la responsabilité du sample, pas de la plateforme ; la primitive future est le `CapturePort` de phase 8 (consentement, tokens, frames bornés). Voir la phase 9 de `ANDROID-IMPLEMENTATION-ROADMAP.md`. |

## Dépendances — zéro kadre-old

Le module résout les artefacts **publiés** du repository contractuel
(`build/kadre-contract-repository`, déclaré dans les settings racine) :

- l'umbrella Android `org.graphiks.kadre:kadre-android` (la variante android publiée de
  `org.graphiks.kadre:kadre` — la coordonnée par défaut serait substituée par le projet
  `:kadre` dans ce build racine, mêmes `group:name` ; la variante vise le même AAR) ;
- la liaison Compose `org.graphiks.kadre.internal:compose-android` (l'AAR publié de
  `kadre/integration/compose`, le même parti pris consumateur que le `android-devicetest`).

## Construire et lancer

Depuis la racine du dépôt (le repository contractuel doit contenir les artefacts de HEAD) :

```sh
./gradlew publishContractArtifacts
./gradlew :kadre:samples:android-compose-showcase:installDebug
adb shell am start -n org.graphiks.kadre.samples.composeshowcase/.MainActivity
```

Touchez la surface sous l'overlay : les événements publiés par le runtime défilent dans la
console compose. Les scénarios du contrat tournent sur l'émulateur attaché :

```sh
./gradlew :kadre:samples:android-compose-showcase:connectedDebugAndroidTest
```
