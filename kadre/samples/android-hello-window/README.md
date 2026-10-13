# kadre hello window (Android)

Le sample migré de kadre-old `samples/hello-window-android` (roadmap Android, phase 9 —
AND-010) : l'attach Kotlin par la forme **ACTIVITY** de `attachKadre` sur une `FrameLayout`
ordinaire — **aucune SurfaceView, aucun renderer GPU** — et une console texte qui journalise
le cycle de vie de la fenêtre vu du runtime (attachement, visibilité, focus, dimensions,
terminaison de session).

**Attach unique, sans relance.** Le sample pose l'attach UNE fois (attach-once) : un
refus (`KadreResult.Failure`) est journalisé tel quel et n'est jamais retenté — la
sémantique d'erreur de programmation assumée d'une démo ; une vraie application
déciderait sa propre politique de relance.

## Mapping kadre-old → nouveau

| kadre-old | nouveau | note |
| --- | --- | --- |
| `samples/hello-window-android` (`HelloWindowActivity`, `HelloApp`, `HelloWindowApp`) | `kadre/samples/android-hello-window` | `EventLoop.runApp()` dans `Application.onCreate()` et `AndroidKadreRuntime.currentHandler` disparaissent : l'attach est un appel ordinaire du thread principal (`ComponentActivity.attachKadre(view, factory)`), l'application kadre est une coroutine structurée, la session s'arrête seule au détachement du host (`Stopped(HostDetached)`). |
| `samples/hello-window` (desktop partagé) | hors périmètre Android — non migré ici | le gabarit JVM/desktop reste le flux existant. |
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
./gradlew :kadre:samples:android-hello-window:installDebug
adb shell am start -n org.graphiks.kadre.samples.hellowindow/.MainActivity
```

Touchez la moitié haute de l'écran (le host `FrameLayout`), pivotez, envoyez l'app en
arrière-plan : la console journalise les états de fenêtre et de session publiés par le
runtime — l'observation, jamais une invention.
