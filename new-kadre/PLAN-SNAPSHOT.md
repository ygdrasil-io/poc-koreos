# New Kadre — Snapshot du plan directeur

> Ce document ordonne la refonte complète de l’API publique. Il ne remplace pas les plans d’implémentation task-by-task qui seront dérivés après validation finale de `new-kadre/DESIGN.md`.

> Ce snapshot reste exclusivement documentaire : aucune génération de source Kotlin n’a lieu avant la fermeture du catalogue public du domaine concerné.

**Objectif :** remplacer l’API winit-like par une API Kotlin Multiplatform coroutine, embeddable-first, capability-driven et sans état global.

**Architecture :** `kadre` devient l’unique API principale. Une `KadreSession` attachée à un host possède sa hiérarchie coroutine, sa surface hôte, ses fenêtres top-level, sa vue des displays, ses périphériques et ses captures. Les backends traduisent les contrats communs vers leurs threads et lifecycles natifs.

**Stack :** Kotlin Multiplatform, kotlinx.coroutines, Flow/StateFlow, Kotlin Time, Gradle KMP, Kotlin ABI validation, Kotlin/Native, Kotlin/JS et Kotlin/Wasm.

**Spécification :** `new-kadre/DESIGN.md`

**Registre ABI :** `new-kadre/API-MIGRATION.md`

## Contraintes globales

- Breaking changes autorisés ; aucun maintien permanent de l’API actuelle.
- Coroutines obligatoires dans l’API principale.
- Aucun renderer, widget ou layout dans Kadre.
- Aucun état global applicatif.
- Les brokers internes process-wide imposés par l’OS sont autorisés sans session courante et avec enregistrement reference-counted.
- Aucun buffer ni fan-out d’événements non borné ; les profiles fixent des limites de collectors par flow et par session, ainsi que des budgets finis de ressources, payloads, images, fenêtres, requêtes, captures et drop transfers. Les `StateFlow` conservent un snapshot producteur unique et ne refusent jamais un collector.
- Aucun faux succès pour une fonctionnalité non disponible.
- Une `HostSurface` n’est jamais présentée comme une fenêtre top-level fictive.
- Les opérations dépendant d’une user activation ou d’un serial natif utilisent un contexte d’interaction transitoire explicite.
- Tout value object publié est profondément immuable ; une collection de handles est un snapshot immuable d’appartenance dont les ressources restent vivantes et identifiables.
- Temps et délais monotones.
- Un timeout exige une opportunité de scheduling : il borne le cleanup coopératif mais ne promet ni préemption hard real-time ni exécution après destruction du runtime.
- Chaque événement public est estampillé et chaque snapshot est mis à jour avant sa transition observable.
- Les flux d’événements sont hot, multicast et sans replay ; la capture closeable utilise un collector unique encadré.
- Une erreur de session ne cancelle pas le scope de son host ; l’annulation du host ferme sa session.
- Une nouvelle scène utilise une nouvelle application fournie par `KadreApplicationFactory`.
- `explicitApi()` sur chaque module publié.
- Chaque chantier laisse le dépôt compilable et vérifiable.
- Chaque nouvelle abstraction est introduite par tests avant migration des backends.
- Chaque backend doit passer la même suite de contract tests.
- `KadrePolicies.Default` est accepté par tout host adapter déclaré supporté ; seuls un profil avancé ou custom peuvent échouer avec `UnsupportedPolicy`.
- Un invariant réparti entre plusieurs champs utilise un snapshot composé révisionné ; plusieurs `StateFlow` indépendants ne promettent pas une transaction implicite.
- Les valeurs numériques publiques excluent `NaN`, les infinis et les durées infinies ; les chaînes sémantiques ne sont ni normalisées ni tronquées silencieusement.
- Toute mutation suspendue possède des points d’admission et de commit ; une cancellation après commit détache le waiter sans rollback implicite.

## Graphe de dépendances

```text
1. Frontière publique et fondations
            |
2. Session, lifecycle et Host SPI
       /          |           \
3. Surfaces, displays et fenêtres   4. Input      5. Capture
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

## Gate documentaire avant implémentation

Avant le premier changement de source de chaque chantier, le domaine doit disposer d’un catalogue Markdown exhaustif des déclarations publiques : packages, signatures, sealed variants, unités, nullabilité, ownership, threading, flows, failures, capabilities et exports interop. Les formulations ouvertes comme « au minimum » bloquent ce gate. La revue de ce catalogue précède les tests et le code ; le dump ABI cible n’est généré qu’après approbation documentaire.

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
3. Créer `KadreResult`, la taxonomie stable de `KadreFailure`, `Capability`, `FeatureAvailability`, les IDs opaques, `EventStamp` et les primitives de temps monotone.
4. Ajouter les combinators idiomatiques de `KadreResult` et `KadreException` pour l’interop avec le style exception.
5. Créer `KadrePolicy`, les trois profils, les files bornées distinctes ingress/collector, les limites de fan-out/ressources et les compteurs de diagnostics monotones, non perdables et explicitement saturables.
6. Déplacer les contrats publics vers les packages cibles sous `org.graphiks.kadre`.
7. Rendre internes les bridges `PlatformGamepad*`, `FrameTimingTracer` et autres détails présents dans les dumps ABI.
8. Préparer la fusion de `kadre-coroutines` en déplaçant sa dépendance coroutine et ses contrats généraux vers `kadre`.
9. Ajouter des tests d’API qui interdisent les imports consommateurs depuis `org.graphiks.kadre.core`.
10. Matérialiser `new-kadre/API-MIGRATION.md` dans une tâche qui classe chaque symbole ABI comme `keep/move`, `replace`, `internalize` ou `remove`, et échoue sur tout symbole non couvert.
11. Créer une tâche racine `checkPublicApi` qui agrège cette couverture et la validation ABI de tous les modules encore publiés.
12. Faire de `Default` le minimum obligatoire des hosts supportés, limiter les collectors d’événements par flow/session et ajouter les budgets d’interactions, fenêtres, requêtes, payloads, images, captures, effets gamepad et drop transfers.
13. Produire avant le code le catalogue documentaire exhaustif du chantier 1 et faire échouer la revue si une forme publique reste ouverte.
14. Ajouter `ShutdownTimedOut`, `CaptureDeliveryPolicy.events` et une table fermée associant chaque `Flow` public à sa policy, son owner et son action `CloseSource`.

### Critères de sortie

- La nouvelle fondation compile sur JVM, Android, iOS, JS et Wasm.
- Les profils de delivery ont des tests déterministes d’overflow.
- Aucun type nommé `Platform*` non intentionnel n’apparaît dans les dumps ABI communs.
- Chaque symbole de la baseline ABI possède exactement une décision de migration.
- Le rapport liste séparément chaque symbole absorbé par une règle résiduelle ; aucun match résiduel non approuvé ne subsiste.
- Une collection de `StateFlow` ne peut pas être refusée par un budget Kadre.
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
2. `SupervisorJob` isolant le host et sous-arbre applicatif à échec ordinaire.
3. Contexte de `KadreScope` obtenu en remplaçant uniquement le `Job` du parent, dispatcher hérité et arrêt applicatif explicite.
4. `KadreApplicationFactory`, contexte de lancement, token de restoration opaque et création indépendante par session.
5. États `Starting`, `Running`, `Stopping` et `Terminated(SessionOutcome)`.
6. `close()`, arrêts host/application, auto-cancellation et `awaitTermination()` idempotents, avec protection contre l’attente depuis un enfant et distinction entre terminaison logique et achèvement physique d’un consumer non coopératif.
7. Lifecycle orthogonal attachment/visibility/activation, signaux de pression mémoire et mapping normatif de chaque host.
8. Contrats hot/cold, replay, cardinalité, terminaison, ordre state/event, `EventDeliverySpan` pour coalescing/remplacement et limites agrégées des seuls flows d’événements.
9. Host SPI expérimental retournant `KadreResult<KadreSession>` et implémentation fake.
10. Infrastructure de brokers internes sans session courante et routing testable entre plusieurs sessions.
11. Diagnostics redacted par défaut et propagation correcte des exceptions/cancellations.
12. Teardown ordonné et borné lorsque le runtime reste schedulable : aucune coroutine interne Kadre active ne survit, l’accès aux ressources est révoqué à la première opportunité après le timeout et un job consumer non coopératif peut rester annulé et physiquement incomplet sans garder de ressource Kadre.
13. Sémantique commune de tous les `AutoCloseable` : admission fermée immédiatement, `close()` non bloquant, thread-safe et idempotent, état terminal conservé.
14. Tests prouvant l’ordre croissant dans chaque flow et l’absence de promesse d’ordre de livraison entre flows distincts.
15. Fermeture de l’admission des enfants dès le retour du corps de `KadreApplication.run`; un receiver capturé ne peut pas prolonger la session après ce point.
16. Point de linéarisation entre `attach`, détachement du host et passage à `Running`, sans démarrage applicatif après admission d’un motif terminal.
17. Règle commune de cancellation : un waiter ne possède pas l’opération attendue, sauf streaming explicitement owner comme `collectFrames`; toute factory suspendue conserve l’owner jusqu’au handoff de son retour et le ferme si le caller est annulé avant.
18. Thread-safety par défaut pour managers, handles, getters et méthodes non suspendues, avec confinement explicite des builders, callbacks transitoires et leases.
19. Drain garanti uniquement à la fermeture normale d’une ressource vers les collectors encore actifs ; le teardown de session s’appuie sur les snapshots terminaux.
20. Validation synchrone d’un `parentScope` contenant un `Job` actif et règle de priorité où la première failure non-cancellation admise reste primaire devant les failures de cleanup.

### Critères de sortie

- Une `KadreApplication` complète s’exécute avec `FakeKadreHost`.
- Une erreur applicative termine la session avec un `ApplicationFailure` stable et transmet sa cause originale au reporter du host.
- Une erreur de session n’annule pas le scope du host, tandis qu’une annulation du host ferme la session.
- Une application peut demander son propre arrêt sans annuler arbitrairement le scope du host.
- Une fermeture hôte annule fenêtres, devices et captures factices.
- Aucun `GlobalScope`, singleton de handler/session courante ou job détaché n’est utilisé ; un broker process-wide ne contient que l’état natif partagé autorisé.
- Une coroutine applicative volontairement non coopérative ne bloque ni le teardown logique ni `awaitTermination()` après la première opportunité de scheduling suivant `shutdownTimeout`, et ne conserve aucun accès Kadre vivant ; monopoliser l’unique dispatcher retarde explicitement l’observation de cette échéance.

### Vérification

```bash
rtk ./gradlew :kadre:check :kadre-test:check
```

---

## Chantier 3 — Surfaces, displays, fenêtres et interop native

### But

Séparer les surfaces fournies par l’hôte des fenêtres top-level, puis remplacer `WindowAttributes` et les setters winit-like par un modèle suspendu, observable et capability-driven.

### Fichiers actuels principaux

- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Window.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/WindowAttributes.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/WindowingTypes.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Monitor.kt`
- `kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/RawHandles.kt`

### Fichiers cibles

- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/WindowManager.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/surface/HostSurface.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/surface/InteractionContext.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/display/DisplayManager.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/Window.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/WindowSpec.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/WindowState.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/window/WindowCapabilities.kt`
- `kadre/src/commonMain/kotlin/org/graphiks/kadre/platform/PlatformHandles.kt`

### Livrables

1. `KadreScope.primarySurface` et `HostSurface` pour les régions possédées par le host, sans opérations top-level fictives.
2. `DisplayManagerState` atomique regroupant inventaire permission-aware, capabilities et révision, plus displays vivants révisés et coordonnées physiques du bureau virtuel.
3. `WindowManagerState` atomique regroupant fenêtre primaire, liste des fenêtres top-level, capabilities et révision ; `primary` est toujours `null` ou membre exact de `windows`.
4. `WindowRequest` observable séparant `Pending` du `WindowRequestOutcome` terminal, avec cancellation typée et opérations irréversibles.
5. DSL `WindowSpec`, value objects profondément immuables et identité stable des handles vivants.
6. Snapshots atomiques `SurfaceState` et `WindowState`, chacun avec révision et état terminal explicite.
7. `Window.apply` non transactionnel avec résultats `Applied`, `PartiallyApplied` et `Accepted`, sérialisation, operation IDs et races close/apply définies.
8. `InteractionContext` synchrone, sérialisé et non réentrant pour user activation/serial, plus une action pré-armable par surface avec trigger, expiration et budget.
9. Capabilities séparant support structurel, disponibilité dynamique, interaction requise et contraintes typées.
10. Handles sous `@KadrePlatformApi`, constructeurs internes et lifetime documenté.
11. Fake surfaces, displays, interactions et windows dans `kadre-test`.
12. Contract tests de fermeture, focus, resize, fullscreen, expiration d’interaction et absence de faux succès.
13. Protocole de fermeture fenêtre distinguant requête différable, rejet, fermeture forcée, commit asynchrone et snapshot terminal.
14. Ordre de détachement surface → input/events → retrait de `primarySurface`, avec handles terminaux encore lisibles.
15. Lifecycle d’un `Display` retiré : état terminal avant retrait d’inventaire, événement après les snapshots et nouvel ID après réapparition.
16. Cancellation de `WindowRequest.await` et `ArmedInteraction.await` limitée au waiter ; seules les méthodes explicites modifient l’owner.
17. Inventaire display trop grand rendu terminalement `Unavailable(ResourceLimitExceeded)` sans liste partielle ; `WindowManagerState` met atomiquement à jour appartenance, primaire et capabilities.

### Critères de sortie

- Tous les comportements de fenêtre sont exprimables sans appeler un setter de l’ancienne interface et aucun host surface n’est forcé dans ce modèle.
- Un backend fake peut changer dynamiquement ses capabilities.
- Un handle ne peut pas être construit par un consumer.
- `WindowState` ne présente jamais un mélange de deux transitions natives différentes.
- Les opérations nécessitant un geste réussissent uniquement pendant un `InteractionContext` valide ou via une action pré-armée.
- Une requête de nouvelle scène reste corrélée jusqu’à la nouvelle session ou son rejet terminal.
- Une fermeture demandée par le host peut être acceptée ou rejetée lorsque la capability le permet ; un host incapable d’attendre annonce une fermeture forcée sans faux `CloseRequested`.

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

1. `DeviceManagerState` atomique regroupant disponibilité, inventaires complets des devices/gamepads et révision ; aucune liste partielle n’est publiée comme complète.
2. `SurfaceInput` avec ordre total des événements par surface.
3. Événements clavier, pointeur, tactile et gestes avec `EventStamp` monotone et séquence de session.
4. `SurfaceInputState` atomique regroupant clavier, modifiers, pointeurs, capabilities et révision dans un seul `StateFlow`.
5. `Gamepad` avec snapshot atomique descriptor/connexion/routing/contrôles/capabilities, neutralisation lors d’une suspension de routing, événements révisionnés et `GamepadEffectSession` owned sous budget agrégé.
6. Collections spécialisées `ButtonValues` et `AxisValues`.
7. Suppression des fallbacks d’ordinal silencieux.
8. `TextInputSession` unique par surface, cursor rect logique, surrounding text indexé en UTF-16 et révision de document sur chaque échange IME.
9. `InputEvent.StateReset` après publication atomique d’un `SurfaceInputState` neutre sur perte de focus, déconnexion ou révocation ; un overflow publie directement le snapshot terminal et une terminaison typée hors de la lane saturée.
10. Drag-and-drop par offre/transfer sans faux chemin portable, acceptation via interaction, état fermé de l’offre, handoff single-winner, claim timeout et chunks bornés.
11. `RawInputAccess` sous policy, permission, routing de session et opt-in appropriés.
12. Broker gamepad partagé sans IDs cross-session et arbitrage explicite des effets.
13. Tests de saturation pour événements discrets et continus, limites de collectors d’événements comprises.
14. Tests d’edit IME obsolète, de régression de révision et de cursor rect calculé sur une ancienne révision.
15. Scheduler mixte borné pour `SurfaceInput.events` et `Gamepad.events`, avec lane FIFO discrète, lanes continues et barrières de séquence empêchant tout coalescing à travers un événement discret.
16. Cancellation de `GamepadEffectSession.awaitTermination` limitée au waiter.
17. `DropItemReadMode`, une seule lecture active par transfert, distinction replayable/single-use, résultats partiels bornés et effets de cancellation/close définis ; le transfer reste compté dans le budget jusqu’à sa fermeture effective.

### Critères de sortie

- Les événements press/release conservent leur ordre ou produisent un diagnostic explicite.
- Les mouvements peuvent être coalescés par policy.
- Un snapshot gamepad est lisible sans créer de mapping mutable public.
- Fermer une surface ou sa fenêtre propriétaire ferme sa session IME.
- Une perte de focus ne laisse aucune touche, bouton ou contact bloqué et ne synthétise pas de faux release.
- Deux sessions ne peuvent piloter le même effet exclusif sans résultat `AlreadyInUse`.
- Aucun type natif clavier n’est requis dans le chemin portable.
- Les coordonnées logiques/physiques et l’ordre snapshot/événement sont identiques sur tous les backends.
- Une lane input saturée n’a pas besoin d’accepter un dernier événement pour rendre son état terminal observable.
- Une policy continue ne peut ni réordonner un événement discret ni créer une file cachée non bornée.
- Aucun transfert de drop ne livre plus de `maxBytes`, et un succès signifie toujours que l’item complet a été livré.
- Une seule coroutine gagne `claimTransfer`; annuler un waiter ne consomme pas le handoff et un transfer non claimé expire selon la policy.
- Une suspension de routing gamepad neutralise le snapshot sans masquer connexion ou déconnexion physique, puis une reprise republie l’état physique courant avant les nouveaux contrôles.

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
2. Permission, capabilities et sources dans un unique `CaptureManagerState` révisionné, avec `HostPickerOnly` pour les sources non énumérables et `Unavailable(failure)` lorsqu’aucun inventaire complet ne peut être publié.
3. `CaptureSource` descriptor immuable lié à la révision d’inventaire, IDs typés, détection des requêtes obsolètes et nouvel ID après retrait/réapparition.
4. `CaptureRequest` avec rate, région, format, cursor et delivery policy.
5. `CaptureSession` enfant de `KadreSession`, `CaptureOutcome` et raisons terminales stables.
6. `open` réserve sans produire ; le premier et unique `collectFrames` démarre la source et encadre sa fermeture.
7. `CaptureFrame` closeable, `PixelPlaneLayout` immuable sans vue native, `CopiedPixelPlane` associant layout et bytes possédés, color encoding complet, timestamp média optionnel, durée et discontinuité.
8. Fermeture automatique des frames consommées, droppées, remplacées ou interrompues.
9. Chemin sûr `copyPlanes()` et zero-copy retenable sous opt-in.
10. `CaptureConfiguration` effective complète dans l’état `Streaming` et dans `Reconfigured`, avec cadence `Fixed`, `Variable` ou `Unknown`, publiée avant l’admission de la première frame concernée.
11. Fake capture avec révocation et perte de source.
12. Contract tests de collector unique-ever, outcomes, teardown, reconfiguration, buffer pool, budget total en octets et overflow.
13. `CaptureDeliveryPolicy.events`, `FrameDelivery` limité à `Latest` ou `Buffered` sans coalescing de frames, résultats normatifs de `collectFrames`, cancellation non propriétaire de `awaitTermination` et garde anti-deadlock depuis le collector actif.
14. Cancellation d’un picker : détachement du waiter, annulation best-effort seulement si exclusive et aucune session abandonnée lors d’un résultat tardif.
15. `refreshSources()` peut récupérer d’un inventaire `Unavailable` uniquement en publiant une nouvelle révision complète ; aucun faux inventaire vide ni liste tronquée.

### Critères de sortie

- Aucun `ByteArray` adossé à un buffer natif n’est exposé ; seules des copies détenues par l’application quittent la lease.
- Chaque frame native est libérée exactement une fois, quel que soit le chemin de terminaison.
- Une permission révoquée termine la capture sans terminer arbitrairement l’application.
- Une perte de source et une révocation ne peuvent pas produire le même outcome.
- Une capture ou reconfiguration qui dépasserait `maxBufferedBytesPerSession` échoue avant d’admettre une frame hors budget.
- `EventStamp` d’arrivée et timestamp média de source ne sont jamais confondus.
- `ScreenCapturer.resolve()` n’est plus nécessaire.
- Les layouts de plane restent lisibles après fermeture, mais aucun buffer natif ni offset vers son backing storage n’est exposé.
- Toute frame référence une configuration complète déjà observable avec la même révision.
- L’ordre entre le flow d’événements et le stream de frames n’est jamais supposé à la réception ; la révision embarquée dans chaque frame suffit à l’interpréter.

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
3. Liaison `LifecycleOwner`, refus des doubles attachments et teardown `onDestroy`.
4. `HostSurface` principale pour `View`, et fenêtre top-level distincte uniquement lorsque l’`Activity` en fournit une.
5. Input, IME, gamepad et capture migrés vers les nouveaux contrats.
6. Capabilities Android honnêtes pour resize, multi-window, cursor et capture.
7. Suppression de `AndroidKadreRuntime.currentHandler`.
8. Intégration Compose sans renderer ni widget Kadre.
9. Migration de tous les samples Android.
10. Contrat de recréation : nouvelle session après changement de configuration, sans rétention implicite des jobs.

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

1. `KadreIos.attach(windowScene, applicationFactory, policy)`.
2. Une session et un scope par `UIWindowScene`.
3. Bridge `UISceneDelegate` complet.
4. Fermeture sur `sceneDidDisconnect`.
5. Propagation des événements globaux réellement applicables, dont la pression mémoire.
6. Fenêtre top-level et surface de contenu distinctes fournies par la scène ou le host SwiftUI.
7. Requête de scène supplémentaire corrélée jusqu’à `OpenedInNewSession` ou `Rejected`.
8. Host `UIViewController` pour SwiftUI et `UIViewControllerRepresentable`.
9. Input, IME, gestes, gamepad et capture migrés.
10. Suppression des registries globaux de session.
11. Factory conservée par le host et contexte `AdditionalHostRequested` transmis à la nouvelle application.

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
10. `WebAttachmentPolicy` avec `StopWhenDetached` par défaut et arrêt manuel explicite.
11. `WebWindowProvider` optionnel ; aucun élément DOM ou popup créé implicitement et toute nouvelle browsing context produit une nouvelle session.
12. Interaction handler pour fullscreen, pointer lock, drop et popup soumis à transient user activation.
13. Contrat best-effort explicite pour `pagehide`/unload, sans promesse de teardown suspendu après destruction du runtime.

### Critères de sortie

- Le même code partagé fonctionne en JS et Wasm.
- Deux canvases peuvent héberger deux sessions indépendantes.
- Un canvas ou élément attaché n’apparaît jamais dans `WindowManager.state.value.windows`.
- Retirer un élément ferme la session en `StopWhenDetached` ou la laisse explicitement attachée en `Manual`.
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
3. `runKadreApplication` Desktop-only, bloquant et construit au-dessus du host embarquable.
4. Marshalling sûr vers le thread propriétaire.
5. Migration de WindowManager, input, gamepad et capture pour chaque backend.
6. Diagnostic `BackendFallback` uniquement pendant la sélection initiale.
7. Aucun fallback après échec de démarrage.
8. Handles natifs sous `@KadrePlatformApi`.
9. Suppression des event loops publiques de backend.
10. Migration des samples desktop.
11. Sémantique explicite de la dernière fenêtre : session conservée en embedded, arrêt demandé en standalone.
12. Intégrations de boucle embedded typées pour AppKit, AWT/Compose, JavaFX ou pump fourni ; refus des doubles boucles cachées.

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
3. Ajouter des tests de lifecycle, threading, surfaces/windows, close requests, displays, interactions, capabilities, overflow, handles et permissions.
4. Ajouter les contract tests de température/replay/cardinalité/terminaison des flux, ordre intra-flow, absence de garantie inter-flows, ordre state/event, delivery spans, limites de collectors par flow/session, budgets agrégés et collectors d’événements lents.
5. Ajouter des consumer compile tests Java, Swift, JS et Wasm, y compris les artefacts exportés.
6. Mesurer la baseline avant suppression de l’ancien chemin.
7. Ajouter les benchmarks input, state, gamepad et capture.
8. Créer trois samples de référence : utilitaire, jeu, site Web.
9. Écrire les guides Android, UIKit, SwiftUI, Web et Desktop.
10. Écrire les guides policies, structured concurrency, host surfaces, interactions transitoires, interop renderer et migration.
11. Générer la matrice de capabilities à partir des résultats de contrats.
12. Vérifier que la documentation ne promet ni rendu ni widgets.
13. Générer le rapport de couverture du registre `API-MIGRATION.md` depuis tous les dumps ABI publiés.
14. Vérifier que chaque catalogue public documentaire approuvé correspond exactement aux dumps ABI et exports générés.
15. Tester qu’un nombre élevé de collectors `StateFlow` ne reçoit jamais `ResourceLimitExceeded` de Kadre.
16. Tester séparément un consumer non coopératif avec scheduler disponible et la monopolisation de l’unique dispatcher, afin de prouver la borne schedulable sans inventer une garantie hard real-time.
17. Tester les handoffs single-winner, claims expirés, mutations annulées avant/après commit, inventaires devenus `Unavailable` et limites de payload sans troncature sémantique.
18. Tester l’aplatissement exact des spans lors d’un coalescing ingress puis collector, les enveloppes et spans distincts de collectors de vitesses différentes, la saturation signalée des compteurs et l’absence de diagnostic récursif lorsque le flow de diagnostics déborde.

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
8. Vérifier que chaque symbole de la baseline est effectivement absent, internalisé ou remplacé conformément à `API-MIGRATION.md`.
9. Examiner nominativement tous les symboles classés par les règles résiduelles et obtenir une approbation explicite ou une règle spécifique avant publication.

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

### Coroutine applicative non coopérative

**Risque :** confondre cancellation coopérative et préemption, puis bloquer indéfiniment `awaitTermination()` ou prétendre qu’un job consumer est achevé.

**Réponse :** à la première opportunité de scheduling suivant l’échéance, terminaison logique, révocation des ressources et fin des coroutines internes Kadre ; un job consumer annulé peut rester physiquement incomplet sous la hiérarchie du host jusqu’à sa coopération. Aucune préemption ni borne murale n’est promise si l’unique dispatcher ou le runtime ne s’exécute plus.

### User activation et serials transitoires

**Risque :** une opération fullscreen, pointer lock, popup, drag ou resize est livrée trop tard par un `Flow` et échoue malgré un geste valide.

**Réponse :** `InteractionContext` synchrone limité aux actions autorisées, tokens expirants et actions pré-armables ; aucune coroutine ordinaire ne prétend conserver l’autorité native.

### Confusion surface/fenêtre

**Risque :** un élément DOM ou une vue mobile reçoit des opérations top-level sans signification, recréant des no-op silencieux.

**Réponse :** `HostSurface` et `Window` distinctes, capabilities séparées et `WindowManager.state.value.primary = null` lorsqu’aucune vraie fenêtre top-level n’existe.

### Migration trop horizontale

**Risque :** dépôt non compilable pendant plusieurs semaines.

**Réponse :** nouveaux contrats d’abord, migrations verticales backend par backend, ancienne API supprimée seulement au chantier 11.

### API générique au plus petit dénominateur commun

**Risque :** faux support et no-op silencieux.

**Réponse :** capabilities dynamiques et contraintes typées, `KadreResult`, `WindowRequest` suivi jusqu’à son état terminal, extensions sous opt-in.

### Coût de Flow pour le temps réel

**Risque :** allocations et latence pour les jeux.

**Réponse :** snapshots `StateFlow` sans file par collector, collections spécialisées, profils Realtime, benchmarks, buffers et nombre de collectors d’événements bornés. Les flows mixtes utilisent un scheduler borné à barrières discrètes et chaque `Flow` public possède un mapping de policy fermé. Les coroutines d’observation créées par l’application restent sous son propre budget.

### Ressources OS process-wide

**Risque :** dupliquer les subscriptions gamepad/display/permission ou réintroduire une session globale cachée.

**Réponse :** brokers internes reference-counted sans session courante, IDs projetés par session, routing et ownership d’effets explicitement testés.

### Ownership des frames et handles

**Risque :** use-after-close ou fuite native.

**Réponse :** ressources closeable, constructeurs internes, lifetime lié à la session, layouts de plane sans vue native, copies explicitement app-owned et contract tests de teardown.

### Cancellation confondue avec ownership

**Risque :** annuler un waiter ferme une fenêtre, un effet, un picker ou une capture encore utilisée par un autre consumer.

**Réponse :** cancellation limitée au waiter par défaut ; seuls `cancel`, `requestStop`, `close` et les opérations streaming explicitement propriétaires modifient la ressource.

### Explosion du nombre d’options

**Risque :** API illisible.

**Réponse :** trois profils documentés, policies métier composables, détails coroutine conservés en interne.

## Ordre de publication des snapshots d’incubation

1. Snapshot A : fondations, session et fake host.
2. Snapshot B : surfaces, displays, fenêtres et input avec un backend pilote desktop.
3. Snapshot C : capture et Android.
4. Snapshot D : UIKit et Web.
5. Snapshot E : tous les backends desktop.
6. Snapshot F : suppression de l’ancienne API et surface candidate pour stabilisation.

Chaque snapshot peut casser le précédent, mais doit fournir des release notes et un guide de migration correspondant.
