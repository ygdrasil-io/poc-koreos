# AppKit Phase 5 — Window Level Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (- [ ]) syntax for tracking.

**Goal:** rendre WindowSpec.level et WindowUpdate.level effectifs sur AppKit
avec readback KFFI, coroutines corrélées et preuves CI.

**Architecture:** le runtime porte un candidat complet contenant level, le
port AppKit applique uniquement le niveau demandé et relit le niveau natif, et
la dernière PR active la capability publique. Les niveaux sont obtenus avec
CGWindowLevelForKey ; aucun nombre AppKit ni FFI local n'est recopié dans
Kadre.

**Tech Stack:** Kotlin Multiplatform/JVM 25, coroutines, AppKit, KFFI généré,
JUnit et evidence contracts.

**Spec:** kadre/APPKIT-PHASE-5-WINDOW-LEVEL-DESIGN.md

## Global Constraints

- KFFI est l'unique owner des bindings natifs ; ne pas écrire de FFI manuel.
- Les setters et readbacks AppKit restent sur le main thread.
- Modal est un niveau de z-order, jamais une modalité ou une boucle native.
- Clear(level) échoue avant admission avec InvalidRequest("level").
- Les capabilities restent Unsupported jusqu'à la PR d'activation.
- Une carte ne mélange aucune autre propriété de fenêtre ou phase AppKit.

---

## PR 1 — Contrat et réservation

### Task 1: Définir le contrat level sans changer le comportement public

**Files:**

- Create: kadre/APPKIT-PHASE-5-WINDOW-LEVEL-DESIGN.md
- Create: kadre/implementation-plans/2026-08-30-appkit-phase-5-window-level-stack.md
- Modify: kadre/contracts/registry/contracts.tsv
- Modify: kadre/APPKIT-IMPLEMENTATION-ROADMAP.md

**Consumes:** les conventions WIN-001 à WIN-003 et APK-006 à APK-008.

**Produces:** WIN-004 et APK-009 en statut planned, sans mapping, evidence,
tâche de gate ou capability Supported.

- [x] **Step 1: Décrire les trois niveaux et la frontière AppKit**

  Documenter Normal, Floating et Modal, leur conversion par
  CGWindowLevelForKey, la non-modalité de Modal, les règles de readback,
  cancellation, no-op et le hors-scope fullscreen.

- [x] **Step 2: Réserver les contrats**

  Ajouter exactement les deux lignes suivantes après APK-008 :

      WIN-004	planned	APPKIT-PHASE-5-WINDOW-LEVEL-DESIGN.md#Preuves et contrats	runtime window level pipeline	invalid level clear, stale native readback or unordered property event	O2	-	-	-	-	-
      APK-009	planned	APPKIT-PHASE-5-WINDOW-LEVEL-DESIGN.md#Preuves et contrats	public AppKit window level activation	false level capability, native readback drift or bypassed mutation boundary	O3	-	-	-	-	-

- [x] **Step 3: Vérifier que la réservation ne change aucun gate**

  Run: ./gradlew :kadre:contracts:validator:validateContractRegistry --no-daemon --console=plain

  Expected: PASS ; les contrats planned ne réclament ni evidence ni capability.

- [x] **Step 4: Commit**

      git add kadre/APPKIT-PHASE-5-WINDOW-LEVEL-DESIGN.md \
        kadre/implementation-plans/2026-08-30-appkit-phase-5-window-level-stack.md \
        kadre/contracts/registry/contracts.tsv kadre/APPKIT-IMPLEMENTATION-ROADMAP.md
      git commit -m "docs(appkit): define window level stack"

## PR 2 — Runtime privé

### Task 2: Faire du niveau un champ runtime corrélé, sans capability publique

**Files:**

- Modify: kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt
- Modify: kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt
- Modify: kadre/runtime/contracts/evidence.tsv

**Consumes:** WindowUpdate.level, WindowState.level, le registre WIN-004
réservé et la sémantique du design.

**Produces:** un candidat runtime qui porte level, valide Clear, conserve un
no-op sans commande native et publie PropertiesChanged(Level) avec un operation
ID corrélé ; les capabilities publiques restent inchangées.

- [ ] **Step 1: Écrire les tests O2 rouges**

  Ajouter ces trois tests :

      @Test
      fun windowLevelClearFailsBeforeDispatchOrPublication() = runTest {
          val port = DeterministicWindowCommandPort()
          val manager = manager(port, enabledWindowUpdateCapabilities = levelUpdateProperties())
          val window = commit(manager.requestWindow(WindowSpec()).successValue(), port.openCommands.single())

          assertEquals(
              KadreResult.Failure(KadreFailure.InvalidRequest("level")),
              window.apply(WindowUpdate(level = PropertyChange.Clear)),
          )
          assertTrue(port.updateCommands.isEmpty())
          assertEquals(WindowLevel.Normal, window.state.value.level)
      }

      @Test
      fun windowLevelSharesTheCorrelatedCommandAndPublishesStateBeforeProperties() = runTest {
          val port = DeterministicWindowCommandPort()
          val manager = manager(port, enabledWindowUpdateCapabilities = levelCompositionUpdateProperties())
          val window = commit(
              manager.requestWindow(WindowSpec(title = "before", contentSize = LogicalSize(100.0, 100.0)))
                  .successValue(),
              port.openCommands.single(),
          )
          val events = mutableListOf<WindowEvent>()
          val collector = launch(start = CoroutineStart.UNDISPATCHED) { window.events.collect(events::add) }

          val update = async(start = CoroutineStart.UNDISPATCHED) {
              window.apply(
                  WindowUpdate(
                      title = PropertyChange.Set("after"),
                      contentSize = PropertyChange.Set(LogicalSize(120.0, 100.0)),
                      decorations = PropertyChange.Set(WindowDecorations.Borderless),
                      systemButtons = PropertyChange.Set(WindowSystemButtons.All),
                      level = PropertyChange.Set(WindowLevel.Floating),
                  ),
              )
          }
          val command = port.updateCommands.single()
          assertEquals(PropertyChange.Set(WindowSystemButtons.None), command.update.systemButtons)
          command.applied(
              window.state.value.copy(
                  title = "after",
                  contentSize = LogicalSize(120.0, 100.0),
                  decorations = WindowDecorations.Borderless,
                  systemButtons = WindowSystemButtons.None,
                  level = WindowLevel.Floating,
              ),
          )

          val outcome = assertIs<WindowUpdateOutcome.Applied>(update.await().successValue())
          assertEquals(outcome.state, assertIs<WindowEvent.GeometryChanged>(events[0]).state)
          assertEquals(
              setOf(
                  WindowProperty.Title,
                  WindowProperty.Decorations,
                  WindowProperty.SystemButtons,
                  WindowProperty.Level,
              ),
              assertIs<WindowEvent.PropertiesChanged>(events[1]).changed,
          )
          assertEquals(outcome.state, window.state.value)
          collector.cancelAndJoin()
      }

      @Test
      fun windowLevelCancellationAndQueuedRevisionRespectTheNativeCommitBoundary() = runTest {
          val port = DeterministicWindowCommandPort()
          val manager = manager(port, enabledWindowUpdateCapabilities = levelUpdateProperties())
          val window = commit(manager.requestWindow(WindowSpec()).successValue(), port.openCommands.single())
          port.updateCancellationOutcome = WindowUpdateCancellationOutcome.CancelledBeforeCommit

          val withdrawn = async(start = CoroutineStart.UNDISPATCHED) {
              window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Floating)))
          }
          withdrawn.cancelAndJoin()
          assertEquals(WindowLevel.Normal, window.state.value.level)

          port.updateCancellationOutcome = WindowUpdateCancellationOutcome.TooLate
          val committed = async(start = CoroutineStart.UNDISPATCHED) {
              window.apply(WindowUpdate(level = PropertyChange.Set(WindowLevel.Floating)))
          }
          port.updateCommands.last().applied(window.state.value.copy(level = WindowLevel.Floating))
          assertEquals(WindowLevel.Floating, assertIs<WindowUpdateOutcome.Applied>(committed.await().successValue()).state.level)
      }

- [ ] **Step 2: Vérifier le RED**

  Run: ./gradlew :kadre:runtime:jvmTest --tests org.graphiks.kadre.internal.runtime.RuntimeWindowManagerTest.windowLevelClearFailsBeforeDispatchOrPublication --no-daemon --console=plain

  Expected: FAIL ; le candidat actuel ignore level.

- [ ] **Step 3: Étendre le candidat et la publication**

  Ajouter resolveLevel, porter level dans candidateFor, dans les propriétés
  routées et dans le state candidat. Rejeter Clear pour ce champ obligatoire.
  S'assurer que le no-op ne soumet aucune WindowUpdateCommand et que
  WindowProperty.Level entre dans le seul PropertiesChanged. Ajouter les
  helpers de test suivants au même fichier de tests :

      private fun levelUpdateProperties(): Set<WindowProperty> =
          DEFAULT_RUNTIME_WINDOW_UPDATE_PROPERTIES + WindowProperty.Level

      private fun levelCompositionUpdateProperties(): Set<WindowProperty> =
          levelUpdateProperties() +
              setOf(WindowProperty.Title, WindowProperty.Decorations, WindowProperty.SystemButtons)

- [ ] **Step 4: Vérifier le GREEN**

  Run: ./gradlew :kadre:runtime:jvmTest --no-daemon --console=plain

  Expected: PASS.

- [ ] **Step 5: Préparer les mappings O2 réservés**

  Ajouter les scénarios runtime-window-level-validation,
  runtime-window-level-composition, runtime-window-level-cancellation et
  runtime-window-level-event-order, ainsi que les cinq sentinelles définies
  par le design, dans kadre/runtime/contracts/evidence.tsv. Garder WIN-004
  planned.

- [ ] **Step 6: Commit**

      git add kadre/runtime/src/jvmMain/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManager.kt \
        kadre/runtime/src/jvmTest/kotlin/org/graphiks/kadre/internal/runtime/RuntimeWindowManagerTest.kt \
        kadre/runtime/contracts/evidence.tsv
      git commit -m "feat(runtime): correlate window level updates"

## PR 3 — Port AppKit privé

### Task 3: Appliquer et relire le niveau natif avec KFFI généré

**Files:**

- Modify: kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitNativeWindowPort.kt
- Modify: kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriver.kt
- Modify: kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPort.kt
- Modify: kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitWindowRuntimeDriverTest.kt
- Modify: kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/KffiAppKitWindowPortMacOsTest.kt
- Modify: kadre/backend/appkit/contracts/evidence.tsv

**Consumes:** le runtime privé de Task 2 et les bindings KFFI NSWindow.level,
NSWindow.setLevel et CGWindowLevelForKey.

**Produces:** un peer qui applique le niveau initial et mutable, relit une
valeur bijective, préserve les autres dimensions de fenêtre et maintient la
frontière de commit.

- [ ] **Step 1: Écrire les tests O3 rouges**

  Couvrir initial Floating, mutation Modal vers Normal, readback, annulation
  avant le premier setter et deux peers indépendants. Le test macOS lit le vrai
  NSWindow.level() et compare la valeur obtenue par CGWindowLevelForKey.

- [ ] **Step 2: Vérifier le RED**

  Run: ./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitWindowRuntimeDriverTest.levelUpdatesRemainPeerLocalAfterTheNativeLevelChanges --no-daemon --console=plain

  Expected: FAIL ; la cible de mutation et le snapshot privé ne portent pas
  encore le niveau.

- [ ] **Step 3: Étendre le seam et le readback**

  Ajouter une cible/snapshot privé de niveau à AppKitWindowMutationTarget et
  AppKitWindowMutationSnapshot. Appliquer
  setLevel(appKitLevelFor(level)) à la création et après la frontière de commit
  ; convertir window.level() par comparaison aux trois résultats de
  CGWindowLevelForKey. Lever une failure pour toute valeur inconnue.

- [ ] **Step 4: Raccorder le driver**

  Faire conserver spec.level dans appKitEffectiveSpec seulement quand
  WindowProperty.Level est activé pour le peer privé. Propager la cible et le
  snapshot dans la conversion, la relecture et rejectedMutationFields, sans
  réappliquer la géométrie ou le chrome.

- [ ] **Step 5: Vérifier le GREEN**

  Run: ./gradlew :kadre:backend:appkit:jvmTest :kadre:backend:appkit:appKitNativeTests --no-daemon --console=plain

  Expected: PASS sur macOS ; les tests réels ne passent pas sur un runner
  non-AppKit.

- [ ] **Step 6: Préparer les mappings O3 réservés**

  Ajouter les scénarios appkit-window-level-initial,
  appkit-window-level-native-update, appkit-window-level-combined-update et
  appkit-window-level-policy, plus les sentinelles du design. Garder APK-009
  planned et ne modifier aucune gate.

- [ ] **Step 7: Commit**

      git add kadre/backend/appkit/src kadre/backend/appkit/contracts/evidence.tsv
      git commit -m "feat(appkit): read back mutable window level"

## PR 4 — Activation publique et evidence

### Task 4: Publier seulement le niveau entièrement prouvé

**Files:**

- Modify: kadre/backend/appkit/src/jvmMain/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProvider.kt
- Modify: kadre/backend/appkit/src/jvmTest/kotlin/org/graphiks/kadre/internal/appkit/AppKitBackendProviderTest.kt
- Modify: kadre/contracts/registry/contracts.tsv
- Modify: kadre/contracts/validator/build.gradle.kts
- Modify: kadre/runtime/contracts/evidence.tsv
- Modify: kadre/backend/appkit/contracts/evidence.tsv
- Modify: scripts/test-kadre-appkit-contracts.sh
- Modify: scripts/test-kadre-appkit-contract-driver.sh
- Modify: scripts/fixtures/fake-gradlew.sh
- Modify: kadre/APPKIT-PHASE-5-WINDOW-LEVEL-DESIGN.md
- Modify: kadre/APPKIT-IMPLEMENTATION-ROADMAP.md

**Consumes:** les preuves O2/O3 privées de Tasks 2 et 3.

**Produces:** WindowCapabilities.level active sur AppKit, WIN-004 et APK-009
actifs et evidence JSON imposée par la CI.

- [ ] **Step 1: Écrire le test public rouge**

  Ouvrir une fenêtre AppKit publique, vérifier la capability exacte, créer
  Floating, appliquer un update combiné avec titre/géométrie/chrome/niveau,
  puis relire NSWindow.level() et l'unique operation ID publié.

- [ ] **Step 2: Vérifier le RED**

  Run: ./gradlew :kadre:backend:appkit:jvmTest --tests org.graphiks.kadre.internal.appkit.AppKitBackendProviderTest.publicAppKitWindowLevelUsesTheGeneratedBindingAndOneCorrelatedUpdateOnMacOs --no-daemon --console=plain

  Expected: FAIL ; WindowCapabilities.level est encore Unsupported.

- [ ] **Step 3: Activer la capability**

  Ajouter seulement WindowProperty.Level à
  APPKIT_PUBLIC_WINDOW_UPDATE_CAPABILITIES ; vérifier que les autres propriétés
  hors scope restent refusées.

- [ ] **Step 4: Activer les contrats atomiquement**

  Remplacer les lignes planned par les oracles, scénarios, targets et
  sentinelles listés dans le design. Ajouter WIN-004 à runtimeContractIds,
  APK-009 à appKitContractIds, APK-009.json aux deux scripts et à leur fake.
  Les mappings doivent désigner les noms JUnit exacts avant l'activation.

- [ ] **Step 5: Vérifier les gates**

  Run:

      ./gradlew :kadre:runtime:jvmTest :kadre:backend:appkit:appKitNativeTests \
        :kadre:contracts:validator:generateRuntimeContractEvidence \
        :kadre:contracts:validator:generateAppKitContractEvidence \
        :kadre:contracts:validator:validateContractRegistry \
        --no-daemon --console=plain
      ./scripts/test-kadre-appkit-contracts.sh
      ./scripts/test-kadre-appkit-contract-driver.sh

  Expected: PASS ; WIN-004.json et APK-009.json sont non vides et aucune
  capability hors scope n'est devenue supportée.

- [ ] **Step 6: Commit**

      git add kadre/backend/appkit kadre/contracts kadre/runtime/contracts scripts \
        kadre/APPKIT-PHASE-5-WINDOW-LEVEL-DESIGN.md kadre/APPKIT-IMPLEMENTATION-ROADMAP.md
      git commit -m "feat(appkit): activate public window level"
