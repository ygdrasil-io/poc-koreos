# kadre hello touch (Android)

Le sample migré de kadre-old `samples/hello-touch-android` + le cœur partagé
`samples/hello-touch` (roadmap Android, phase 9 — AND-010) : le même attach Kotlin forme
**ACTIVITY** que `android-hello-window` sur une `FrameLayout` ordinaire, plus l'observation
du touch — les événements `SurfaceInput` publiés par le runtime (`InputEvent.TouchChanged`,
`Key`, `Scrolled` : phases, identités stables, positions) journalisés dans une console
simple.

**Attach unique, sans relance.** Le sample pose l'attach UNE fois (attach-once) : un
refus (`KadreResult.Failure`) est journalisé tel quel et n'est jamais retenté — la
sémantique d'erreur de programmation assumée d'une démo ; une vraie application
déciderait sa propre politique de relance.

## Mapping kadre-old → nouveau

| kadre-old | nouveau | note |
| --- | --- | --- |
| `samples/hello-touch-android` (`HelloTouchActivity`, `HelloTouchApp`) | `kadre/samples/android-hello-touch` | plus de `MotionEvent` surchargé ni de `GestureDetector` maison : le sample n'observe que ce que le runtime publie (`surface.input.events`) — la réduction d'état, les identités de contact et la livraison appartiennent à la plateforme (AND-004). |
| `samples/hello-touch` (cœur partagé) | fusionné dans ce sample | la partie partagée de l'ancien sample nourrissait les deux plates-formes ; ici l'observation `SurfaceInput` est directement multiplateforme (le même type fondation que web/desktop). |
| `samples/hello-triangle-android-capture` | **non migré** — reporté | le renderer GPU (wgpu4k) est la responsabilité du sample, pas de la plateforme ; la primitive future est le `CapturePort` de phase 8 (consentement, tokens, frames bornés). Voir la phase 9 de `ANDROID-IMPLEMENTATION-ROADMAP.md`. |

## Dépendances — zéro kadre-old

Le module résout les artefacts **publiés** du repository contractuel
(`build/kadre-contract-repository`, déclaré dans les settings racine) : l'umbrella Android
`org.graphiks.kadre:kadre-android` à la version du build. La coordonnée par défaut
(`org.graphiks.kadre:kadre`) serait substituée par le projet `:kadre` dans ce build racine
(mêmes `group:name`) : la variante `kadre-android` vise le même AAR publié, sans la
collision. La variante `-android` est l'AAR exact que la résolution de variantes sert au
consumer instrumenté (`consumers/android`).

## Construire et lancer

Depuis la racine du dépôt (le repository contractuel doit contenir les artefacts de HEAD) :

```sh
./gradlew publishContractArtifacts
./gradlew :kadre:samples:android-hello-touch:installDebug
adb shell am start -n org.graphiks.kadre.samples.hellotouch/.MainActivity
```

Touchez, glissez, multi-touchez la moitié haute de l'écran : la console journalise chaque
`TouchChanged` (phase `Started/Moved/Ended/Cancelled`, identité de contact, position) —
l'observation réduite du runtime, jamais un canal parallèle.
