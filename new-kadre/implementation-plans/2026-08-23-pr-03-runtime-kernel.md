# New Kadre Runtime Kernel Implementation Plan

> **For agentic workers:** use `superpowers:executing-plans` and `superpowers:test-driven-development`. Sub-agents are forbidden in this side-conversation workspace.

**Goal:** exécuter une `KadreApplication` dans une session coroutine structurée, avec lifecycle, teardown déterministe, managers honnêtement non supportés et scénarios O2 JVM, sans SDK host ni KFFI.

**Stack:** cette PR part de `codex/foundation-api`, publie la branche `codex/runtime-kernel` et cible la PR 2. Elle ne modifie pas encore les entry points Desktop.

**Architecture:** le projet transitif interne `runtime` contient un `RuntimeHostController` technique dans `org.graphiks.kadre.internal.runtime`. Il implémente `KadreHost`, possède l'admission attach/detach et crée un `SessionRuntime` par session. Chaque session remplace uniquement le `Job` du contexte parent pour l'application, conserve son propre lifecycle et expose des managers fermés dont les capabilities et opérations sont explicitement `Unsupported`. Le fake de cette PR est une fixture interne au test runtime ; l'artifact public `test` n'est pas publié partiellement.

## Contraintes globales

- JVM 25 et metadata common uniquement ; le code runtime initial vit dans `jvmMain` tant que la target JVM est la seule active.
- `runtime` dépend de `foundation`, jamais d'un SDK, backend ou KFFI.
- Le groupe publié est `org.graphiks.kadre.internal`; aucun type de ce package ne fuit dans l'ABI de `foundation`, `desktop` ou `kadre-new`.
- Les constructeurs internes des IDs/révisions de `foundation` sont accessibles uniquement via une compilation friend explicite de `runtime`; aucune factory publique n'est ajoutée au catalogue.
- Aucun `GlobalScope`, singleton de session courante, sleep arbitraire, retry, test historique ou test de timing mural.
- La gate reste limitée à `:kadre-new:`.
- L'exception applicative originale est transmise au reporter technique, mais seul `ApplicationFailure` entre dans le contrat public.
- La PR active uniquement les contrats réellement prouvés ; aucun fake public incomplet n'est publié.

## Task 1 — Build interne et accès friend

**Files:** `settings.gradle.kts`, `new-kadre/runtime/build.gradle.kts`, `new-kadre/build.gradle.kts`.

1. Ajouter un test de compilation rouge utilisant `RuntimeHostController`.
2. Inclure `:kadre-new:runtime`, KMP JVM 25, `explicitApi()`, coroutines et `foundation` en API interne.
3. Configurer le jar JVM de `foundation` comme friend path et dépendance explicite de compilation.
4. Publier `runtime` sous `org.graphiks.kadre.internal:runtime` vers le repository contractuel, sans l'exposer encore depuis l'umbrella.
5. Faire dépendre `:kadre-new:check` de `:kadre-new:runtime:check`.

## Task 2 — Lifecycle et horloge de session

**Files:** `runtime/.../RuntimeHostController.kt`, `RuntimeLifecycle.kt`, `RuntimeClock.kt`.

1. Tester les snapshots initiaux, la déduplication, l'ordre snapshot avant event, les transitions invalides et le detach terminal.
2. Implémenter une horloge monotone injectable et un générateur de `EventStamp` propre à chaque session.
3. Implémenter `RuntimeLifecycle` avec `StateFlow` initial, `Flow` sans replay et publication sérialisée.
4. Exposer sur le contrôleur technique `updateLifecycle`, `updateLifecycleCapabilities`, `emitMemoryPressure` et `detach`.
5. Vérifier qu'une session créée après une mise à jour reçoit le snapshot courant sans event historique.

## Task 3 — Session structurée

**Files:** `runtime/.../SessionRuntime.kt`, `RuntimeScope.kt`, `RuntimeHostController.kt`.

1. Écrire les scénarios rouges : parent sans `Job`, parent inactif, état `Starting`, `Running` avant application, retour normal, arrêt application, arrêt host, parent cancellation, detach et exception de factory/application.
2. Créer un `SupervisorJob` racine enfant du parent et un job applicatif ordinaire exposé par `KadreScope`.
3. Sérialiser admission attach/start/stop/failure/detach avec un verrou par host/session.
4. Implémenter `requestStop`, `close` et `awaitTermination` idempotents ; un marqueur de contexte interdit l'attente depuis l'arbre applicatif.
5. Sur retour normal, fermer l'admission de nouveaux enfants et attendre les enfants déjà admis avant `Completed`.
6. Sur arrêt, annuler l'application, respecter `shutdownTimeout`, publier un seul outcome et révoquer les ressources même si l'application ne coopère pas.
7. Conserver la première failure non-cancellation comme primaire et reporter la cause originale.
8. Prouver qu'une session n'annule jamais le scope host, mais que l'annulation du parent termine la session.

## Task 4 — Managers fermés et requête de fenêtre rejetée

**Files:** `runtime/.../UnsupportedManagers.kt`, `UnsupportedWindowRequest.kt`, `RuntimeDiagnostics.kt`.

1. Tester les snapshots initiaux complets accessibles avant `Running`.
2. Exposer `primarySurface = null`, display vide/non énumérable, devices `Unsupported`, capture indisponible et diagnostics vides avec compteurs nuls.
3. Retourner les failures directes exactes pour display/capture.
4. Pour `WindowManager.requestWindow`, retourner un owner déjà terminal `Rejected(Unsupported(RequestWindow))`, jamais une failure externe ni un faux succès de fenêtre.
5. Assurer IDs de requête uniques par session, `close` idempotent, `cancel/await` déterministes et `WindowManagerState` inchangé.

## Task 5 — Scénarios O2 actifs

**Files:** tests runtime, `new-kadre/contracts/registry/contracts.tsv`.

1. Ajouter des scénarios à oracle explicite couvrant attach/stop/failure/lifecycle/unsupported/isolation.
2. Ajouter deux sessions sur le même contrôleur et deux contrôleurs distincts pour prouver l'absence de fuite d'IDs, jobs et lifecycle events.
3. Ajouter les contrats actifs `RUN-*` uniquement pour ces preuves.
4. Ne pas créer `contracts:model` tant qu'un modèle plus petit que l'implémentation n'est pas nécessaire ; les expected des scénarios sont des traces fermées écrites indépendamment.

## Task 6 — Audit et PR empilée

1. Exécuter séparément les updates/checks ABI nécessaires ; `runtime` n'est pas une ABI publique contractuelle.
2. Exécuter :

   ```bash
   rtk ./gradlew :kadre-new:check :kadre-new:runtime:check --rerun-tasks
   ```

3. Vérifier metadata common/JVM 25 et absence de SDK/KFFI/ancien Kadre.
4. Vérifier que les ABI publiques existantes sont inchangées.
5. Pousser `codex/runtime-kernel` et ouvrir la PR contre `codex/foundation-api`.

## Hors scope

- sélection/provider Desktop et `ServiceLoader` ;
- AppKit et KFFI ;
- surface/fenêtre réelle ;
- input réel ;
- artifact public `test` complet ;
- policies de delivery avancées au-delà des flows réellement émis dans cette PR ;
- toute target autre que JVM.
