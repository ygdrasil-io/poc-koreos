# New Kadre — Snapshot du plan directeur

> Ce document ordonne la refonte complète de l’API publique. Il ne remplace pas les plans d’implémentation task-by-task qui seront dérivés après validation finale de `new-kadre/DESIGN.md`.

**Objectif :** remplacer l’API winit-like par une API Kotlin Multiplatform coroutine, embeddable-first, capability-driven et sans état global.

**Architecture :** `kadre` devient l’unique API principale. Une `KadreSession` attachée à un host possède sa hiérarchie coroutine, ses fenêtres, ses périphériques et ses captures. Les backends traduisent les contrats communs vers leurs threads et lifecycles natifs.

**Stack :** Kotlin Multiplatform, kotlinx.coroutines, Flow/StateFlow, Kotlin Time, Gradle KMP, Kotlin ABI validation, Kotlin/Native, Kotlin/JS et Kotlin/Wasm.

**Spécification :** `new-kadre/DESIGN.md`

## Contraintes globales

- Breaking changes autorisés ; aucun maintien permanent de l’API actuelle.
- Coroutines obligatoires dans l’API principale.
- Aucun renderer, widget ou layout dans Kadre.
- Aucun état global applicatif.
- Aucun buffer non borné.
- Aucun faux succès pour une fonctionnalité non disponible.
- Temps et délais monotones.
- `explicitApi()` sur chaque module publié.
- Chaque chantier laisse le dépôt compilable et vérifiable.
- Chaque nouvelle abstraction est introduite par tests avant migration des backends.
- Chaque backend doit passer la même suite de contract tests.

## Graphe de dépendances

```text
1. Frontière publique et fondations
            |
2. Session, lifecycle et Host SPI
       /          |           \
3. Fenêtres   4. Input      5. Capture
       \          |           /
        \---------+----------/
                  |
      6. Android  7. iOS  8. Web  9. Desktop
                  |
10. Tests publics, benchmarks, samples et documentation
                  |
11. Suppression finale de l’ancienne API et validation globale
```

Les chantiers 3, 4 et 5 peuvent avancer en parallèle uniquement après stabilisation des contrats du chantier 2. Les migrations de plateformes peuvent avancer en parallèle lorsqu’elles ne modifient plus les interfaces communes.

## Stratégie de commits

Chaque lot suit ce cycle :

1. ajouter un test de contrat qui échoue ;
2. introduire le contrat ou l’adaptateur minimal ;
3. exécuter les tests ciblés ;
4. exécuter la validation ABI concernée ;
5. committer un état autonome et relisible.

Les suppressions massives n’ont lieu qu’au chantier 11, après migration de tous les consumers internes.

---

## Chantier 1 — Frontière publique et fondations

### But

Créer la nouvelle surface publique sans encore migrer les backends natifs.

### Portée actuelle

- `kadre/build.gradle.kts`
- `kadre-core/build.gradle.kts`
- `kadre-coroutines/build.gradle.kts`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/`
- `kadre-test/`

### Livrables

1. Activer `explicitApiWarning()` sur les modules publiés, corriger les déclarations accidentelles, puis passer à `explicitApi()` avant la fin du chantier.
2. Créer les annotations `ExperimentalKadreApi`, `KadrePlatformApi` et `DelicateKadreApi`.
3. Créer `KadreResult`, `KadreFailure`, `FeatureSupport`, les IDs opaques et les primitives de temps monotone.
4. Créer `KadrePolicy`, les trois profils et les policies métier de delivery.
5. Déplacer les contrats publics vers les packages cibles sous `org.graphiks.kadre`.
6. Rendre internes les bridges `PlatformGamepad*`, `FrameTimingTracer` et autres détails présents dans les dumps ABI.
7. Préparer la fusion de `kadre-coroutines` en déplaçant sa dépendance coroutine et ses contrats généraux vers `kadre`.
8. Ajouter des tests d’API qui interdisent les imports consommateurs depuis `org.graphiks.kadre.core`.
9. Créer une tâche racine `checkPublicApi` qui agrège la validation ABI de tous les modules encore publiés.

### Critères de sortie

- La nouvelle fondation compile sur JVM, Android, iOS, JS et Wasm.
- Les profils de delivery ont des tests déterministes d’overflow.
- Aucun type nommé `Platform*` non intentionnel n’apparaît dans les dumps ABI communs.
- L’ancienne API compile encore temporairement, mais tous les nouveaux travaux ciblent la nouvelle surface.

### Vérification

```bash
rtk ./gradlew :kadre:check :kadre-core:check :kadre-test:check
rtk ./gradlew :kadre:checkKotlinAbi :kadre-core:checkKotlinAbi
```

---

## Chantier 2 — KadreSession, lifecycle et Host SPI

### But

Établir le modèle d’exécution commun et supprimer toute dépendance conceptuelle à une event loop universellement bloquante.

### Fichiers cibles

- créer `kadre/src/commonMain/kotlin/org/graphiks/kadre/application/KadreApplication.kt`
- créer `kadre/src/commonMain/kotlin/org/graphiks/kadre/application/KadreScope.kt`
- créer `kadre/src/commonMain/kotlin/org/graphiks/kadre/application/KadreSession.kt`
- créer `kadre/src/commonMain/kotlin/org/graphiks/kadre/application/KadreLifecycle.kt`
- créer `kadre/src/commonMain/kotlin/org/graphiks/kadre/application/KadreHost.kt`
- créer les implémentations internes correspondantes dans `kadre-core`
- étendre `kadre-test` avec `FakeKadreHost` et `VirtualKadreClock`

### Livrables

1. Job racine de session et arbre d’ownership.
2. États `Starting`, `Running`, `Stopping`, `Stopped` et `Failed`.
3. `close()`, `stop()` et `join()` idempotents.
4. Lifecycle avec `StateFlow` courant et `Flow` de transitions.
5. Normalisation et déduplication des transitions hôtes.
6. Host SPI public et implémentation fake.
7. Diagnostics de session et propagation correcte des exceptions/cancellations.
8. Tests prouvant qu’aucun enfant ne survit au teardown.

### Critères de sortie

- Une `KadreApplication` complète s’exécute avec `FakeKadreHost`.
- Une erreur applicative termine la session et conserve sa cause.
- Une fermeture hôte annule fenêtres, devices et captures factices.
- Aucun `GlobalScope`, singleton de handler ou job détaché n’est utilisé.

### Vérification

```bash
rtk ./gradlew :kadre:check :kadre-test:check
```

---

## Chantier 3 — Fenêtres, capabilities et interop native

### But

Remplacer `WindowAttributes` et les setters winit-like par un modèle suspendu, observable et capability-driven.

### Fichiers actuels principaux

- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Window.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/WindowAttributes.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/WindowingTypes.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Monitor.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/RawHandles.kt`

### Fichiers cibles

- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/WindowManager.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/Window.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/WindowSpec.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/WindowState.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/WindowCapabilities.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/platform/PlatformHandles.kt`

### Livrables

1. `WindowManager.primary`, `windows` et `capabilities` en `StateFlow`.
2. `requestWindow` avec résultats `Opened` et `AcceptedByHost`.
3. DSL `WindowSpec` et snapshot immuable.
4. Snapshot atomique `WindowState`.
5. `Window.apply` et opérations contextuelles suspendues.
6. Capabilities dynamiques et résultats autoritaires.
7. Handles sous `@KadrePlatformApi`, constructeurs internes et lifetime documenté.
8. Fake windows dans `kadre-test`.
9. Contract tests de fermeture, focus, resize, fullscreen et absence de faux succès.

### Critères de sortie

- Tous les comportements de fenêtre sont exprimables sans appeler un setter de l’ancienne interface.
- Un backend fake peut changer dynamiquement ses capabilities.
- Un handle ne peut pas être construit par un consumer.
- `WindowState` ne présente jamais un mélange de deux transitions natives différentes.

### Vérification

```bash
rtk ./gradlew :kadre:check :kadre-test:check
```

---

## Chantier 4 — Input, périphériques, gamepad et IME

### But

Fournir une API d’entrée ordonnée, observable et adaptée aux applications temps réel.

### Fichiers actuels principaux

- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Events.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/GamepadController.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/GamepadTypes.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/GamepadEvent.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/ImeCapabilities.kt`

### Livrables

1. `DeviceManager`, inventaire et lifecycle des devices.
2. `WindowInput` avec ordre total des événements par fenêtre.
3. Événements clavier, pointeur, tactile et gestes avec timestamp monotone.
4. États clavier, modifiers et pointeurs en `StateFlow`.
5. `Gamepad` avec snapshot d’état, événements, capabilities et effets.
6. Collections spécialisées `ButtonValues` et `AxisValues`.
7. Suppression des fallbacks d’ordinal silencieux.
8. `TextInputSession` avec cursor rect et surrounding text.
9. `RawInputAccess` sous policy et opt-in appropriés.
10. Tests de saturation pour événements discrets et continus.

### Critères de sortie

- Les événements press/release conservent leur ordre ou produisent un diagnostic explicite.
- Les mouvements peuvent être coalescés par policy.
- Un snapshot gamepad est lisible sans créer de mapping mutable public.
- Fermer une fenêtre ferme sa session IME.
- Aucun type natif clavier n’est requis dans le chemin portable.

### Vérification

```bash
rtk ./gradlew :kadre:check :kadre-test:check :benchmarks:jmh-core:check
```

---

## Chantier 5 — Capture et ownership des frames

### But

Rendre la capture observable, permission-aware et sûre pour les buffers natifs.

### Fichiers actuels principaux

- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/capture/`
- implémentations `capture/` de chaque backend

### Livrables

1. `CaptureManager` attaché à `KadreScope`.
2. Permissions et sources en `StateFlow`.
3. IDs de source typés.
4. `CaptureRequest` avec rate, région, format, cursor et delivery policy.
5. `CaptureSession` enfant de la session applicative.
6. `CaptureFrame` closeable et `PixelPlane` read-only.
7. Fermeture automatique des frames droppées.
8. Chemin sûr `copyPixels()` et zero-copy sous opt-in.
9. Fake capture avec révocation et perte de source.
10. Contract tests de teardown, buffer pool et overflow.

### Critères de sortie

- Aucun `ByteArray` mutable n’est exposé comme valeur structurelle publique.
- Chaque frame native a exactement un chemin de libération.
- Une permission révoquée termine la capture sans terminer arbitrairement l’application.
- `ScreenCapturer.resolve()` n’est plus nécessaire.

### Vérification

```bash
rtk ./gradlew :kadre:check :kadre-test:check
```

---

## Chantier 6 — Migration Android

### But

Remplacer le handler global Android par des sessions attachées aux hôtes.

### Fichiers principaux

- `kadre-android/src/androidMain/`
- `kadre/src/androidMain/kotlin/org/graphiks/kadre/EventLoop.android.kt`
- samples Android et Compose

### Livrables

1. `ComponentActivity.attachKadre`.
2. `View.attachKadre`.
3. Liaison `LifecycleOwner` et teardown `onDestroy`.
4. Surface/window principale alimentée par l’hôte.
5. Input, IME, gamepad et capture migrés vers les nouveaux contrats.
6. Capabilities Android honnêtes pour resize, multi-window, cursor et capture.
7. Suppression de `AndroidKadreRuntime.currentHandler`.
8. Intégration Compose sans renderer ni widget Kadre.
9. Migration de tous les samples Android.

### Critères de sortie

- Deux activités peuvent posséder deux sessions indépendantes.
- Détruire une activité n’affecte pas une autre session.
- Aucun handler process-global.
- Les tests host Android couvrent recreation et fermeture.

### Vérification

```bash
rtk ./gradlew :kadre-android:check
rtk scripts/test-android-device-selection.sh
```

---

## Chantier 7 — Migration UIKit, scènes iOS et SwiftUI

### But

Aligner Kadre sur le lifecycle multi-scène UIKit.

### Fichiers principaux

- `kadre-uikit/src/iosMain/`
- `kadre/src/ios*Main/kotlin/org/graphiks/kadre/EventLoop.*.kt`
- samples iOS et bridges Swift

### Livrables

1. `KadreIos.attach(windowScene, application)`.
2. Une session et un scope par `UIWindowScene`.
3. Bridge `UISceneDelegate` complet.
4. Fermeture sur `sceneDidDisconnect`.
5. Propagation des événements globaux réellement applicables.
6. Fenêtre principale fournie par la scène.
7. Requête de scène supplémentaire avec `AcceptedByHost`.
8. Host `UIViewController` pour SwiftUI et `UIViewControllerRepresentable`.
9. Input, IME, gestes, gamepad et capture migrés.
10. Suppression des registries globaux de session.

### Critères de sortie

- Deux scènes iPadOS n’échangent ni fenêtres ni jobs.
- Déconnecter une scène ferme uniquement sa session.
- Le bridge Swift ne contient pas de logique applicative.
- UIKit et SwiftUI utilisent le même contrat Kotlin partagé.

### Vérification

```bash
rtk scripts/test-uikit-simulator.sh
```

---

## Chantier 8 — Migration Web JS et Wasm

### But

Attacher Kadre à des éléments DOM existants avec un contrat identique en JS et Wasm.

### Fichiers principaux

- `kadre-web-common/src/webMain/`
- `kadre/src/jsMain/kotlin/org/graphiks/kadre/EventLoop.js.kt`
- `kadre/src/wasmJsMain/kotlin/org/graphiks/kadre/EventLoop.wasmJs.kt`
- `kadre-js/` et `kadre-wasm/`

### Livrables

1. `HTMLElement.attachKadre` et `HTMLCanvasElement.attachKadre`.
2. Lifecycle fondé sur DOM attachment, visibility et focus.
3. Support de plusieurs sessions par page.
4. Suppression de l’utilisation du titre comme ID de canvas.
5. Window/input/capture migrés vers les contrats communs.
6. Policies adaptées à `requestAnimationFrame` sans prétendre fournir un renderer.
7. Traitement local et explicite des opt-ins Wasm interop.
8. Suppression du faux contrat `runApp`.
9. Migration des samples Web.

### Critères de sortie

- Le même code partagé fonctionne en JS et Wasm.
- Deux canvases peuvent héberger deux sessions indépendantes.
- Retirer un élément ferme ou suspend selon une policy documentée.
- Aucun warning Wasm interop non traité dans les fichiers migrés.

### Vérification

```bash
rtk scripts/test-web-browsers.sh
rtk ./gradlew :kadre:jsTest :kadre:wasmJsTest
```

---

## Chantier 9 — Migration Desktop

### But

Unifier AppKit, Win32, X11 et Wayland derrière le host desktop et conserver une commodité standalone honnête.

### Fichiers principaux

- `kadre-appkit/src/jvmMain/`
- `kadre-win32/src/jvmMain/`
- `kadre-x11/src/jvmMain/`
- `kadre-wayland/src/jvmMain/`
- `kadre/src/jvmMain/`

### Livrables

1. `DesktopHostOptions` et sélection typée du backend.
2. `CoroutineScope.attachKadreDesktop`.
3. `runKadreApplication` construit au-dessus du host.
4. Marshalling sûr vers le thread propriétaire.
5. Migration de WindowManager, input, gamepad et capture pour chaque backend.
6. Diagnostic `BackendFallback` uniquement pendant la sélection initiale.
7. Aucun fallback après échec de démarrage.
8. Handles natifs sous `@KadrePlatformApi`.
9. Suppression des event loops publiques de backend.
10. Migration des samples desktop.

### Critères de sortie

- Le même `KadreApplication` fonctionne sur les quatre familles desktop.
- La sémantique de `KadreSession` ne dépend pas du backend.
- Toute opération native est exécutée sur son thread propriétaire.
- Les backends absents produisent une erreur actionnable, pas une erreur de réflexion opaque.

### Vérification

```bash
rtk scripts/test-appkit-runtime.sh
rtk scripts/test-x11-xvfb.sh
rtk scripts/wayland-test.sh
rtk ./gradlew :kadre-win32:check
```

---

## Chantier 10 — API de test, contrats, benchmarks, samples et documentation

### But

Transformer les garanties architecturales en preuves exécutables et en parcours d’adoption.

### Livrables

1. Finaliser `runKadreTest` et tous les contrôleurs virtuels.
2. Extraire une contract suite commune consommée par chaque backend.
3. Ajouter des tests de lifecycle, threading, capabilities, overflow, handles et permissions.
4. Mesurer la baseline avant suppression de l’ancien chemin.
5. Ajouter les benchmarks input, state, gamepad et capture.
6. Créer trois samples de référence : utilitaire, jeu, site Web.
7. Écrire les guides Android, UIKit, SwiftUI, Web et Desktop.
8. Écrire les guides policies, structured concurrency, interop renderer et migration.
9. Générer la matrice de capabilities à partir des résultats de contrats.
10. Vérifier que la documentation ne promet ni rendu ni widgets.

### Critères de sortie

- Chaque exemple public est couvert par un test ou sample compilé.
- La matrice de capabilities correspond aux résultats des backends.
- Les benchmarks enregistrent une baseline versionnée.
- Un consumer peut tester son application sans backend natif.

### Vérification

```bash
rtk ./gradlew check
rtk ./gradlew :benchmarks:jmh-core:jmh
```

---

## Chantier 11 — Suppression finale et validation globale

### But

Retirer toute l’ancienne surface une fois les backends et consumers internes migrés.

### Suppressions

- `ApplicationHandler`.
- `ActiveEventLoop` et `EventLoopProxy` sous leur forme actuelle.
- les deux classes `EventLoop`.
- `WindowAttributes`.
- les méthodes `setX` et résultats winit-like remplacés.
- `ScreenCapturer.resolve()`.
- les IDs `Long`/`data class` incohérents.
- les buffers publics mutables.
- les typealiases de `KadreApi.kt`.
- l’artifact `kadre-coroutines`.
- les imports `org.graphiks.kadre.core` dans samples et docs.
- les déclarations publiques de backend non prévues par la spec.

### Livrables

1. Supprimer le code mort et les dumps ABI historiques devenus sans objet.
2. Régénérer volontairement les nouveaux dumps ABI.
3. Rechercher les imports, symboles et docs de l’ancienne API.
4. Exécuter tous les tests disponibles sur chaque host CI.
5. Publier un guide de migration unique pour le prochain snapshot d’incubation.
6. Vérifier la surface publique avec `explicitApi()` strict.
7. Vérifier qu’aucun warning d’opt-in non intentionnel n’est ignoré globalement.

### Critères de sortie

- Aucun consumer interne n’importe `org.graphiks.kadre.core`.
- Aucun ancien `EventLoop` ou `ApplicationHandler` dans l’ABI.
- Tous les modules publiés utilisent `explicitApi()` strict.
- Toutes les contract suites disponibles sont vertes.
- Le dépôt contient une seule architecture publique documentée.

### Vérification finale

```bash
rtk rg -n "ApplicationHandler|ActiveEventLoop|WindowAttributes|org\.graphiks\.kadre\.core" samples docs kadre-* --glob '*.kt' --glob '*.md'
rtk ./gradlew check
rtk ./gradlew checkPublicApi
```

## Risques principaux et réponses

### Coroutines dans les callbacks natifs

**Risque :** blocage du thread UI ou perte silencieuse.

**Réponse :** ingress non bloquant, buffers bornés, lanes discrètes/continues et diagnostics d’overflow.

### Migration trop horizontale

**Risque :** dépôt non compilable pendant plusieurs semaines.

**Réponse :** nouveaux contrats d’abord, migrations verticales backend par backend, ancienne API supprimée seulement au chantier 11.

### API générique au plus petit dénominateur commun

**Risque :** faux support et no-op silencieux.

**Réponse :** capabilities dynamiques, `KadreResult`, résultats spécifiques comme `AcceptedByHost`, extensions sous opt-in.

### Coût de Flow pour le temps réel

**Risque :** allocations et latence pour les jeux.

**Réponse :** snapshots `StateFlow`, collections spécialisées, profils Realtime, benchmarks et buffers bornés.

### Ownership des frames et handles

**Risque :** use-after-close ou fuite native.

**Réponse :** ressources closeable, constructeurs internes, lifetime lié à la session et contract tests de teardown.

### Explosion du nombre d’options

**Risque :** API illisible.

**Réponse :** trois profils documentés, policies métier composables, détails coroutine conservés en interne.

## Ordre de publication des snapshots d’incubation

1. Snapshot A : fondations, session et fake host.
2. Snapshot B : fenêtres et input avec un backend pilote desktop.
3. Snapshot C : capture et Android.
4. Snapshot D : UIKit et Web.
5. Snapshot E : tous les backends desktop.
6. Snapshot F : suppression de l’ancienne API et surface candidate pour stabilisation.

Chaque snapshot peut casser le précédent, mais doit fournir des release notes et un guide de migration correspondant.
