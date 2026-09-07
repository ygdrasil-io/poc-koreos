# Task 4 — review fix round 1

## Corrections

- Ajout du seam runtime interne `detachImmediately` pour `pagehide` :
  `Stopped(HostDetached)` est publié sans attendre `shutdownTimeout`; le detach ordinaire reste
  coopératif.
- Séparation du cleanup de port et de la réservation. Le port est relâché durant la fermeture de
  surface, alors que la réservation est relâchée dans `RuntimeSessionObserver` après
  `SessionState.Terminated`.
- Cleanup de port best effort sur les échecs d'admission et de teardown; une exception de cleanup
  ne fuit plus hors de `attach` et ne bloque pas la libération de réservation.
- Le rapport précédemment suivi est supprimé de Git; ce rapport de remplacement reste ignoré.
- Tests renforcés pour une observation inter-document directe et pour `pagehide` sur application
  non coopérative.

## RED

Avant l'implémentation, le test JS Web échouait comme attendu :

- `pagehideTerminatesAStubbornApplicationWithoutAwaitingShutdownTimeout` observait `Stopping`
  au lieu de `Terminated(Stopped(HostDetached))`;
- `cleanupFailureDuringLifecycleInstallationDoesNotEscapeOrLeakReservation` laissait remonter
  `IllegalStateException("cleanup")`.

Le test runtime JVM échouait à la compilation, car `RuntimeHostController.detachImmediately`
n'existait pas.

## GREEN / vérification

```text
./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeHostControllerTest.immediateHostDetachPublishesTerminationWithoutAwaitingANonCooperativeApplication
./gradlew :kadre:platform:web:jsBrowserTest --tests org.graphiks.kadre.platform.web.WebHostSessionTest --tests org.graphiks.kadre.platform.web.WebLifecycleReducerTest
./gradlew :kadre:platform:web:wasmJsBrowserTest --tests org.graphiks.kadre.platform.web.WebHostSessionTest --tests org.graphiks.kadre.platform.web.WebLifecycleReducerTest
./gradlew :kadre:runtime:jvmTest :kadre:platform:web:jsBrowserTest :kadre:platform:web:wasmJsBrowserTest
git diff --check
```

Toutes les commandes ont réussi. Les artefacts `kotlin-js-store/` générés par Gradle sont retirés
avant le commit.

## Concern

Aucun.
