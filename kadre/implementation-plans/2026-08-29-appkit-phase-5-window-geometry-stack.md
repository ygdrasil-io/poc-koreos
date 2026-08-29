# AppKit Phase 5 — Window Geometry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax and must be completed in order.

**Goal:** rendre opérationnelles, sur AppKit uniquement, les mutations de taille de contenu, contraintes min/max et redimensionnement, avec des contrats coroutine et une validation CI de leur comportement.

**Architecture:** un pipeline runtime portable admet et sérialise les mises à jour par fenêtre, un port de commandes les délègue au peer AppKit, puis le peer applique les APIs KFFI générées et renvoie un snapshot effectif. Les changements issus de Cocoa repassent par le même pipeline d’observation. L’activation publique est différée à la dernière PR.

**Tech Stack:** Kotlin Multiplatform, coroutines (`StateFlow`, `Flow`), JVM, AppKit, KFFI Objective-C généré, Gradle.

**Spec:** `kadre/APPKIT-PHASE-5-WINDOW-GEOMETRY-DESIGN.md`, `kadre/DESIGN.md` §9.5, `kadre/OPERATION-CONTRACTS.md` §4 et `kadre/POLICY-PROFILES.md`.

## Global Constraints

- Portée : `contentSize`, `minimumSize`, `maximumSize` et `resizable` seulement.
- `outerPosition` demeure `Unsupported` jusqu’à la phase Display 9 ; `outerBounds` reste `null`.
- Ne pas introduire de FFI manuel dans Kadre. Les appels restent ceux générés par KFFI ; aucun changement KFFI/Kextract n’est attendu pour cette tranche.
- Conserver les capacités publiques AppKit à `Unsupported` jusqu’à la PR 3.
- Une opération obtient son `OperationId` à l’admission, est sérialisée par fenêtre, et revalide son `expectedRevision` juste avant son commit natif.
- Validation complète avant tout appel natif : minimum ≤ contenu ≤ maximum. Une annulation avant le premier setter retire la requête ; après ce point, l’état natif effectif fait foi, sans rollback artificiel.
- Toute publication met à jour `state` avant les événements. À révision égale : `GeometryChanged`, puis `PropertiesChanged`.
- Les fenêtres distinctes ne se bloquent pas entre elles. Les events de fenêtre appliquent le `WindowDeliveryPolicy` sans modifier le scheduler existant de `Surface`.
- Aucun manuel de test n’est créé dans cette phase : les preuves O2/O3 sont automatisées.

---

## PR fille 1 — Runtime portable, contrat O2 non public

### Task 1: Définir les commandes et l’admission runtime des mises à jour

**Files:**

- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/runtime/WindowCommandPort.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/runtime/RuntimeWindowManagerTest.kt`

- [ ] **Step 1: écrire les tests RED d’admission.** Ajouter au fake `DeterministicWindowCommandPort` une file de commandes de mise à jour et des completions explicitement pilotées par le test. Écrire les tests nommés :

  ```kotlin
  @Test fun windowUpdateValidatesCombinedSizeConstraintsBeforeDispatch()
  @Test fun windowUpdatesSerializePerWindowAndRevalidateExpectedRevisionAtDispatch()
  @Test fun windowUpdateCancellationAndCloseRespectTheNativeCommitBoundary()
  ```

  Le premier vérifie que chaque candidat invalide échoue avant le port et ne publie ni état ni événement. Le second émet deux `apply`, confirme un seul dispatch à la fois et fait échouer un `expectedRevision` devenu obsolète. Le troisième couvre l’annulation pré-commit, l’annulation post-commit et une requête après fermeture.

- [ ] **Step 2: exécuter les tests RED.**

  Run: `./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.runtime.RuntimeWindowManagerTest --console=plain`

  Expected: échec de compilation ou assertions sur les nouvelles sémantiques encore absentes.

- [ ] **Step 3: étendre le contrat interne de commande.** Introduire un type interne immuable du genre :

  ```kotlin
  internal data class WindowUpdateCommand(
      val operationId: OperationId,
      val expectedRevision: Long?,
      val update: WindowUpdate,
  )

  internal sealed interface WindowUpdateCommandStimulus {
      data class Applied(val operationId: OperationId, val state: WindowState) : WindowUpdateCommandStimulus
      data class Rejected(val operationId: OperationId, val error: Throwable) : WindowUpdateCommandStimulus
  }
  ```

  Ajouter au `WindowCommandPort` une requête de mise à jour et au sink les stimuli de fin correspondants. Documenter que le port accuse la réception rapidement et que la completion est asynchrone.

- [ ] **Step 4: implémenter l’admission dans `RuntimeWindow`.** Ajouter une file et un mutex par fenêtre. À l’admission : attribuer l’identifiant, calculer le candidat intégral (y compris `Clear` qui rétablit les defaults initiaux), valider les bornes combinées et décider le no-op avant tout dispatch. À la tête de file : vérifier fermeture, cancellation et `expectedRevision`, puis transmettre au port. Une completion native applique le snapshot effectif, pas le candidat demandé.

- [ ] **Step 5: conserver les comportements existants.** Les propriétés hors de portée restent `Unsupported`; aucun changement à `publicWindowCapabilities` dans cette PR. Garantir qu’une fenêtre B progresse pendant qu’une fenêtre A attend son port.

- [ ] **Step 6: exécuter les tests GREEN.**

  Run: `./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.runtime.RuntimeWindowManagerTest --console=plain`

  Expected: succès, y compris les trois nouveaux tests.

- [ ] **Step 7: commit.**

  ```text
  feat(runtime): serialize window geometry updates
  ```

### Task 2: Programmer les événements Window et clôturer les preuves O2

**Files:**

- Create: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/runtime/RuntimeWindowEventFlow.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/runtime/RuntimeWindowManagerTest.kt`
- Modify: `kadre/runtime/contracts/evidence.tsv`

- [ ] **Step 1: écrire les tests RED de publication et de policy.** Ajouter :

  ```kotlin
  @Test fun windowGeometryEventsFollowConfiguredDeliveryPolicy()
  @Test fun windowUpdatePublishesStateBeforeCorrelatedGeometryAndPropertiesEvents()
  ```

  Couvrir `Coalesced`, `Buffered(…, FailWindow)` et `Buffered(…, FailSession)`. Vérifier que les abonnés lents suivent effectivement la policy, que `state` est visible avant l’event, que `GeometryChanged` précède `PropertiesChanged` à révision égale et que l’`operationId` est conservé. Vérifier qu’une modification externe est corrélée par `null`.

- [ ] **Step 2: exécuter les tests RED.**

  Run: `./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.runtime.RuntimeWindowManagerTest --console=plain`

  Expected: échec car `RuntimeWindow` utilise encore un `MutableSharedFlow` non piloté par policy.

- [ ] **Step 3: créer un scheduler dédié aux événements Window.** Adapter les sémantiques nécessaires de `BoundedSurfaceScheduler` dans `RuntimeWindowEventFlow.kt`, sans refactorer le code Surface. Définir explicitement `GeometryChanged` comme coalesçable et `PropertiesChanged`/fermeture comme discrets. En overflow `FailWindow`, fermer de façon terminale la fenêtre avec `SourceOverflow(Window)` ; en `FailSession`, déléguer au handler de session.

- [ ] **Step 4: brancher l’ordre de publication.** Passer `WindowDeliveryPolicy` et le handler de session de `SessionRuntime` à chaque `RuntimeWindow`. Pour chaque completion gérée, publier le `StateFlow`, puis les événements ordonnés. Pour une observation native externe, publier le même état effectif avec `operationId = null`.

- [ ] **Step 5: enregistrer les preuves O2 sans activer le contrat.** Ajouter dans `kadre/runtime/contracts/evidence.tsv` les scénarios `WIN-001` et leurs tests JVM réels. Garder `WIN-001` en statut `planned` et ne modifier ni la liste des contrats runtime imposés ni les capabilities publiques.

- [ ] **Step 6: exécuter les tests GREEN et le validator.**

  Run: `./gradlew :kadre:runtime:jvmTest :kadre:contracts:validator:validateContractRegistry --console=plain`

  Expected: succès. Les preuves existent mais le registry accepte encore `WIN-001` comme contrat `planned`.

- [ ] **Step 7: commit.**

  ```text
  feat(runtime): schedule window geometry events
  ```

---

## PR fille 2 — Intégration AppKit privée, contrat O3 non public

### Task 3: Étendre les seams AppKit et le peer sans exposer de capability

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/backend/appkit/AppKitNativeWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/backend/appkit/AppKitWindowPeer.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/backend/appkit/AppKitWindowRuntimeDriver.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/backend/appkit/AppKitWindowRuntimeDriverTest.kt`

- [ ] **Step 1: écrire les tests RED du peer.** Étendre `DeterministicAppKitNativeWindowPort` afin qu’il enregistre les commandes géométriques, autorise leur completion et simule un callback externe. Ajouter :

  ```kotlin
  @Test fun peerForwardsGeometryUpdatesAndReturnsTheEffectiveNativeSnapshot()
  @Test fun peerSuppressesManagedResizeCallbacksButForwardsExternalGeometry()
  @Test fun peerPreservesUnrelatedStyleMaskBitsWhenChangingResizable()
  ```

- [ ] **Step 2: exécuter les tests RED.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.backend.appkit.AppKitWindowRuntimeDriverTest --console=plain`

  Expected: échec, les seams ne décrivent pas encore la géométrie et le driver ne route pas les commandes.

- [ ] **Step 3: ajouter les contrats natifs privés.** Définir sur `AppKitNativeWindowPort` une opération de mise à jour qui prend le target et retourne le snapshot effectif, plus un callback `geometryChanged`. Garder `NSWindow` et ses handles exclusivement dans le port KFFI. Sur `AppKitWindowPeer`, sérialiser les appels sur le callback gate et borner la suppression de réentrance aux callbacks issus de sa propre mutation.

- [ ] **Step 4: compléter le driver.** Étendre `AppKitWindowCommandPort` et `AppKitWindowCommandQueue` pour soumettre les mises à jour, propager cancellation avant commit et publier les stimuli correspondants. Le snapshot de la completion est celui renvoyé par le peer. Les callbacks externes deviennent des stimuli non corrélés.

- [ ] **Step 5: exécuter les tests GREEN.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.backend.appkit.AppKitWindowRuntimeDriverTest --console=plain`

  Expected: succès.

- [ ] **Step 6: commit.**

  ```text
  feat(appkit): route private window geometry commands
  ```

### Task 4: Appliquer les contraintes avec les APIs KFFI générées

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/backend/appkit/KffiAppKitWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/backend/appkit/KffiAppKitWindowPortMacOsTest.kt`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`

- [ ] **Step 1: écrire les tests RED macOS.** Ajouter les tests :

  ```kotlin
  @Test fun generatedKffiWindowAppliesInitialContentConstraintsAndResizableMaskOnMacOs()
  @Test fun generatedKffiWindowUpdatesContentConstraintsAndRestoresNativeDefaultsOnMacOs()
  ```

  Ils vérifient `setContentSize`, `setContentMinSize`, `setContentMaxSize`, la lecture du snapshot effectif, `Clear` et la préservation des bits de `styleMask` qui ne sont pas `Resizable`.

- [ ] **Step 2: exécuter les tests RED sur macOS.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.backend.appkit.KffiAppKitWindowPortMacOsTest --console=plain`

  Expected: échec jusqu’à l’utilisation des setters KFFI générés.

- [ ] **Step 3: implémenter le port KFFI.** À la création, capturer les defaults natifs de min/max puis appliquer les contraintes initiales et le bit `Resizable`. Pour une mutation : normaliser le target, respecter l’ordre sûr « relâcher les bornes, taille de contenu, resserrer les bornes », appeler uniquement les méthodes générées de `NSWindow`, relire l’état effectif et le retourner. `Clear` restaure les defaults capturés, tandis que l’API publique conserve `null`.

- [ ] **Step 4: envoyer les observations externes.** Étendre `KffiSurfaceObserverOwner` pour que `NSWindowDidResizeNotification` alimente aussi `geometryChanged`; ne pas détourner ou retirer l’observation Surface existante.

- [ ] **Step 5: ajouter les preuves O3 privées.** Dans `kadre/backend/appkit/contracts/evidence.tsv`, associer les scénarios AppKit concernés aux tests macOS réels tout en gardant `APK-006` `planned` et les capabilities publiques désactivées.

- [ ] **Step 6: exécuter les tests GREEN.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.backend.appkit.KffiAppKitWindowPortMacOsTest --console=plain`

  Expected: succès sur macOS arm64 avec le snapshot KFFI publié `1.0.0-20260829.065753-21` ou plus récent contenant `ObjCPointerTracking`.

- [ ] **Step 7: commit.**

  ```text
  feat(appkit): apply native window geometry
  ```

---

## PR fille 3 — Activation publique, contracts et gates

### Task 5: Activer uniquement les quatre capabilities AppKit prévues

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/backend/appkit/AppKitWindowRuntimeDriverFactory.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/backend/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/backend/appkit/AppKitBackendProviderTest.kt`

- [ ] **Step 1: écrire les tests RED d’activation publique macOS.** Ajouter :

  ```kotlin
  @Test fun publicAppKitWindowGeometryActivatesOnlyTheFourProvenCapabilitiesOnMacOs()
  @Test fun publicAppKitWindowApplyUsesGeneratedNativeGeometryAndCorrelatesOperationOnMacOs()
  @Test fun nativeExternalResizeUpdatesWindowStateWithNullOperationIdOnMacOs()
  @Test fun publicAppKitWindowGeometryEventsFollowSessionPolicyOnMacOs()
  ```

  Vérifier que les quatre capabilities sont supportées, que `outerPosition` et toutes les autres mutations restent `Unsupported`, que l’état publié suit la taille native et que les events externes ont une corrélation nulle.

- [ ] **Step 2: exécuter les tests RED.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.backend.appkit.AppKitBackendProviderTest --console=plain`

  Expected: échec tant que la factory publique ne transmet pas le support interne de géométrie.

- [ ] **Step 3: activer au dernier point de composition.** Ajouter une configuration interne explicite de géométrie à `AppKitWindowRuntimeDriverFactory`, désactivée par défaut. Seul `AppKitBackendProvider` public l’active avec `contentSize`, `minimumSize`, `maximumSize` et `resizable`. Laisser `outerPosition` à `Unsupported`, `outerBounds` à `null`, et ne pas modifier le comportement des factories de test ou privées par défaut.

- [ ] **Step 4: exécuter les tests GREEN.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.backend.appkit.AppKitBackendProviderTest --console=plain`

  Expected: succès des tests publics AppKit, y compris l’overflow/policy.

- [ ] **Step 5: commit.**

  ```text
  feat(appkit): activate public window geometry
  ```

### Task 6: Activer WIN-001 et APK-006 avec leurs preuves et gates

**Files:**

- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/contracts/validator/build.gradle.kts`
- Modify: `kadre/runtime/contracts/evidence.tsv`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`
- Modify: `scripts/test-kadre-appkit-contracts.sh`

- [ ] **Step 1: écrire les contrôles RED de registry.** Dans les tests du validator ou les fixtures existantes, prouver qu’un contrat actif sans mapping de scénario/sentinelle ou hors de son gate échoue. Employer exactement `WIN-001` et `APK-006`.

- [ ] **Step 2: exécuter le validator RED.**

  Run: `./gradlew :kadre:contracts:validator:validateContractRegistry --console=plain`

  Expected: échec jusqu’à ce que les deux contrats, l’evidence et les gates soient cohérents.

- [ ] **Step 3: activer les contrats atomiquement.** Passer `WIN-001` et `APK-006` de `planned` à `active`. Mapper dans les TSV les tests réels introduits aux tâches 1 à 5. Ajouter `WIN-001` à `runtimeContractIds`, `APK-006` à `appKitContractIds`, puis ajouter APK-006 au script de gate AppKit. Ne pas renommer une clé de scénario ou de sentinelle réservée par le design sans modifier simultanément la spec et le registry.

- [ ] **Step 4: exécuter les gates GREEN.**

  Run: `./gradlew :kadre:contracts:validator:validateContractRegistry :kadre:runtime:jvmTest :kadre:backend:appkit:jvmTest --console=plain`

  Run: `./scripts/test-kadre-appkit-contracts.sh`

  Expected: succès complet ; les deux contrats actifs ont une preuve exécutable.

- [ ] **Step 5: commit.**

  ```text
  test(contracts): enforce AppKit window geometry evidence
  ```

### Task 7: Audit final de la tranche et revue de non-régression

**Files:**

- Modify: `kadre/APPKIT-PHASE-5-WINDOW-GEOMETRY-DESIGN.md` (uniquement si l’implémentation impose une clarification factuelle)
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md` (marquer la tranche achevée seulement après succès des gates)

- [ ] **Step 1: relire les diffs des trois PRs.** Vérifier l’absence de FFI manuel, l’absence de nouvelle API publique hors des quatre capabilities, et l’absence de changement aux garanties de Surface.

- [ ] **Step 2: vérifier les invariants de la spec.** Contrôler dans les tests et les code paths : validation pré-native, génération d’ID à l’admission, révision revalidée au dispatch, pas de rollback post-commit, state avant event, ordre Geometry/Properties, callback externe non corrélé, et isolation inter-fenêtres.

- [ ] **Step 3: exécuter la suite finale.**

  Run: `./gradlew :kadre:foundation:allTests :kadre:runtime:jvmTest :kadre:backend:appkit:jvmTest :kadre:contracts:validator:validateContractRegistry --console=plain`

  Run: `./scripts/test-kadre-appkit-contracts.sh`

  Expected: succès complet avant la fusion de la dernière PR fille.

- [ ] **Step 4: mettre à jour la roadmap si et seulement si les commandes sont vertes.** Remplacer l’état de la sous-tranche Phase 5 par une formulation factuelle qui liste les quatre mutations supportées et le report de `outerPosition` à Phase 9.

- [ ] **Step 5: commit d’audit si nécessaire.**

  ```text
  docs(appkit): record window geometry completion
  ```

