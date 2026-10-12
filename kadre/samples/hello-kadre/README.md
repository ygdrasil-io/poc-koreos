# hello-kadre — les bases de Kadre, 100 % console

Le plus petit sample du dépôt, **sans rendu ni Compose** : une fenêtre (bureau)
ou un conteneur (web), et tout ce qui lui arrive est imprimé dans la console.
C'est le point d'entrée recommandé pour découvrir l'API Kadre avant d'ouvrir la
démo complète (`kadre/samples/desk-tour`).

Le module est multiplateforme : deux runtimes, la même idée, le même code
d'observation partagé (`commonMain/HelloKadreShared.kt`). Chaque runtime
n'écrit que son bootstrap.

## Lancer

```bash
# Bureau (JVM) — les événements arrivent dans le terminal
./gradlew :kadre:samples:hello-kadre:run

# Web (wasmJs) — les événements arrivent dans la console du navigateur (DevTools)
./gradlew :kadre:samples:hello-kadre:wasmJsBrowserDevelopmentRun
```

Deux arguments JVM sont configurés dans `build.gradle.kts` : `-XstartOnFirstThread`
(uniquement sur un hôte macOS — AppKit exige le thread principal ; les JVM des
autres OS refuseraient l'option) et `--enable-native-access=ALL-UNNAMED` (le pont
kffi vers le backend natif).

## Ce que montre le bureau (`jvmMain/HelloKadreMain.kt`)

| Étape | Quoi | API |
|---|---|---|
| 1 | Démarrer le runtime et laisser la plateforme choisir le backend | `runKadreApplication`, `DesktopHostOptions.Standalone` |
| 2 | Lister les écrans détectés | `KadreScope.displays` |
| 3 | Créer une fenêtre (titre, taille logique) et attendre son ouverture | `windows.requestWindow(WindowSpec(...))`, `WindowRequest.await()` |
| 4 | Imprimer les événements de fenêtre (géométrie, propriétés) | `window.events` |
| 5 | Imprimer les événements de surface (focus, visibilité, dimensions) | `window.surface.events` |
| 6 | Imprimer les entrées (clavier, souris, scroll, gestes) | `window.surface.input.events` |
| 7 | Répondre à la demande de fermeture du système | `WindowEvent.CloseRequested`, `window.respondToCloseRequest(..., Accept)` |

## Ce que montre le web (`wasmJsMain/HelloKadreWebMain.kt`)

Le web n'a pas de fenêtres OS : la session s'attache à un conteneur de la page
(`index.html`) et **ce conteneur est la fenêtre**. Pas de `requestWindow` ni de
fermeture système — la session vit tant que la page vit.

| Étape | Quoi | API |
|---|---|---|
| 1 | Attacher la session au conteneur de la page | `HTMLElement.attachKadre(parentScope, application = ...)` |
| 2 | Lister les écrans détectés | `KadreScope.displays` (partagé) |
| 3 | Récupérer la surface primaire (le conteneur) | `primarySurface.value` |
| 4 | Imprimer les événements de surface (focus, visibilité, métriques) | `surface.events` |
| 5 | Imprimer les entrées (souris, clavier) | `surface.input.events` |

## À essayer

*Bureau* — cliquez dans puis hors de la fenêtre (`[surface]` focus), déplacez et
redimensionnez (`[fenêtre]` géométrie), tapez au clavier et scrollez (`[entrée]`),
fermez avec le bouton rouge : le protocole de fermeture s'exécute et le processus
se termine (`stopWhenLastWindowClosed = true`).

*Web* — ouvrez la console (F12), bougez la souris sur la page (`[entrée]`), cliquez
dans et hors du conteneur (`[surface]` focus), redimensionnez la fenêtre du
navigateur (`[surface]` métriques).

## Plateformes du bureau

Le code est identique partout : `DesktopBackend.Auto` laisse la façade choisir le
backend natif de l'OS hôte.

| OS | Backend | État |
|---|---|---|
| macOS | AppKit | Complet — vérifié (fenêtre, focus, entrées, fermeture) |
| Linux | Wayland puis X11 | Provider Wayland enregistré ; le backend est livré par phases (hôte headless aujourd'hui) : la session s'attache, mais pas encore de fenêtre ni d'entrées réelles |
| Windows | Win32 | En attente du backend (aucun provider livré) |

## Hors périmètre

Le rendu, la saisie de texte (IME), le drag-and-drop, le multi-fenêtres et les
fenêtres enfants web (`windowProvider`) sont montrés par le sample complet :
`kadre/samples/desk-tour`.
