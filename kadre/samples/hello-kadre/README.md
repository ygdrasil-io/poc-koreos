# hello-kadre — fenêtre + entrées, 100 % console

Le plus petit sample du dépôt. Il ouvre une fenêtre native **sans rendu et sans
Compose** : tout ce qui lui arrive est imprimé dans la console. C'est le point
d'entrée recommandé pour découvrir l'API Kadre avant d'ouvrir la démo complète
(`kadre/samples/desk-tour`).

## Lancer

```bash
./gradlew :kadre:samples:hello-kadre:run
```

Deux arguments JVM sont déjà configurés dans `build.gradle.kts` :

- `-XstartOnFirstThread` — AppKit (macOS) exige de tourner sur le thread principal ;
- `--enable-native-access=ALL-UNNAMED` — autorise le pont kffi vers AppKit.

Le backend demandé est `DesktopBackend.Auto` : chaque plateforme résout son
backend natif (AppKit sur macOS).

## Ce que montre `HelloKadreMain.kt`

| Étape | Quoi | API |
|---|---|---|
| 1 | Démarrer le runtime et laisser la plateforme choisir le backend | `runKadreApplication`, `DesktopHostOptions.Standalone` |
| 2 | Lister les écrans détectés | `KadreScope.displays` |
| 3 | Créer une fenêtre (titre, taille logique) et attendre son ouverture | `windows.requestWindow(WindowSpec(...))`, `WindowRequest.await()` |
| 4 | Imprimer les événements de fenêtre (géométrie, propriétés) | `window.events` |
| 5 | Imprimer les événements de surface (focus, visibilité, dimensions) | `window.surface.events` |
| 6 | Imprimer les entrées (clavier, souris, scroll, gestes) | `window.surface.input.events` |
| 7 | Répondre à la demande de fermeture du système | `WindowEvent.CloseRequested`, `window.respondToCloseRequest(..., Accept)` |

## À essayer une fois la fenêtre ouverte

- cliquez dans puis hors de la fenêtre → les lignes `[surface]` suivent le focus ;
- déplacez et redimensionnez la fenêtre → les lignes `[fenêtre]` suivent la géométrie ;
- tapez au clavier, bougez la souris, scrollez → les lignes `[entrée]` montrent chaque événement ;
- fermez avec le bouton rouge → le protocole de fermeture en deux temps s'exécute, la session
  s'arrête (`stopWhenLastWindowClosed = true`) et le processus se termine.

## Hors périmètre

Le rendu, la saisie de texte (IME), le drag-and-drop, le multi-fenêtres et le web
sont montrés par le sample complet : `kadre/samples/desk-tour`.
