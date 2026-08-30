# AppKit Phase 5 — Window Title Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax and must be completed in order.

**Goal:** rendre `WindowUpdate.title` opérationnel sur AppKit avec un snapshot KFFI effectif, des coroutines corrélées et des preuves CI O2/O3, sans activer d'autre mutation top-level.

**Architecture:** le runtime transforme titre et géométrie en une seule commande privée sérialisée par fenêtre. Le peer AppKit exécute les setters synchrones sur son thread propriétaire puis relit le titre et la géométrie avant de compléter l'opération. L'activation de capability et les contrats actifs restent concentrés dans la dernière PR de la stack.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines (`StateFlow`, `Flow`), JVM, AppKit, bindings Objective-C générés par KFFI, Gradle.

**Spec:** `kadre/APPKIT-PHASE-5-WINDOW-TITLE-DESIGN.md`; `kadre/APPKIT-PHASE-5-WINDOW-GEOMETRY-DESIGN.md`; `kadre/OPERATION-CONTRACTS.md` §4; `kadre/POLICY-PROFILES.md`.

## Global Constraints

- Portée : `WindowProperty.Title` seulement, en plus de la géométrie déjà active.
- `PropertyChange.Clear` sur `title` retourne exactement `KadreFailure.InvalidRequest("title")` avant tout setter natif.
- `outerPosition`, `outerBounds`, fullscreen, décorations, boutons système, level, transparence, blur, icône, attention et content protection gardent leur état `Unsupported` actuel.
- Ne pas écrire de FFI à la main dans Kadre. Utiliser uniquement `NSWindow.setTitle(String)` et `NSWindow.titleAsString()` générés par KFFI ; une lacune remonte d'abord à Kextract puis à une régénération KFFI.
- Le point de commit est le premier setter réel. Avant lui, cancellation retire la commande ; après lui, aucun rollback artificiel ne doit être tenté.
- Une completion native publie l'état effectif avant les événements. À une révision donnée, `GeometryChanged` précède l'unique `PropertiesChanged` éventuel.
- Une observation native externe reste limitée à la géométrie ; ce plan n'ajoute pas d'observateur de titre externe.
- La stack reste empilée : PR de contrat, PR runtime, PR AppKit privé, PR d'activation. Chaque carte reste verte par elle-même.

---

## PR 1 — Contrat et réservation

### Task 1: Réserver les contrats et documenter la frontière

**Files:**

- Create: `kadre/APPKIT-PHASE-5-WINDOW-TITLE-DESIGN.md`
- Create: `kadre/implementation-plans/2026-08-30-appkit-phase-5-window-title-stack.md`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/APPKIT-IMPLEMENTATION-ROADMAP.md`

**Consumes:** les contrats actifs `WIN-001` et `APK-006` de la géométrie.

**Produces:** deux réservations `planned`, `WIN-002` O2 et `APK-007` O3, sans evidence ni capability publique.

- [ ] **Step 1: ajouter les deux lignes planned au registre.**

  Ajouter exactement ces enregistrements tabulés après `APK-006` :

  ```text
  WIN-002	planned	APPKIT-PHASE-5-WINDOW-TITLE-DESIGN.md#Preuves et contrats	runtime window title pipeline	invalid title clear, lost combined mutation or unordered property event	O2	-	-	-	-	-
  APK-007	planned	APPKIT-PHASE-5-WINDOW-TITLE-DESIGN.md#Preuves et contrats	public AppKit window title activation	false title capability, stale effective title or bypassed mutation boundary	O3	-	-	-	-	-
  ```

- [ ] **Step 2: écrire le design.**

  Fixer explicitement : `Clear(title)` invalide, un update titre + géométrie
  devient une seule commande, le setter de titre précède les setters de
  géométrie, le readback reste autoritaire, et les mutations hors Kadre ne sont
  pas observées. Lister `WIN-002`, `APK-007`, leurs scénarios et sentinelles.

- [ ] **Step 3: inscrire la prochaine sous-tranche à la roadmap.**

  Remplacer la phrase qui présente la géométrie comme seule sous-tranche par
  une phrase qui la déclare achevée et référence le design de titre comme
  prochaine tranche réservée. Ne pas modifier le gate global de phase 5.

- [ ] **Step 4: vérifier le registre et le scan de cohérence.**

  Run: `./gradlew :kadre:contracts:validator:validateContractRegistry --console=plain`

  Expected: PASS ; les contrats `planned` n'exigent pas d'evidence.

  Run: `rg -n '([T])(ODO)|([T])(BD)|[à] définir|à déci[d]er' kadre/APPKIT-PHASE-5-WINDOW-TITLE-DESIGN.md kadre/implementation-plans/2026-08-30-appkit-phase-5-window-title-stack.md`

  Expected: aucune occurrence.

- [ ] **Step 5: commit.**

  ```text
  docs(appkit): define window title stack
  ```

---

## PR 2 — Runtime portable privé et preuve O2

### Task 2: Faire entrer le titre dans le candidat et la file runtime

**Files:**

- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`

**Consumes:** `WindowUpdate`, `WindowState`, `WindowProperty.Title`, `WindowUpdateCommand` et la file `PendingWindowUpdate` existants.

**Produces:** une commande contenant les changements supportés de titre et de géométrie ; `WindowCapabilities.title` reste non activée dans cette PR.

- [ ] **Step 1: écrire les tests RED du titre runtime.**

  Ajouter les tests suivants à `RuntimeWindowManagerTest` avec le port
  déterministe déjà utilisé par la géométrie :

  ```kotlin
  @Test fun windowTitleClearFailsBeforeDispatchOrPublication()
  @Test fun windowTitleAndGeometryShareOneCorrelatedCommand()
  @Test fun windowTitleNoOpDoesNotDispatchOrReviseState()
  @Test fun queuedWindowTitleRevalidatesExpectedRevisionAtDispatch()
  ```

  Le premier attend `InvalidRequest("title")`, zéro `updateCommands`, la même
  révision et aucun event. Le deuxième complète une commande avec un snapshot
  de titre et de géométrie différents de la demande puis vérifie un unique
  `operationId`. Le troisième vérifie `Applied`, aucune commande, aucun event
  et aucune révision nouvelle. Le quatrième fait avancer la première commande,
  puis vérifie que la seconde avec une révision obsolète échoue sans dispatch.

- [ ] **Step 2: exécuter les tests RED.**

  Run: `./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest --console=plain`

  Expected: échec parce que `Title` est rejeté ou supprimé avant le port.

- [ ] **Step 3: dissocier le routage de l'annonce publique.**

  Garder `enabledWindowGeometryCapabilities` comme configuration de routage :
  compléter ce set par les quatre propriétés de géométrie déjà routées et
  ajouter `WindowProperty.Title` dans les preuves titre. `WindowCapabilities.title`
  devient `Supported` seulement si ce set contient `Title` *et* que
  `publicWindowCapabilities` vaut `true`; les preuves O2 le laissent à `false`.
  Étendre `candidateFor` après avoir filtré l'update vers les seules propriétés
  de ce set :

  ```kotlin
  title = resolveTitle(update.title, current.title)
  ```

  avec :

  ```kotlin
  private fun resolveTitle(change: PropertyChange<String>, current: String): String = when (change) {
      is PropertyChange.Set -> change.value
      PropertyChange.Unchanged -> current
      PropertyChange.Clear -> current // Clear est écarté par invalidWindowClearField avant ce point.
  }
  ```

  Étendre `invalidGeometryClearField` en validation non nullable incluant
  `title` seulement lorsque le champ est routé, conserver les validations de
  taille, et rejeter explicitement comme `Unsupported(UpdateWindow)` les champs
  absents du set. Une fenêtre minimale garde donc `title` indisponible.

- [ ] **Step 4: conserver l'update complet supporté jusqu'au port.**

  Remplacer `geometryOnly(update)` par une fonction qui conserve exactement
  `title`, `contentSize`, `minimumSize`, `maximumSize`, `resizable` et
  `expectedRevision`. Remplacer `geometryChanged` par un prédicat qui compare
  aussi `title`. Ne pas inclure les propriétés toujours non supportées.

- [ ] **Step 5: faire publier l'événement de propriétés regroupé.**

  Dans `publishStatePublication`, calculer :

  ```kotlin
  val changedProperties = buildSet {
      if (before.title != effective.title) add(WindowProperty.Title)
      if (before.resizable != effective.resizable) add(WindowProperty.Resizable)
  }
  ```

  Émettre un unique `WindowEvent.PropertiesChanged` si ce set n'est pas vide,
  après l'éventuel `GeometryChanged`, avec le même `operationId` et le snapshot
  effectif.

- [ ] **Step 6: exécuter les tests GREEN.**

  Run: `./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest --console=plain`

  Expected: PASS, y compris les scénarios géométrie existants.

- [ ] **Step 7: commit.**

  ```text
  feat(runtime): serialize window title updates
  ```

### Task 3: Ajouter les preuves O2 sans activation publique

**Files:**

- Modify: `kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt`
- Modify: `kadre/runtime/contracts/evidence.tsv`

**Consumes:** les tests de Task 2 et `WIN-002` réservé.

**Produces:** les scénarios/sentinelles complets de `WIN-002`, tandis que le
registre le laisse `planned` jusqu'à la PR d'activation.

- [ ] **Step 1: écrire les tests RED de cancellation et d'ordre.**

  Ajouter :

  ```kotlin
  @Test fun windowTitleCancellationRespectsTheNativeCommitBoundary()
  @Test fun windowTitleStatePrecedesOneCorrelatedPropertiesEvent()
  ```

  Le premier couvre le retrait avant dispatch et la publication tardive après
  commit. Le second collecte `Window.events`, vérifie que `state` possède le
  titre effectif au moment de l'event, que `changed == setOf(WindowProperty.Title)`
  pour une mutation titre seule et que le même `operationId` apparaît dans
  l'outcome et l'event. Exécuter avec un profil `WindowDeliveryPolicy` borné
  pour vérifier que l'event discret ne contourne pas la policy.

- [ ] **Step 2: exécuter les tests RED.**

  Run: `./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest --console=plain`

  Expected: échec tant que l'événement titre et la boundary ne sont pas prouvés.

- [ ] **Step 3: compléter les preuves et l'implémentation minimale.**

  Ajuster l'implémentation de Task 2 seulement si les tests révèlent une
  violation de la file ou de l'ordre de publication. Ajouter ces cinq mappings
  à `kadre/runtime/contracts/evidence.tsv` :

  ```text
  WIN-002	scenario	runtime-window-title-validation	org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest	windowTitleClearFailsBeforeDispatchOrPublication[jvm]
  WIN-002	scenario	runtime-window-title-composition	org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest	windowTitleAndGeometryShareOneCorrelatedCommand[jvm]
  WIN-002	scenario	runtime-window-title-cancellation	org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest	windowTitleCancellationRespectsTheNativeCommitBoundary[jvm]
  WIN-002	scenario	runtime-window-title-event-order	org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest	windowTitleStatePrecedesOneCorrelatedPropertiesEvent[jvm]
  WIN-002	sentinel	runtime-window-title-noop	org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest	windowTitleNoOpDoesNotDispatchOrReviseState[jvm]
  ```

  Ajouter aussi les quatre sentinelles restantes, chacune vers le test qui
  démontre sa frontière : clear, stale dispatch, corrélation et policy.

- [ ] **Step 4: exécuter les preuves O2.**

  Run: `./gradlew :kadre:runtime:jvmTest :kadre:contracts:validator:validateContractRegistry --console=plain`

  Expected: PASS ; les mappings sont permis pour un contrat `planned`, mais ne
  deviennent exigibles qu'à l'activation.

- [ ] **Step 5: commit.**

  ```text
  test(runtime): prove window title update pipeline
  ```

---

## PR 3 — AppKit privé et preuve O3

### Task 4: Étendre le seam AppKit à une mutation complète

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowPeer.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt`

**Consumes:** la commande runtime complète de Task 2 et les bindings KFFI publiés.

**Produces:** `AppKitWindowMutationTarget`, `AppKitWindowMutationSnapshot` et
un readback natif commun titre + géométrie, sans activation de capability.

- [ ] **Step 1: écrire les tests RED avec le port déterministe.**

  Ajouter :

  ```kotlin
  @Test fun peerForwardsTitleAndGeometryInOneNativeMutation()
  @Test fun titleCancellationBeforeFirstSetterDoesNotMutateNativeWindow()
  @Test fun titleFailureAfterCommitPublishesTheEffectiveReadback()
  ```

  Le port déterministe enregistre une `AppKitWindowMutationTarget`, garde un
  titre mutable dans `RecordingNativeWindowOwner`, et sait injecter une failure
  après `setTitle`. Les assertions vérifient respectivement une seule cible,
  zéro setter avant commit, et la valeur relue après une failure post-commit.

- [ ] **Step 2: exécuter les tests RED.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitWindowRuntimeDriverTest --console=plain`

  Expected: échec de compilation ou de comportement car le seam est limité à la géométrie.

- [ ] **Step 3: remplacer le seam de géométrie par le seam de mutation.**

  Dans `AppKitNativeWindowPort.kt`, définir :

  ```kotlin
  internal data class AppKitWindowMutationTarget(
      val title: PropertyChange<String>,
      val geometry: AppKitWindowGeometryTarget,
  )

  internal data class AppKitWindowMutationSnapshot(
      val title: String,
      val geometry: AppKitWindowGeometrySnapshot,
  )
  ```

  Renommer le token en `AppKitWindowMutationCommit`, remplacer `updateGeometry`
  et `readGeometry` par `updateWindow` et `readWindow`, puis conserver les
  callbacks d'observation limités à `AppKitWindowGeometrySnapshot`. Les nouveaux
  types restent `internal` et sans `MemorySegment`.

- [ ] **Step 4: conserver la boundary native unique.**

  Dans `AppKitWindowPeer`, faire exécuter `updateWindow(target, commit)` à
  travers le même gate de callbacks de géométrie. Dans le port KFFI, appeler
  `commit.beforeFirstSetter()` exactement une fois, appeler `window.setTitle`
  seulement pour `PropertyChange.Set`, appliquer ensuite la géométrie sans
  reconsulter le token, puis renvoyer :

  ```kotlin
  AppKitWindowMutationSnapshot(
      title = window.titleAsString(),
      geometry = readGeometry(),
  )
  ```

  En cas d'exception post-commit, le peer appelle `readWindow` et complète avec
  le snapshot ainsi obtenu. Ne pas installer de notification de titre.

- [ ] **Step 5: adapter le driver et le fake.**

  Remplacer `PendingGeometryCommand`, `geometryCommands`, `applyGeometry` et
  les messages de diagnostic par des noms de mutation. Construire le target à
  partir de `WindowUpdate.title` et de la cible géométrique existante. Mapper le
  snapshot en `WindowState` en remplaçant titre et géométrie, mais préserver les
  autres propriétés du state courant. Passer `WindowProperty.Title` seulement
  dans le set de routage du driver ; garder `publicWindowCapabilities` à sa
  valeur privée et ne modifier aucune capability publique dans cette PR.

- [ ] **Step 6: exécuter les tests GREEN privés.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitWindowRuntimeDriverTest --console=plain`

  Expected: PASS, y compris toutes les courses de géométrie existantes.

- [ ] **Step 7: commit.**

  ```text
  feat(appkit): apply correlated window title mutations
  ```

### Task 5: Prouver le binding généré sur une vraie NSWindow

**Files:**

- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`

**Consumes:** le seam de Task 4, `NSWindow.setTitle(String)` et `titleAsString()` générés.

**Produces:** preuve O3 native de mutation et de readback ; `APK-007` reste
planned jusqu'à la PR suivante.

- [ ] **Step 1: écrire le test RED macOS.**

  Ajouter :

  ```kotlin
  @Test fun generatedKffiWindowUpdatesTitleAndReadsItBackOnMacOs()
  ```

  Le test quitte tôt hors macOS. Sur macOS, il prépare un peer, appelle la
  mutation avec `PropertyChange.Set("effective title")`, et vérifie via la
  `withDesktopHandle` existante que `NSWindow(...).titleAsString()` vaut
  `"effective title"`. Il vérifie aussi que la snapshot retournée porte ce
  titre et conserve une géométrie lisible.

- [ ] **Step 2: exécuter le test RED.**

  Run: `./gradlew :kadre:backend:appkit:appKitNativeTests --tests org.graphiks.kadre.internal.appkit.KffiAppKitWindowPortMacOsTest.generatedKffiWindowUpdatesTitleAndReadsItBackOnMacOs --console=plain`

  Expected: échec avant le raccordement du seam KFFI.

- [ ] **Step 3: enregistrer la preuve privée.**

  Ajouter les mappings `APK-007` pour `appkit-window-title-native-update` et
  `appkit-window-title-generated-binding` vers ce test, plus les mappings des
  tests déterministes de Task 4 pour cancellation, effective readback et
  cross-window. Ajouter le mapping policy lors de la Task 6.

- [ ] **Step 4: exécuter les tests GREEN.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest :kadre:backend:appkit:appKitNativeTests --console=plain`

  Expected: PASS ; le test natif est réellement exécuté sur un runner macOS et
  reste no-op sur un hôte non macOS.

- [ ] **Step 5: commit.**

  ```text
  test(appkit): prove generated window title binding
  ```

---

## PR 4 — Activation publique et gates

### Task 6: Activer la capability, les contrats et la CI ensemble

**Files:**

- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt`
- Modify: `kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverFactory.kt`
- Modify: `kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt`
- Modify: `kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt`
- Modify: `kadre/contracts/registry/contracts.tsv`
- Modify: `kadre/runtime/contracts/evidence.tsv`
- Modify: `kadre/backend/appkit/contracts/evidence.tsv`
- Modify: `scripts/test-kadre-appkit-contracts.sh`
- Modify: `scripts/test-kadre-appkit-contract-driver.sh`
- Modify: `scripts/fixtures/fake-gradlew.sh`

**Consumes:** les preuves O2/O3 des Tasks 2 à 5 et les réservations planned.

**Produces:** `WIN-002` et `APK-007` actifs, `WindowCapabilities.title`
supportée sur AppKit, evidence JSON `APK-007` exigée par la CI.

- [ ] **Step 1: écrire les tests RED d'activation.**

  Ajouter à `AppKitBackendProviderTest` :

  ```kotlin
  @Test fun publicAppKitWindowTitleCapabilityUsesGeneratedReadbackOnMacOs()
  @Test fun publicAppKitWindowTitleEventsFollowSessionPolicyOnMacOs()
  @Test fun publicAppKitWindowTitleDoesNotCrossBetweenTwoWindowsOnMacOs()
  ```

  Le premier exige `Capability.Supported(Unit, FeatureAvailability.Available)`,
  applique un titre et vérifie l'outcome, le state et le getter KFFI. Le second
  vérifie state avant `PropertiesChanged` et le passage par la policy. Le
  troisième ouvre deux fenêtres, mute une seule et vérifie l'isolation des deux
  états et titres natifs.

- [ ] **Step 2: exécuter les tests RED.**

  Run: `./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest --console=plain`

  Expected: échec parce que `WindowCapabilities.title` est encore `Unsupported`.

- [ ] **Step 3: activer seulement le titre.**

  Ajouter `WindowProperty.Title` à `APPKIT_PUBLIC_WINDOW_UPDATE_CAPABILITIES`
  (renommé depuis le set geometry) transmis par le provider. Dans
  `windowCapabilities`, utiliser ce set pour retourner la capability `title`.
  Ne modifier aucune autre propriété ni l'initialisation effective des
  décorations/boutons.

- [ ] **Step 4: passer les contrats de planned à active.**

  Remplacer les deux lignes par des lignes actives complètes :

  ```text
  WIN-002	active	APPKIT-PHASE-5-WINDOW-TITLE-DESIGN.md#Preuves et contrats	runtime window title pipeline	invalid title clear, lost combined mutation or unordered property event	O2	runtime-window-title-validation,runtime-window-title-composition,runtime-window-title-cancellation,runtime-window-title-event-order	jvm	-	runtime-window-title-clear-precommit,runtime-window-title-noop,runtime-window-title-stale-dispatch,runtime-window-title-operation-correlation,runtime-window-title-policy-bypass	-
  APK-007	active	APPKIT-PHASE-5-WINDOW-TITLE-DESIGN.md#Preuves et contrats	public AppKit window title activation	false title capability, stale effective title or bypassed mutation boundary	O3	appkit-window-title-public-activation,appkit-window-title-native-update,appkit-window-title-combined-update,appkit-window-title-policy	jvm	WindowCapabilities.title	appkit-window-title-generated-binding,appkit-window-title-cancellation-boundary,appkit-window-title-effective-readback,appkit-window-title-cross-window,appkit-window-title-policy-bypass	-
  ```

  Compléter les mappings manquants afin que chaque scénario et sentinelle soit
  représenté exactement une fois.

- [ ] **Step 5: étendre la gate AppKit.**

  Ajouter `APK-007.json` aux listes de `EVIDENCE_FILES` dans les deux scripts,
  intégrer `APK-007` à la boucle du fake Gradle et ajouter un cas négatif qui
  échoue quand ce JSON manque. Aucun gate ne doit accepter une activation sans
  evidence produite par `generateAppKitContractEvidence`.

- [ ] **Step 6: exécuter les tests GREEN et les gates.**

  Run: `./gradlew :kadre:foundation:allTests :kadre:runtime:jvmTest :kadre:backend:appkit:jvmTest :kadre:backend:appkit:appKitNativeTests :kadre:contracts:validator:validateContractRegistry --refresh-dependencies --rerun-tasks --no-daemon --console=plain`

  Expected: PASS.

  Run: `./scripts/test-kadre-appkit-contracts.sh`

  Expected: PASS et présence de `APK-007.json` dans l'evidence générée.

- [ ] **Step 7: commit.**

  ```text
  feat(appkit): activate public window title updates
  ```

### Task 7: Vérification finale de la stack

**Files:** aucune modification attendue.

**Consumes:** les quatre PRs empilées.

**Produces:** une stack prête à reviewer, où chaque base est explicite et où
les invariants de titre ne dégradent pas les contrats actifs précédents.

- [ ] **Step 1: vérifier le diff de chaque carte.**

  Run: `git diff --check <base-runtime>...<head-runtime>`

  Run: `git diff --check <base-appkit>...<head-appkit>`

  Run: `git diff --check <base-activation>...<head-activation>`

  Expected: aucune erreur d'espaces et aucun changement hors périmètre.

- [ ] **Step 2: rejouer les gates depuis la tête.**

  Run: `./gradlew :kadre:foundation:allTests :kadre:runtime:jvmTest :kadre:backend:appkit:jvmTest :kadre:backend:appkit:appKitNativeTests :kadre:contracts:validator:validateContractRegistry --refresh-dependencies --rerun-tasks --no-daemon --console=plain`

  Run: `./scripts/test-kadre-appkit-contracts.sh`

  Expected: PASS pour les deux commandes.

- [ ] **Step 3: inspecter l'état Git avant publication.**

  Run: `git status --short --branch`

  Expected: aucune modification suivie non committée ; les fichiers non suivis
  préexistants de l'utilisateur restent intacts et ne sont pas ajoutés.
